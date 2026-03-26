package sh.harold.creative.library.example.paper.entity;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.creative.library.entity.CommonEntityFlags;
import sh.harold.creative.library.entity.EntitySpec;
import sh.harold.creative.library.entity.EntityTransform;
import sh.harold.creative.library.entity.EntityTypes;
import sh.harold.creative.library.entity.paper.PaperEntityPlatform;
import sh.harold.creative.library.entity.paper.citizens.PaperCitizensEntityPlatform;
import sh.harold.creative.library.entity.house.HousePromptMode;
import sh.harold.creative.library.entity.house.HouseRole;
import sh.harold.creative.library.entity.house.HouseServiceSpec;

public final class PaperEntityExamplePlugin extends JavaPlugin {

    private PaperEntityPlatform nativePlatform;
    private PaperCitizensEntityPlatform citizensPlatform;

    @Override
    public void onEnable() {
        nativePlatform = new PaperEntityPlatform(this);

        World world = Bukkit.getWorlds().getFirst();
        Location spawn = world.getSpawnLocation();

        nativePlatform.spawn(world, EntitySpec.builder(EntityTypes.VILLAGER)
                .transform(new EntityTransform(spawn.getX() + 2.0, spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch()))
                .flags(CommonEntityFlags.builder().customName(Component.text("Entity Example Villager")).customNameVisible(true).build())
                .build());

        nativePlatform.spawnService(world, HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.VILLAGER)
                        .transform(new EntityTransform(spawn.getX() + 4.0, spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch()))
                        .flags(CommonEntityFlags.builder().gravity(false).build())
                        .build())
                .name(Component.text("Meredith"))
                .role(HouseRole.of(Component.text("Banker")))
                .promptMode(HousePromptMode.OPEN)
                .clickHandler(context -> getLogger().info("House banker clicked by " + context.interactor().uniqueId()))
                .build());

        if (Bukkit.getPluginManager().getPlugin("Citizens") != null) {
            citizensPlatform = new PaperCitizensEntityPlatform(this);
            citizensPlatform.spawnService(world, HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.PLAYER_LIKE_HUMANOID)
                            .transform(new EntityTransform(spawn.getX() + 6.0, spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch()))
                            .flags(CommonEntityFlags.builder().gravity(false).build())
                            .build())
                    .name(Component.text("Gideon"))
                    .role(HouseRole.of(Component.text("Guide")))
                    .promptMode(HousePromptMode.TALK)
                    .build());
        }
    }

    @Override
    public void onDisable() {
        if (citizensPlatform != null) {
            citizensPlatform.close();
        }
        if (nativePlatform != null) {
            nativePlatform.close();
        }
    }
}
