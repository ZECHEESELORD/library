package sh.harold.library.example.paper.entity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.library.entity.CommonEntityFlags;
import sh.harold.library.entity.EntityInteractionHandler;
import sh.harold.library.entity.EntityInteractionResult;
import sh.harold.library.entity.EntitySpec;
import sh.harold.library.entity.EntityTransform;
import sh.harold.library.entity.EntityTypes;
import sh.harold.library.entity.ManagedEntity;
import sh.harold.library.entity.ItemDescriptor;
import sh.harold.library.entity.house.HouseServiceEntity;
import sh.harold.library.entity.house.HouseServiceSpec;
import sh.harold.library.entity.paper.PaperEntityPlatform;
import sh.harold.library.npc.behavior.HumanoidBehaviorCapable;
import sh.harold.library.npc.behavior.NpcBehaviorProfile;
import sh.harold.library.npc.behavior.NpcConversationTopic;
import sh.harold.library.npc.behavior.NpcPersonalityPreset;
import sh.harold.library.npc.behavior.NpcRoutines;
import sh.harold.library.npc.behavior.NpcVoiceProfiles;
import sh.harold.library.spatial.AnchorRef;
import sh.harold.library.spatial.AnchorSnapshot;
import sh.harold.library.spatial.Frame3;
import sh.harold.library.spatial.SpaceId;
import sh.harold.library.spatial.Vec3;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

public final class PaperEntityExamplePlugin extends JavaPlugin {

    private PaperEntityPlatform nativePlatform;
    private ManagedEntity nativeVillager;
    private HouseServiceEntity bankerService;
    private HouseServiceEntity guideService;
    private HouseServiceEntity artisanService;

    @Override
    public void onEnable() {
        nativePlatform = new PaperEntityPlatform(this);

        World world = Bukkit.getWorlds().getFirst();
        Location spawn = world.getSpawnLocation();

        CompletionStage<ManagedEntity> villagerSpawn = nativePlatform.spawnAsync(world, EntitySpec.builder(EntityTypes.VILLAGER)
                .transform(new EntityTransform(spawn.getX() + 2.0, spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch()))
                .flags(CommonEntityFlags.builder().customName(Component.text("Entity Example Villager")).customNameVisible(true).build())
                .interactionHandler(EntityInteractionHandler.observing(
                        context -> getLogger().info(
                                "Native villager interaction: " + context.action()
                                        + context.hand().map(hand -> " (" + hand + ")").orElse("")
                                        + " by " + context.interactor().uniqueId()
                        ),
                        EntityInteractionResult.PASS
                ))
                .build());

        CompletionStage<HouseServiceEntity> bankerSpawn = nativePlatform.spawnServiceAsync(world, HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.VILLAGER)
                        .transform(new EntityTransform(spawn.getX() + 4.0, spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch()))
                        .flags(CommonEntityFlags.builder().gravity(false).build())
                        .build())
                .name("&bMeredith")
                .description("Banker")
                .clickHandler(context -> getLogger().info("House banker clicked by " + context.interactor().uniqueId()))
                .build());

        NpcBehaviorProfile guideBehavior = NpcBehaviorProfile.builder(NpcPersonalityPreset.WARM)
                .voice(NpcVoiceProfiles.WARM_VILLAGER)
                .interactionLine(Component.text("Oh! Hello there."))
                .interactionLine(Component.text("Need a hand finding your way?"))
                .build();
        CompletionStage<HouseServiceEntity> guideSpawn = nativePlatform.spawnServiceAsync(
                world,
                HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.PLAYER_LIKE_HUMANOID)
                                .transform(new EntityTransform(
                                        spawn.getX() + 6.0,
                                        spawn.getY(),
                                        spawn.getZ(),
                                        spawn.getYaw(),
                                        spawn.getPitch()
                                ))
                                .flags(CommonEntityFlags.builder().gravity(false).build())
                                .build())
                        .name("&aGideon")
                        .description("Guide")
                        .behaviorProfile(guideBehavior)
                        .clickHandler(context -> getLogger().info(
                                "Native mannequin guide clicked by " + context.interactor().uniqueId()
                        ))
                        .build()
        );

        NpcBehaviorProfile artisanBehavior = NpcBehaviorProfile.builder(NpcPersonalityPreset.CONFIDENT)
                .voice(NpcVoiceProfiles.DEEP_VILLAGER)
                .interactionLine(Component.text("The tools are ready."))
                .conversationInterruptionLine(Component.text("Hm? I was listening."))
                .build();
        CompletionStage<HouseServiceEntity> artisanSpawn = nativePlatform.spawnServiceAsync(
                world,
                HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.PLAYER_LIKE_HUMANOID)
                                .transform(new EntityTransform(
                                        spawn.getX() + 8.0,
                                        spawn.getY(),
                                        spawn.getZ(),
                                        spawn.getYaw(),
                                        spawn.getPitch()
                                ))
                                .build())
                        .name("&6Mara")
                        .description("Artisan")
                        .behaviorProfile(artisanBehavior)
                        .build()
        );

        CompletableFuture.allOf(
                villagerSpawn.toCompletableFuture(),
                bankerSpawn.toCompletableFuture(),
                guideSpawn.toCompletableFuture(),
                artisanSpawn.toCompletableFuture()
        ).whenComplete((ignored, failure) -> {
            if (failure != null) {
                getLogger().log(Level.SEVERE, "Paper entity smoke harness failed to spawn", failure);
                return;
            }
            nativeVillager = villagerSpawn.toCompletableFuture().join();
            bankerService = bankerSpawn.toCompletableFuture().join();
            guideService = guideSpawn.toCompletableFuture().join();
            artisanService = artisanSpawn.toCompletableFuture().join();
            nativePlatform.conversationRegistry().register(
                    NpcConversationTopic.of(
                            Key.key("paper-example", "workshop"),
                            List.of(
                                    Component.text("Quiet morning in the workshop."),
                                    Component.text("The shelf still needs sorting."),
                                    Component.text("I can take the next visitor."),
                                    Component.text("Did you hear that anvil ring?")
                            ),
                            List.of(Component.text("Oh—were we needed?"))
                    ),
                    List.of(guideService.entity(), artisanService.entity())
            );
            scheduleSmoke(spawn);
            getLogger().info("Paper/Folia NPC smoke harness ready: attention, speech, four routines, and conversations are active.");
        });
    }

    @Override
    public void onDisable() {
        if (nativePlatform != null) {
            nativePlatform.closeAsync().exceptionally(failure -> {
                getLogger().log(Level.SEVERE, "Asynchronous entity cleanup failed", failure);
                return null;
            });
        }
    }

    private void scheduleSmoke(Location spawn) {
        Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> {
            guideService.entity()
                    .requireCapability(HumanoidBehaviorCapable.class)
                    .speak(Component.text("This line is queued from the global coordinator."));
            getLogger().info("Smoke step 1: queued native mannequin speech.");
        }, 20L);

        Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> nativePlatform.teleportAsync(
                guideService.entity(),
                new EntityTransform(
                        spawn.getX() + 6.0,
                        spawn.getY(),
                        spawn.getZ() + 1.5,
                        spawn.getYaw(),
                        spawn.getPitch()
                )
        ).whenComplete((ignored, failure) -> {
            if (failure == null) {
                getLogger().info("Smoke step 2: asynchronously teleported the native mannequin guide.");
            } else {
                getLogger().log(Level.SEVERE, "Native mannequin teleport failed", failure);
            }
        }), 40L);

        Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> runRoutineSmoke(spawn), 60L);
    }

    private void runRoutineSmoke(Location spawn) {
        SpaceId space = SpaceId.of("paper", spawn.getWorld().getUID().toString());
        AnchorRef lectern = fixed(space, spawn.getX() + 6.0, spawn.getY() + 1.0, spawn.getZ() + 2.5);
        AnchorRef anvil = fixed(space, spawn.getX() + 6.7, spawn.getY() + 0.8, spawn.getZ() + 2.0);
        AnchorRef shelf = fixed(space, spawn.getX() + 5.3, spawn.getY() + 1.4, spawn.getZ() + 2.0);
        AnchorRef table = fixed(space, spawn.getX() + 6.0, spawn.getY() + 0.8, spawn.getZ() + 2.0);
        HumanoidBehaviorCapable behavior = guideService.entity().requireCapability(HumanoidBehaviorCapable.class);

        behavior.perform(NpcRoutines.lecternStudy(lectern)).completion()
                .thenCompose(ignored -> behavior.perform(NpcRoutines.anvilForging(
                        anvil,
                        new ItemDescriptor(Key.key("minecraft", "iron_pickaxe"), 1)
                )).completion())
                .thenCompose(ignored -> behavior.perform(NpcRoutines.shelfDistraction(shelf)).completion())
                .thenCompose(ignored -> behavior.perform(NpcRoutines.tableCrafting(
                        table,
                        List.of(
                                new ItemDescriptor(Key.key("minecraft", "oak_planks"), 1),
                                new ItemDescriptor(Key.key("minecraft", "stick"), 1)
                        )
                )).completion())
                .whenComplete((ignored, failure) -> {
                    if (failure == null) {
                        getLogger().info("Smoke step 3: all four declarative prop routines completed.");
                    } else {
                        getLogger().log(Level.SEVERE, "Routine smoke sequence failed", failure);
                    }
                });
    }

    private static AnchorRef fixed(SpaceId space, double x, double y, double z) {
        return new AnchorRef.Fixed(new AnchorSnapshot(space, Frame3.world(new Vec3(x, y, z))));
    }
}
