package sh.harold.library.example.minestom.entity;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.timer.TaskSchedule;
import sh.harold.library.entity.CommonEntityFlags;
import sh.harold.library.entity.EntityInteractionResult;
import sh.harold.library.entity.EntitySpec;
import sh.harold.library.entity.EntityTransform;
import sh.harold.library.entity.EntityTypes;
import sh.harold.library.entity.ItemDescriptor;
import sh.harold.library.entity.ManagedEntity;
import sh.harold.library.entity.house.HouseServiceEntity;
import sh.harold.library.entity.house.HouseServiceSpec;
import sh.harold.library.entity.minestom.MinestomEntityPlatform;
import sh.harold.library.npc.behavior.HumanoidBehaviorCapable;
import sh.harold.library.npc.behavior.NpcBehaviorProfile;
import sh.harold.library.npc.behavior.NpcConversationTopic;
import sh.harold.library.npc.behavior.NpcCooldownRange;
import sh.harold.library.npc.behavior.NpcPersonalityPreset;
import sh.harold.library.npc.behavior.NpcRoutine;
import sh.harold.library.npc.behavior.NpcRoutines;
import sh.harold.library.npc.behavior.NpcVoiceProfiles;
import sh.harold.library.spatial.AnchorRef;
import sh.harold.library.spatial.AnchorSnapshot;
import sh.harold.library.spatial.Frame3;
import sh.harold.library.spatial.SpaceId;
import sh.harold.library.spatial.Vec3;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class MinestomEntityExampleBootstrap {

    private static final String HOST = "0.0.0.0";
    private static final int PORT = 25565;

    private MinestomEntityExampleBootstrap() {
    }

    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();

        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.enableAutoChunkLoad(true);
        instance.setGenerator(unit -> {
            unit.modifier().fillHeight(0, 40, Block.STONE);
            unit.modifier().fillHeight(40, 41, Block.GRASS_BLOCK);
        });
        instance.loadChunk(0, 0).join();

        MinestomEntityPlatform platform = new MinestomEntityPlatform();
        Pos spawn = new Pos(0.5, 42.0, 0.5);
        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(instance);
            event.getPlayer().setRespawnPoint(spawn);
        });

        ManagedEntity nativeVillager = platform.spawn(instance, EntitySpec.builder(EntityTypes.VILLAGER)
                .transform(new EntityTransform(spawn.x() + 2.0, spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()))
                .flags(CommonEntityFlags.builder().customName(Component.text("Entity Example Villager")).customNameVisible(true).build())
                .interactionHandler(context -> {
                    log("Native villager interaction: " + context.action() + " by " + context.interactor().uniqueId());
                    return EntityInteractionResult.CONSUME;
                })
                .build());

        ManagedEntity temporaryStand = platform.spawn(instance, EntitySpec.builder(EntityTypes.ARMOR_STAND)
                .transform(new EntityTransform(spawn.x() + 3.5, spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()))
                .flags(CommonEntityFlags.builder().customName(Component.text("Smoke Temp Stand")).customNameVisible(true).gravity(false).build())
                .build());

        HouseServiceEntity bankerService = platform.spawnService(instance, HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.VILLAGER)
                        .transform(new EntityTransform(spawn.x() + 4.0, spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()))
                        .flags(CommonEntityFlags.builder().gravity(false).build())
                        .build())
                .name("&bMeredith")
                .description("Banker")
                .clickHandler(context -> log("House banker clicked by " + context.interactor().uniqueId()))
                .build());

        HouseServiceEntity guideService = platform.spawnService(instance, HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.PLAYER_LIKE_HUMANOID)
                        .transform(new EntityTransform(spawn.x() + 6.0, spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()))
                        .flags(CommonEntityFlags.builder().gravity(false).build())
                        .build())
                .name("&aGideon")
                .description("Guide")
                .clickHandler(context -> log("House guide clicked by " + context.interactor().uniqueId()))
                .build());

        ManagedEntity artisan = platform.spawn(instance, EntitySpec.builder(EntityTypes.PLAYER_LIKE_HUMANOID)
                .transform(new EntityTransform(spawn.x() + 8.0, spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()))
                .flags(CommonEntityFlags.builder().customName(Component.text("Rowan the Artisan")).customNameVisible(true).build())
                .build());

        SpaceId space = SpaceId.of("minestom", instance.getUuid().toString());
        AnchorRef lectern = anchor(space, spawn.x() + 6.0, spawn.y() + 1.0, spawn.z() + 1.2);
        AnchorRef anvil = anchor(space, spawn.x() + 6.8, spawn.y() + 0.8, spawn.z() + 1.0);
        AnchorRef shelf = anchor(space, spawn.x() + 5.2, spawn.y() + 1.3, spawn.z() + 0.8);
        AnchorRef table = anchor(space, spawn.x() + 7.0, spawn.y() + 0.8, spawn.z() + 0.7);
        List<NpcRoutine> routines = List.of(
                NpcRoutines.lecternStudy(lectern),
                NpcRoutines.anvilForging(anvil, item("iron_pickaxe")),
                NpcRoutines.shelfDistraction(shelf),
                NpcRoutines.tableCrafting(table, List.of(item("oak_planks"), item("stick"), item("string")))
        );

        NpcBehaviorProfile guideProfile = profile(NpcPersonalityPreset.CURIOUS, routines, "Oh! Hello there.");
        NpcBehaviorProfile artisanProfile = profile(
                NpcPersonalityPreset.CONFIDENT,
                List.of(routines.get(1), routines.get(3)),
                "Careful around the tools."
        );
        HumanoidBehaviorCapable guideBehavior = guideService.entity().requireCapability(HumanoidBehaviorCapable.class);
        HumanoidBehaviorCapable artisanBehavior = artisan.requireCapability(HumanoidBehaviorCapable.class);
        CompletableFuture.allOf(
                guideBehavior.configure(guideProfile).toCompletableFuture(),
                artisanBehavior.configure(artisanProfile).toCompletableFuture()
        ).thenRun(() -> {
            guideBehavior.speak(Component.text("The Minestom behavior smoke test is live.", NamedTextColor.AQUA));
            platform.conversationRegistry().register(
                    NpcConversationTopic.of(
                            Key.key("example", "workshop-chat"),
                            List.of(
                                    Component.text("Did you finish that order?"),
                                    Component.text("Almost. The last fitting needs patience."),
                                    Component.text("The shelves have been oddly distracting today."),
                                    Component.text("That sounds like an excuse to reorganize them.")
                            ),
                            List.of(Component.text("We were in the middle of something!"))
                    ),
                    List.of(guideService.entity(), artisan)
            );
        });

        scheduleSmoke(instance, spawn, nativeVillager, temporaryStand, bankerService, guideService, guideBehavior, routines);
        log("Minestom entity smoke harness ready on localhost:" + PORT + ". Interact with the villager, Meredith, and Gideon.");
        minecraftServer.start(HOST, PORT);
    }

    private static void scheduleSmoke(
            InstanceContainer instance,
            Pos spawn,
            ManagedEntity nativeVillager,
            ManagedEntity temporaryStand,
            HouseServiceEntity bankerService,
            HouseServiceEntity guideService,
            HumanoidBehaviorCapable guideBehavior,
            List<NpcRoutine> routines
    ) {
        instance.scheduler().buildTask(() -> {
            nativeVillager.customName(Component.text("Entity Smoke Villager"));
            nativeVillager.glowing(true);
            nativeVillager.silent(true);
            log("Smoke step 1: renamed native villager and applied glowing + silent.");
        }).delay(TaskSchedule.tick(20)).schedule();

        instance.scheduler().buildTask(() -> {
            nativeVillager.teleport(new EntityTransform(spawn.x() + 2.0, spawn.y(), spawn.z() + 1.5, spawn.yaw(), spawn.pitch()));
            bankerService.entity().gravity(false);
            log("Smoke step 2: teleported native villager and reaffirmed House service gravity toggle.");
        }).delay(TaskSchedule.tick(40)).schedule();

        instance.scheduler().buildTask(() -> {
            temporaryStand.despawn();
            log("Smoke step 3: despawned temporary armor stand.");
        }).delay(TaskSchedule.tick(60)).schedule();

        instance.scheduler().buildTask(() -> {
            guideService.entity().glowing(true);
            guideService.teleport(new EntityTransform(spawn.x() + 6.0, spawn.y(), spawn.z() + 1.5, spawn.yaw(), spawn.pitch()));
            log("Smoke step 4: toggled and teleported the mannequin-backed guide.");
        }).delay(TaskSchedule.tick(80)).schedule();

        for (int index = 0; index < routines.size(); index++) {
            int routineIndex = index;
            instance.scheduler().buildTask(() -> {
                guideBehavior.perform(routines.get(routineIndex));
                log("Behavior smoke: started " + routines.get(routineIndex).key() + ".");
            }).delay(TaskSchedule.tick(140 + index * 180)).schedule();
        }
    }

    private static NpcBehaviorProfile profile(
            NpcPersonalityPreset personality,
            List<NpcRoutine> routines,
            String interactionLine
    ) {
        NpcBehaviorProfile.Builder builder = NpcBehaviorProfile.builder(personality)
                .voice(NpcVoiceProfiles.WARM_VILLAGER)
                .interactionLine(Component.text(interactionLine, NamedTextColor.YELLOW))
                .conversationInterruptionLine(Component.text("A moment—we have company.", NamedTextColor.GRAY));
        for (NpcRoutine routine : routines) {
            builder.idle(routine, 1, NpcCooldownRange.seconds(8.0, 16.0));
        }
        return builder.build();
    }

    private static AnchorRef anchor(SpaceId space, double x, double y, double z) {
        return new AnchorRef.Fixed(new AnchorSnapshot(space, Frame3.world(new Vec3(x, y, z))));
    }

    private static ItemDescriptor item(String value) {
        return new ItemDescriptor(Key.key("minecraft", value), 1);
    }

    private static void log(String message) {
        System.out.println("[minestom-entity-example] " + message);
    }
}
