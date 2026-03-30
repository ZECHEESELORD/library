package sh.harold.creative.library.example.minestom.entity;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.timer.TaskSchedule;
import sh.harold.creative.library.entity.CommonEntityFlags;
import sh.harold.creative.library.entity.EntitySpec;
import sh.harold.creative.library.entity.EntityTransform;
import sh.harold.creative.library.entity.EntityTypes;
import sh.harold.creative.library.entity.ManagedEntity;
import sh.harold.creative.library.entity.house.HouseServiceEntity;
import sh.harold.creative.library.entity.house.HouseServiceSpec;
import sh.harold.creative.library.entity.minestom.MinestomEntityPlatform;

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
                .interactionHandler(context -> log("Native villager interaction: " + context.kind() + " by " + context.interactor().uniqueId()))
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

        scheduleSmoke(instance, spawn, nativeVillager, temporaryStand, bankerService, guideService);
        log("Minestom entity smoke harness ready on localhost:" + PORT + ". Interact with the villager, Meredith, and Gideon.");
        minecraftServer.start(HOST, PORT);
    }

    private static void scheduleSmoke(
            InstanceContainer instance,
            Pos spawn,
            ManagedEntity nativeVillager,
            ManagedEntity temporaryStand,
            HouseServiceEntity bankerService,
            HouseServiceEntity guideService
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
    }

    private static void log(String message) {
        System.out.println("[minestom-entity-example] " + message);
    }
}
