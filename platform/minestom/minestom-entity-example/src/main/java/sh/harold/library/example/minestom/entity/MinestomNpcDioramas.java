package sh.harold.library.example.minestom.entity;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.timer.TaskSchedule;
import sh.harold.library.entity.CommonEntityFlags;
import sh.harold.library.entity.EntitySpec;
import sh.harold.library.entity.EntityTransform;
import sh.harold.library.entity.EntityTypes;
import sh.harold.library.entity.EquipmentSlot;
import sh.harold.library.entity.ItemDescriptor;
import sh.harold.library.entity.capability.Equipable;
import sh.harold.library.entity.house.HouseServiceEntity;
import sh.harold.library.entity.house.HouseServiceClickContext;
import sh.harold.library.entity.house.HouseServiceSpec;
import sh.harold.library.entity.minestom.MinestomEntityPlatform;
import sh.harold.library.npc.behavior.HumanoidBehaviorCapable;
import sh.harold.library.npc.behavior.NpcAttentionLease;
import sh.harold.library.npc.behavior.NpcBehaviorProfile;
import sh.harold.library.npc.behavior.NpcBehaviorSnapshot;
import sh.harold.library.npc.behavior.NpcConversationRegistration;
import sh.harold.library.npc.behavior.NpcConversationTopic;
import sh.harold.library.npc.behavior.NpcPlayback;
import sh.harold.library.spatial.SpaceId;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Three static block dioramas whose native mannequins target immutable AnchorRefs.
 * The blocks are scenery only; behavior routines never mutate the world.
 */
final class MinestomNpcDioramas {

    static final Pos LIBRARY_VIEW = new Pos(0.5, 42.0, 6.5, 0.0f, 0.0f);
    static final Pos FORGE_VIEW = new Pos(17.5, 42.0, 6.5, 0.0f, 0.0f);
    static final Pos WATCH_VIEW = new Pos(-18.0, 42.0, 6.5, 0.0f, 0.0f);

    private final InstanceContainer instance;
    private final MinestomEntityPlatform platform;
    private final NpcDioramaCatalog.Catalog catalog;
    private final List<NpcConversationRegistration> conversations = new CopyOnWriteArrayList<>();
    private final Set<NpcAttentionLease> attentionLeases = ConcurrentHashMap.newKeySet();
    private final Set<NpcPlayback> playbacks = ConcurrentHashMap.newKeySet();
    private final Object resourceLock = new Object();
    private final AtomicBoolean spawnStarted = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CompletableFuture<Void> initialization = new CompletableFuture<>();

    private volatile HouseServiceEntity librarian;
    private volatile HouseServiceEntity cataloguer;
    private volatile HouseServiceEntity shelver;
    private volatile HouseServiceEntity smith;
    private volatile HouseServiceEntity apprentice;
    private volatile HouseServiceEntity sentinel;
    private volatile HouseServiceEntity sleepyGuard;
    private volatile HouseServiceEntity courier;

    private volatile HumanoidBehaviorCapable librarianBehavior;
    private volatile HumanoidBehaviorCapable cataloguerBehavior;
    private volatile HumanoidBehaviorCapable shelverBehavior;
    private volatile HumanoidBehaviorCapable smithBehavior;
    private volatile HumanoidBehaviorCapable apprenticeBehavior;
    private volatile HumanoidBehaviorCapable sentinelBehavior;
    private volatile HumanoidBehaviorCapable sleepyGuardBehavior;
    private volatile HumanoidBehaviorCapable courierBehavior;

    MinestomNpcDioramas(InstanceContainer instance, MinestomEntityPlatform platform) {
        this.instance = Objects.requireNonNull(instance, "instance");
        this.platform = Objects.requireNonNull(platform, "platform");
        this.catalog = NpcDioramaCatalog.create(SpaceId.of("minestom", instance.getUuid().toString()));
    }

    void buildScenery() {
        buildApproach();
        buildLibrary();
        buildForge();
        buildWatchPost();
    }

    CompletionStage<Void> spawnAsync() {
        if (!spawnStarted.compareAndSet(false, true)) {
            return initialization;
        }

        CompletableFuture<HouseServiceEntity> librarianSpawn = spawnActor(
                "&6Elowen",
                "Head Librarian - Warm",
                transform(0.5, 42.0, 14.5, 180.0f),
                catalog.profile(NpcDioramaCatalog.LIBRARIAN),
                new BaseProp(EquipmentSlot.OFF_HAND, item("book")),
                Component.text("Oh, hello! Need help finding a book?", NamedTextColor.GOLD)
        );
        CompletableFuture<HouseServiceEntity> cataloguerSpawn = spawnActor(
                "&bPip",
                "Cataloguer - Curious",
                transform(-4.5, 42.0, 12.5, 180.0f),
                catalog.profile(NpcDioramaCatalog.CATALOGUER),
                new BaseProp(EquipmentSlot.MAIN_HAND, item("paper")),
                Component.text("I am testing a new cross-reference system.", NamedTextColor.AQUA)
        );
        CompletableFuture<HouseServiceEntity> shelverSpawn = spawnActor(
                "&dTamsin",
                "Shelving Assistant - Distracted",
                transform(4.5, 42.0, 15.5, 0.0f),
                catalog.profile(NpcDioramaCatalog.SHELVER),
                new BaseProp(EquipmentSlot.MAIN_HAND, item("book")),
                Component.text("Was this history, mystery, or mysterious history?", NamedTextColor.LIGHT_PURPLE)
        );
        CompletableFuture<HouseServiceEntity> smithSpawn = spawnActor(
                "&6Mara",
                "Master Smith - Confident",
                transform(15.5, 42.0, 14.5, 180.0f),
                catalog.profile(NpcDioramaCatalog.SMITH),
                new BaseProp(EquipmentSlot.MAIN_HAND, item("iron_axe")),
                Component.text("The forge is hot and the work is good.", NamedTextColor.GOLD)
        );
        CompletableFuture<HouseServiceEntity> apprenticeSpawn = spawnActor(
                "&aNiko",
                "Forge Apprentice - Nervous",
                transform(20.5, 42.0, 13.5, 180.0f),
                catalog.profile(NpcDioramaCatalog.APPRENTICE),
                new BaseProp(EquipmentSlot.MAIN_HAND, item("iron_ingot")),
                Component.text("Please tell Mara I measured twice.", NamedTextColor.GREEN)
        );
        CompletableFuture<HouseServiceEntity> sentinelSpawn = spawnActor(
                "&7Iona",
                "Road Sentinel - Neutral",
                transform(-15.5, 42.0, 14.0, 180.0f),
                catalog.profile(NpcDioramaCatalog.SENTINEL),
                new BaseProp(EquipmentSlot.MAIN_HAND, item("iron_sword")),
                Component.text("Use, off-hand use, and attack all reach the normalized handler.", NamedTextColor.GRAY)
        );
        CompletableFuture<HouseServiceEntity> sleepyGuardSpawn = spawnActor(
                "&9Orin",
                "Relief Guard - Sleepy",
                transform(-22.0, 42.0, 14.0, 180.0f),
                catalog.profile(NpcDioramaCatalog.SLEEPY_GUARD),
                new BaseProp(EquipmentSlot.OFF_HAND, item("lantern")),
                Component.text("I am awake. This is simply a long blink.", NamedTextColor.BLUE)
        );
        // This House service intentionally omits behaviorProfile. Its capability is
        // configured explicitly below to demonstrate inert-by-default authoring.
        CompletableFuture<HouseServiceEntity> courierSpawn = spawnActor(
                "&eWren",
                "Lost Courier - Confused",
                transform(-19.5, 42.0, 11.5, 180.0f),
                null,
                new BaseProp(EquipmentSlot.OFF_HAND, item("map")),
                Component.text("Could you rotate the road instead of the map?", NamedTextColor.YELLOW)
        );

        CompletableFuture.allOf(
                librarianSpawn,
                cataloguerSpawn,
                shelverSpawn,
                smithSpawn,
                apprenticeSpawn,
                sentinelSpawn,
                sleepyGuardSpawn,
                courierSpawn
        ).thenCompose(ignored -> {
            librarian = librarianSpawn.join();
            cataloguer = cataloguerSpawn.join();
            shelver = shelverSpawn.join();
            smith = smithSpawn.join();
            apprentice = apprenticeSpawn.join();
            sentinel = sentinelSpawn.join();
            sleepyGuard = sleepyGuardSpawn.join();
            courier = courierSpawn.join();

            librarianBehavior = behavior(librarian);
            cataloguerBehavior = behavior(cataloguer);
            shelverBehavior = behavior(shelver);
            smithBehavior = behavior(smith);
            apprenticeBehavior = behavior(apprentice);
            sentinelBehavior = behavior(sentinel);
            sleepyGuardBehavior = behavior(sleepyGuard);
            courierBehavior = behavior(courier);
            return courierBehavior.configure(catalog.profile(NpcDioramaCatalog.COURIER));
        }).thenRun(this::registerConversations).whenComplete((ignored, failure) -> {
            if (failure == null) {
                initialization.complete(null);
            } else {
                initialization.completeExceptionally(failure);
            }
        });
        return initialization;
    }

    boolean ready() {
        return initialization.isDone() && !initialization.isCompletedExceptionally() && !closed.get();
    }

    void queueLibrarySpeech() {
        requireReady();
        track(librarianBehavior.speak(Component.text("Welcome to the Grand Library.", NamedTextColor.GOLD)));
        track(librarianBehavior.speak(Component.text()
                .append(Component.text("Marginalia: ", NamedTextColor.GRAY))
                .append(Component.text("some dragons are metaphors", NamedTextColor.LIGHT_PURPLE)
                        .decorate(TextDecoration.ITALIC))
                .build()));
        track(librarianBehavior.speak(Component.text(
                "Long authored bubbles wrap near forty visible characters, preserve styling, and even time emoji by grapheme: books and sparkles \uD83D\uDCDA\u2728",
                NamedTextColor.AQUA
        )));
    }

    void speakNow() {
        requireReady();
        track(librarianBehavior.speakNow(Component.text(
                "Pardon the interruption - this speakNow line supersedes disposable queued worldbuilding.",
                NamedTextColor.RED
        )));
    }

    void clearSpeech() {
        requireReady();
        librarianBehavior.clearSpeech();
    }

    NpcPlayback perform(String routineName) {
        requireReady();
        HumanoidBehaviorCapable actor = switch (routineName) {
            case NpcDioramaCatalog.LECTERN, NpcDioramaCatalog.LECTERN_REVIEW -> librarianBehavior;
            case NpcDioramaCatalog.CATALOGUING -> cataloguerBehavior;
            case NpcDioramaCatalog.SHELF -> shelverBehavior;
            case NpcDioramaCatalog.ANVIL, NpcDioramaCatalog.SMITH_INSPECTION -> smithBehavior;
            case NpcDioramaCatalog.CRAFTING -> apprenticeBehavior;
            case NpcDioramaCatalog.GUARD_SCAN -> sentinelBehavior;
            case NpcDioramaCatalog.SLEEPY_WATCH -> sleepyGuardBehavior;
            case NpcDioramaCatalog.ROUTE_CHECK -> courierBehavior;
            default -> throw new IllegalArgumentException("Unknown routine: " + routineName);
        };
        return track(actor.perform(catalog.routine(routineName)));
    }

    void attendTo(Player player) {
        requireReady();
        // Player implements Adventure Identified, so this intentionally exercises
        // the convenience overload while UUID remains the behavior identity.
        NpcAttentionLease lease = librarianBehavior.attendTo(player);
        synchronized (resourceLock) {
            if (closed.get()) {
                lease.close();
                throw new IllegalStateException("NPC dioramas have been closed");
            }
            attentionLeases.add(lease);
        }
        instance.scheduler().buildTask(() -> {
            lease.close();
            attentionLeases.remove(lease);
        }).delay(TaskSchedule.tick(100)).schedule();
    }

    NpcBehaviorSnapshot librarianSnapshot() {
        requireReady();
        return librarianBehavior.snapshot();
    }

    String librarianProfileName() {
        requireReady();
        return librarianBehavior.profile()
                .map(profile -> profile.personality().name())
                .orElse("none");
    }

    CompletionStage<Void> pauseLibrarian() {
        requireReady();
        return librarianBehavior.disable();
    }

    CompletionStage<Void> resumeLibrarian() {
        requireReady();
        return librarianBehavior.configure(catalog.profile(NpcDioramaCatalog.LIBRARIAN));
    }

    CompletionStage<Void> closeAsync() {
        if (!closed.compareAndSet(false, true)) {
            return platform.closeAsync();
        }
        synchronized (resourceLock) {
            attentionLeases.forEach(NpcAttentionLease::close);
            attentionLeases.clear();
            playbacks.forEach(NpcPlayback::cancel);
            playbacks.clear();
            conversations.forEach(NpcConversationRegistration::close);
            conversations.clear();
        }
        return platform.closeAsync();
    }

    private CompletableFuture<HouseServiceEntity> spawnActor(
            String name,
            String description,
            EntityTransform transform,
            NpcBehaviorProfile profile,
            BaseProp baseProp,
            Component chatLine
    ) {
        EntitySpec entitySpec = EntitySpec.builder(EntityTypes.PLAYER_LIKE_HUMANOID)
                .transform(transform)
                .flags(CommonEntityFlags.builder().gravity(false).build())
                .tag(Key.key("creative-library-example", "npc-diorama"))
                .build();
        HouseServiceSpec.Builder service = HouseServiceSpec.builder(entitySpec)
                .name(name)
                .description(description)
                .clickHandler(context -> sendInteractionChat(context, chatLine));
        if (profile != null) {
            service.behaviorProfile(profile);
        }
        return platform.spawnServiceAsync(instance, service.build())
                .thenApply(actor -> {
                    actor.entity().requireCapability(Equipable.class)
                            .equipment(baseProp.slot(), baseProp.item());
                    log("Base equipment authored for " + description + ": " + baseProp.item().key());
                    return actor;
                })
                .toCompletableFuture();
    }

    private void registerConversations() {
        synchronized (resourceLock) {
            if (closed.get()) {
                return;
            }
            registerConversationsWhileOpen();
        }
    }

    private void registerConversationsWhileOpen() {
        conversations.add(platform.conversationRegistry().register(
                NpcConversationTopic.of(
                        Key.key("creative-library-example", "library-research"),
                        List.of(
                                Component.text("The east stacks still need indexing."),
                                Component.text("I found three books filed under 'probably magic'."),
                                Component.text("That category is not in the catalogue."),
                                Component.text("It is now. I used a pencil."),
                                Component.text("Please leave room for 'definitely magic'.")
                        ),
                        List.of(Component.text("Oh - a reader needs us."))
                ),
                List.of(librarian.entity(), cataloguer.entity(), shelver.entity())
        ));
        // Overlaps the three-person registration to demonstrate atomic cast locks.
        conversations.add(platform.conversationRegistry().register(
                NpcConversationTopic.of(
                        Key.key("creative-library-example", "library-inventory"),
                        List.of(
                                Component.text("Are we counting borrowed books?"),
                                Component.text("Only the ones we would like returned."),
                                Component.text("That narrows it down very little.")
                        ),
                        List.of(Component.text("We can finish the inventory later."))
                ),
                List.of(librarian.entity(), shelver.entity())
        ));
        // A disjoint cast can perform concurrently with either library topic.
        conversations.add(platform.conversationRegistry().register(
                NpcConversationTopic.of(
                        Key.key("creative-library-example", "forge-orders"),
                        List.of(
                                Component.text("Quench after the color leaves the edge."),
                                Component.text("After the orange, before the red. Right?"),
                                Component.text("Watch the metal, not the clock."),
                                Component.text("The hinge order is next on the bench.")
                        ),
                        List.of(Component.text("We have company - set the hammer down."))
                ),
                List.of(smith.entity(), apprentice.entity())
        ));
        log("Registered a three-person conversation, an overlapping library topic, and a disjoint forge topic.");
    }

    private static HumanoidBehaviorCapable behavior(HouseServiceEntity actor) {
        return actor.entity().requireCapability(HumanoidBehaviorCapable.class);
    }

    private NpcPlayback track(NpcPlayback playback) {
        synchronized (resourceLock) {
            if (closed.get()) {
                playback.cancel();
                throw new IllegalStateException("NPC dioramas have been closed");
            }
            playbacks.add(playback);
        }
        playback.completion().whenComplete((ignored, failure) -> playbacks.remove(playback));
        return playback;
    }

    private void requireReady() {
        if (!ready()) {
            throw new IllegalStateException("NPC dioramas are still initializing or have been closed");
        }
    }

    private static void sendInteractionChat(HouseServiceClickContext context, Component line) {
        Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(context.interactor().uniqueId());
        if (player == null) {
            return;
        }
        String hand = context.hand().map(value -> " / " + value).orElse("");
        player.sendMessage(Component.text("[NPC chat] ", NamedTextColor.DARK_GRAY)
                .append(line)
                .append(Component.text("  [" + context.action() + hand + "]", NamedTextColor.DARK_GRAY)));
    }

    private void buildApproach() {
        fill(-25, 41, 1, 24, 41, 4, Block.STONE_BRICKS);
        fill(-2, 41, 4, 2, 41, 6, Block.OAK_PLANKS);
        lamp(-8, 2);
        lamp(8, 2);
    }

    private void buildLibrary() {
        fill(-7, 41, 5, 7, 41, 18, Block.SPRUCE_PLANKS);
        fill(-1, 42, 5, 1, 42, 17, Block.RED_CARPET);
        for (int x = -6; x <= 6; x++) {
            if (x >= -1 && x <= 1) {
                continue;
            }
            instance.setBlock(x, 42, 18, Block.BOOKSHELF);
            instance.setBlock(x, 43, 18, Block.BOOKSHELF);
        }
        for (int z = 8; z <= 17; z += 2) {
            instance.setBlock(-7, 42, z, Block.BOOKSHELF);
            instance.setBlock(-7, 43, z, Block.BOOKSHELF);
            instance.setBlock(7, 42, z, Block.BOOKSHELF);
            instance.setBlock(7, 43, z, Block.BOOKSHELF);
        }
        instance.setBlock(0, 42, 12, Block.LECTERN);
        instance.setBlock(-5, 42, 11, Block.CARTOGRAPHY_TABLE);
        instance.setBlock(5, 42, 15, Block.BARREL);
        lamp(-6, 6);
        lamp(6, 6);
    }

    private void buildForge() {
        fill(10, 41, 5, 25, 41, 18, Block.POLISHED_ANDESITE);
        fill(10, 42, 18, 25, 43, 18, Block.BRICKS);
        instance.setBlock(15, 42, 12, Block.ANVIL);
        instance.setBlock(20, 42, 11, Block.CRAFTING_TABLE);
        instance.setBlock(23, 42, 14, Block.FURNACE);
        instance.setBlock(24, 42, 14, Block.BLAST_FURNACE);
        instance.setBlock(21, 42, 17, Block.SMITHING_TABLE);
        instance.setBlock(12, 42, 16, Block.BARREL);
        for (int x = 11; x <= 25; x += 2) {
            instance.setBlock(x, 42, 5, Block.IRON_BARS);
        }
        lamp(11, 6);
        lamp(24, 6);
    }

    private void buildWatchPost() {
        fill(-25, 41, 5, -10, 41, 18, Block.COBBLESTONE);
        fill(-25, 42, 18, -10, 42, 18, Block.STONE_BRICKS);
        instance.setBlock(-20, 42, 9, Block.CARTOGRAPHY_TABLE);
        instance.setBlock(-17, 42, 8, Block.OAK_FENCE);
        instance.setBlock(-17, 43, 8, Block.OAK_FENCE);
        instance.setBlock(-17, 44, 8, Block.LANTERN);
        instance.setBlock(-23, 42, 16, Block.BARREL);
        instance.setBlock(-12, 42, 16, Block.BARREL);
        for (int x = -25; x <= -10; x++) {
            if (x % 3 == 0) {
                instance.setBlock(x, 43, 18, Block.IRON_BARS);
            }
        }
        lamp(-24, 6);
        lamp(-11, 6);
    }

    private void lamp(int x, int z) {
        instance.setBlock(x, 42, z, Block.OAK_FENCE);
        instance.setBlock(x, 43, z, Block.LANTERN);
    }

    private void fill(int minimumX, int y, int minimumZ, int maximumX, int maximumY, int maximumZ, Block block) {
        for (int x = minimumX; x <= maximumX; x++) {
            for (int blockY = y; blockY <= maximumY; blockY++) {
                for (int z = minimumZ; z <= maximumZ; z++) {
                    instance.setBlock(x, blockY, z, block);
                }
            }
        }
    }

    private static EntityTransform transform(double x, double y, double z, float yaw) {
        return new EntityTransform(x, y, z, yaw, 0.0f);
    }

    private static ItemDescriptor item(String value) {
        return new ItemDescriptor(Key.key("minecraft", value), 1);
    }

    private static void log(String message) {
        System.out.println("[minestom-entity-example] " + message);
    }

    private record BaseProp(EquipmentSlot slot, ItemDescriptor item) {
        private BaseProp {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(item, "item");
        }
    }
}
