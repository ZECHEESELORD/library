package sh.harold.creative.library.example.minestom.entity;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.InstanceContainer;
import sh.harold.creative.library.entity.CommonEntityFlags;
import sh.harold.creative.library.entity.EntitySpec;
import sh.harold.creative.library.entity.EntityTransform;
import sh.harold.creative.library.entity.EntityTypes;
import sh.harold.creative.library.entity.house.HousePromptMode;
import sh.harold.creative.library.entity.house.HouseRole;
import sh.harold.creative.library.entity.house.HouseServiceSpec;
import sh.harold.creative.library.entity.minestom.MinestomEntityPlatform;

public final class MinestomEntityExampleBootstrap {

    private MinestomEntityExampleBootstrap() {
    }

    public static void main(String[] args) {
        MinecraftServer.init();

        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.enableAutoChunkLoad(true);
        instance.loadChunk(0, 0).join();

        MinestomEntityPlatform platform = new MinestomEntityPlatform();
        Pos spawn = new Pos(0.0, 42.0, 0.0);

        platform.spawn(instance, EntitySpec.builder(EntityTypes.VILLAGER)
                .transform(new EntityTransform(spawn.x() + 2.0, spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()))
                .flags(CommonEntityFlags.builder().customName(Component.text("Entity Example Villager")).customNameVisible(true).build())
                .build());

        platform.spawn(instance, EntitySpec.builder(EntityTypes.TEXT_DISPLAY)
                .transform(new EntityTransform(spawn.x() + 3.0, spawn.y() + 1.0, spawn.z(), spawn.yaw(), spawn.pitch()))
                .flags(CommonEntityFlags.builder().gravity(false).build())
                .build());

        platform.spawnService(instance, HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.VILLAGER)
                        .transform(new EntityTransform(spawn.x() + 4.0, spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()))
                        .flags(CommonEntityFlags.builder().gravity(false).build())
                        .build())
                .name(Component.text("Meredith"))
                .role(HouseRole.of(Component.text("Banker")))
                .promptMode(HousePromptMode.VIEW)
                .build());

        platform.spawnService(instance, HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.PLAYER_LIKE_HUMANOID)
                        .transform(new EntityTransform(spawn.x() + 6.0, spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()))
                        .flags(CommonEntityFlags.builder().gravity(false).build())
                        .build())
                .name(Component.text("Gideon"))
                .role(HouseRole.of(Component.text("Guide")))
                .promptMode(HousePromptMode.TALK)
                .build());
    }
}
