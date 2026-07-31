package sh.harold.library.example.paper.entity;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.library.entity.BlockDescriptor;
import sh.harold.library.entity.CommonEntityFlags;
import sh.harold.library.entity.EntityInteractionResult;
import sh.harold.library.entity.EntitySpec;
import sh.harold.library.entity.EntityTransform;
import sh.harold.library.entity.EntityTypes;
import sh.harold.library.entity.EquipmentSlot;
import sh.harold.library.entity.ItemDescriptor;
import sh.harold.library.entity.ManagedEntity;
import sh.harold.library.entity.capability.BlockDisplayCapable;
import sh.harold.library.entity.capability.Equipable;
import sh.harold.library.entity.capability.PersistenceCapable;
import sh.harold.library.entity.house.HouseServiceClickHandler;
import sh.harold.library.entity.house.HouseServiceEntity;
import sh.harold.library.entity.house.HouseServiceSpec;
import sh.harold.library.entity.paper.PaperEntityPlatform;
import sh.harold.library.npc.behavior.HumanoidBehaviorCapable;
import sh.harold.library.npc.behavior.NpcBehaviorProfile;
import sh.harold.library.npc.behavior.NpcConversationRegistration;
import sh.harold.library.spatial.AnchorRef;
import sh.harold.library.spatial.AnchorSnapshot;
import sh.harold.library.spatial.Frame3;
import sh.harold.library.spatial.SpaceId;
import sh.harold.library.spatial.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Lifecycle and platform wiring for a playable library and forge showcase.
 * Pure behavior authoring lives in {@link PaperNpcBehaviorCatalog}; imperative
 * demonstrations live in {@link PaperNpcDioramaControls}.
 */
public final class PaperEntityExamplePlugin extends JavaPlugin {

    private final List<NpcConversationRegistration> conversationRegistrations = new CopyOnWriteArrayList<>();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final Object setupLock = new Object();

    private PaperEntityPlatform entities;
    private PaperNpcDioramaControls controls;
    private volatile PaperNpcDioramaCast cast;

    @Override
    public void onEnable() {
        entities = new PaperEntityPlatform(this);
        controls = new PaperNpcDioramaControls(this, () -> cast);
        controls.register();

        World world = Bukkit.getWorlds().getFirst();
        SceneLayout layout = SceneLayout.at(world.getSpawnLocation());
        spawnDioramas(world, layout).whenComplete((readyCast, failure) -> {
            synchronized (setupLock) {
                if (stopping.get()) {
                    return;
                }
                if (failure != null) {
                    getLogger().log(Level.SEVERE, "Could not create the Paper NPC behavior dioramas", failure);
                    return;
                }
                try {
                    cast = readyCast;
                    registerConversations(readyCast);

                    // One startup line demonstrates ordinary shared speech. The
                    // command harness exposes longer FIFO and speakNow examples.
                    behavior(readyCast.librarian()).speak(Component.text(
                            "The catalogue is open. Please mind the freshly sorted folios.",
                            NamedTextColor.GOLD
                    ));
                    getLogger().info(
                            "NPC dioramas ready near world spawn: five-person library, three-person forge, "
                                    + "all eight personalities, shared conversations, per-viewer attention, and /npcdemo controls."
                    );
                } catch (Throwable finalizationFailure) {
                    getLogger().log(Level.SEVERE, "NPC dioramas spawned but final setup failed", finalizationFailure);
                }
            }
        });
    }

    @Override
    public void onDisable() {
        synchronized (setupLock) {
            stopping.set(true);
        }
        if (controls != null) {
            controls.close();
        }
        conversationRegistrations.forEach(NpcConversationRegistration::close);
        conversationRegistrations.clear();
        if (entities != null) {
            entities.closeAsync().exceptionally(failure -> {
                getLogger().log(Level.SEVERE, "Asynchronous entity cleanup failed", failure);
                return null;
            });
        }
    }

    private CompletionStage<PaperNpcDioramaCast> spawnDioramas(World world, SceneLayout layout) {
        PaperNpcBehaviorCatalog.AuthoredBehaviors authored = PaperNpcBehaviorCatalog.author(
                new PaperNpcBehaviorCatalog.Anchors(
                        layout.anchor(4.0, 1.0, 4.9),
                        layout.anchor(7.0, 1.35, 4.9),
                        layout.anchor(10.0, 0.85, 4.9),
                        layout.anchor(13.0, 1.35, 4.9),
                        layout.anchor(16.0, 1.0, 4.9),
                        layout.anchor(5.0, 0.8, 13.9),
                        layout.anchor(8.0, 0.85, 13.9),
                        layout.anchor(11.0, 0.85, 13.9)
                )
        );

        List<CompletionStage<?>> spawns = new ArrayList<>();

        // Two controls keep the broader entity example honest: a generic
        // normalized PASS handler and a House service with behavior omitted.
        spawns.add(entities.spawnAsync(world, EntitySpec.builder(EntityTypes.VILLAGER)
                .transform(layout.transform(2.0, -4.0, 0.0f))
                .flags(CommonEntityFlags.builder()
                        .customName(Component.text("Interaction control (PASS)"))
                        .customNameVisible(true)
                        .invulnerable(true)
                        .build())
                .interactionHandler(context -> {
                    getLogger().info(
                            "Normalized generic interaction: " + context.action()
                                    + context.hand().map(hand -> " (" + hand + ")").orElse("")
                                    + " by " + context.interactor().uniqueId()
                    );
                    return EntityInteractionResult.PASS;
                })
                .build()));
        spawns.add(entities.spawnServiceAsync(world, HouseServiceSpec.builder(
                        EntitySpec.builder(EntityTypes.VILLAGER)
                                .transform(layout.transform(5.0, -4.0, 0.0f))
                                .flags(CommonEntityFlags.builder().gravity(false).build())
                                .build())
                .name("&bMeredith")
                .description("Motionless behavior-omission control")
                .clickHandler(context -> getLogger().info(
                        "Motionless House service received " + context.action() + " from " + context.interactor().uniqueId()
                ))
                .build()));

        CompletionStage<HouseServiceEntity> librarian = spawnNpc(
                world, layout, 4.0, 3.0, "&6Elowen", "Head Librarian - Warm",
                "library", authored.librarianProfile(),
                List.of(new BaseEquipment(EquipmentSlot.MAIN_HAND, PaperNpcBehaviorCatalog.item("writable_book"))),
                controls::greetLibrarian
        );
        CompletionStage<HouseServiceEntity> archivist = spawnNpc(
                world, layout, 7.0, 3.0, "&aOrrin", "Archivist - Curious",
                "library", authored.archivistProfile(),
                List.of(new BaseEquipment(EquipmentSlot.OFF_HAND, PaperNpcBehaviorCatalog.item("book"))),
                null
        );
        CompletionStage<HouseServiceEntity> scribe = spawnNpc(
                world, layout, 10.0, 3.0, "&bMira", "Scribe - Distracted",
                "library", authored.scribeProfile(),
                List.of(new BaseEquipment(EquipmentSlot.MAIN_HAND, PaperNpcBehaviorCatalog.item("feather"))),
                null
        );
        CompletionStage<HouseServiceEntity> researcher = spawnNpc(
                world, layout, 13.0, 3.0, "&dTamsin", "Herpetology Researcher - Confused",
                "library", authored.researcherProfile(),
                List.of(new BaseEquipment(EquipmentSlot.MAIN_HAND, PaperNpcBehaviorCatalog.item("slime_ball"))),
                null
        );
        CompletionStage<HouseServiceEntity> nightClerk = spawnNpc(
                world, layout, 16.0, 3.0, "&7Alda", "Night Clerk - Sleepy",
                "library", authored.nightClerkProfile(),
                List.of(new BaseEquipment(EquipmentSlot.OFF_HAND, PaperNpcBehaviorCatalog.item("book"))),
                null
        );
        CompletionStage<HouseServiceEntity> blacksmith = spawnNpc(
                world, layout, 5.0, 12.0, "&6Bran", "Master Blacksmith - Confident",
                "forge", authored.blacksmithProfile(),
                List.of(
                        new BaseEquipment(EquipmentSlot.MAIN_HAND, PaperNpcBehaviorCatalog.item("iron_pickaxe")),
                        new BaseEquipment(EquipmentSlot.CHEST, PaperNpcBehaviorCatalog.item("leather_chestplate"))
                ),
                null
        );
        CompletionStage<HouseServiceEntity> apprentice = spawnNpc(
                world, layout, 8.0, 12.0, "&ePip", "Forge Apprentice - Nervous",
                "forge", authored.apprenticeProfile(),
                List.of(new BaseEquipment(EquipmentSlot.MAIN_HAND, PaperNpcBehaviorCatalog.item("stick"))),
                null
        );
        CompletionStage<HouseServiceEntity> quartermaster = spawnNpc(
                world, layout, 11.0, 12.0, "&fSera", "Quartermaster - Neutral",
                "forge", authored.quartermasterProfile(),
                List.of(new BaseEquipment(EquipmentSlot.OFF_HAND, PaperNpcBehaviorCatalog.item("paper"))),
                null
        );
        spawns.addAll(List.of(
                librarian, archivist, scribe, researcher, nightClerk,
                blacksmith, apprentice, quartermaster
        ));

        // Static managed BlockDisplays are scenery, never routine-controlled
        // props. They do not mutate world blocks and platform.closeAsync removes
        // them even after partial scene setup.
        spawns.add(spawnScenery(world, layout, 3.5, 0.0, 4.4, "lectern"));
        spawns.add(spawnScenery(world, layout, 6.5, 0.0, 4.4, "bookshelf"));
        spawns.add(spawnScenery(world, layout, 6.5, 1.0, 4.4, "chiseled_bookshelf"));
        spawns.add(spawnScenery(world, layout, 9.5, 0.0, 4.4, "crafting_table"));
        spawns.add(spawnScenery(world, layout, 12.5, 0.0, 4.4, "bookshelf"));
        spawns.add(spawnScenery(world, layout, 12.5, 1.0, 4.4, "bookshelf"));
        spawns.add(spawnScenery(world, layout, 15.5, 0.0, 4.4, "lectern"));
        spawns.add(spawnScenery(world, layout, 4.5, 0.0, 13.4, "anvil"));
        spawns.add(spawnScenery(world, layout, 7.5, 0.0, 13.4, "smithing_table"));
        spawns.add(spawnScenery(world, layout, 10.5, 0.0, 13.4, "barrel"));

        return allOf(spawns).thenApply(ignored -> new PaperNpcDioramaCast(
                librarian.toCompletableFuture().join(),
                archivist.toCompletableFuture().join(),
                scribe.toCompletableFuture().join(),
                researcher.toCompletableFuture().join(),
                nightClerk.toCompletableFuture().join(),
                blacksmith.toCompletableFuture().join(),
                apprentice.toCompletableFuture().join(),
                quartermaster.toCompletableFuture().join(),
                authored,
                layout.location(4.0, 0.0, 3.0)
        ));
    }

    private CompletionStage<HouseServiceEntity> spawnNpc(
            World world,
            SceneLayout layout,
            double x,
            double z,
            String name,
            String role,
            String scene,
            NpcBehaviorProfile profile,
            List<BaseEquipment> baseEquipment,
            HouseServiceClickHandler clickHandler
    ) {
        HouseServiceSpec.Builder builder = HouseServiceSpec.builder(
                        EntitySpec.builder(EntityTypes.PLAYER_LIKE_HUMANOID)
                                .transform(layout.transform(x, z, 0.0f))
                                .flags(CommonEntityFlags.builder().gravity(false).build())
                                .tag(Key.key("paper-example", "diorama/" + scene))
                                .build())
                .name(name)
                .description(role)
                .behaviorProfile(profile);
        if (clickHandler != null) {
            builder.clickHandler(clickHandler);
        }
        return entities.spawnServiceAsync(world, builder.build()).thenApply(service -> {
            // spawnServiceAsync completes its configure on this mannequin's
            // actor lane, so native authored equipment is safe to apply here.
            Equipable equipment = service.entity().requireCapability(Equipable.class);
            for (BaseEquipment base : baseEquipment) {
                equipment.equipment(base.slot(), base.item());
            }
            getLogger().info("Authored base equipment set for " + name + "; routines restore this live base frame.");
            return service;
        });
    }

    private CompletionStage<ManagedEntity> spawnScenery(
            World world,
            SceneLayout layout,
            double x,
            double y,
            double z,
            String block
    ) {
        return entities.spawnAsync(world, EntitySpec.builder(EntityTypes.BLOCK_DISPLAY)
                        .transform(layout.transform(x, y, z, 0.0f))
                        .flags(CommonEntityFlags.builder().gravity(false).invulnerable(true).build())
                        .tag(Key.key("paper-example", "diorama/scenery"))
                        .build())
                .thenApply(display -> {
                    display.requireCapability(BlockDisplayCapable.class)
                            .block(new BlockDescriptor(Key.key("minecraft", block)));
                    display.requireCapability(PersistenceCapable.class).persistent(false);
                    return display;
                });
    }

    private void registerConversations(PaperNpcDioramaCast ready) {
        conversationRegistrations.add(entities.conversationRegistry().register(
                PaperNpcBehaviorCatalog.libraryCatalogueTopic(),
                List.of(
                        ready.librarian().entity(),
                        ready.archivist().entity(),
                        ready.scribe().entity(),
                        ready.researcher().entity()
                )
        ));

        // Orrin and Tamsin deliberately overlap the first cast. Atomic actor
        // locks keep either NPC in only one active conversation at a time.
        conversationRegistrations.add(entities.conversationRegistry().register(
                PaperNpcBehaviorCatalog.libraryClosingTopic(),
                List.of(ready.archivist().entity(), ready.researcher().entity(), ready.nightClerk().entity())
        ));
        conversationRegistrations.add(entities.conversationRegistry().register(
                PaperNpcBehaviorCatalog.forgeOrdersTopic(),
                List.of(ready.blacksmith().entity(), ready.apprentice().entity(), ready.quartermaster().entity())
        ));
    }

    private static HumanoidBehaviorCapable behavior(HouseServiceEntity service) {
        return service.entity().requireCapability(HumanoidBehaviorCapable.class);
    }

    private static CompletableFuture<Void> allOf(List<? extends CompletionStage<?>> stages) {
        CompletableFuture<?>[] futures = stages.stream()
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture<?>[]::new);
        return CompletableFuture.allOf(futures);
    }

    private record BaseEquipment(EquipmentSlot slot, ItemDescriptor item) {
    }

    private record SceneLayout(World world, double x, double y, double z, SpaceId space) {
        private static SceneLayout at(Location origin) {
            World world = origin.getWorld();
            return new SceneLayout(
                    world,
                    origin.getX(),
                    origin.getY(),
                    origin.getZ(),
                    SpaceId.of("paper", world.getUID().toString())
            );
        }

        private EntityTransform transform(double xOffset, double zOffset, float yaw) {
            return transform(xOffset, 0.0, zOffset, yaw);
        }

        private EntityTransform transform(double xOffset, double yOffset, double zOffset, float yaw) {
            return new EntityTransform(x + xOffset, y + yOffset, z + zOffset, yaw, 0.0f);
        }

        private AnchorRef anchor(double xOffset, double yOffset, double zOffset) {
            return new AnchorRef.Fixed(new AnchorSnapshot(
                    space,
                    Frame3.world(new Vec3(x + xOffset, y + yOffset, z + zOffset))
            ));
        }

        private Location location(double xOffset, double yOffset, double zOffset) {
            return new Location(world, x + xOffset, y + yOffset, z + zOffset);
        }
    }
}
