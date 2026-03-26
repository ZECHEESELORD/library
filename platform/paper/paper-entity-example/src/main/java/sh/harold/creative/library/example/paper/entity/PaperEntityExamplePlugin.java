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
import sh.harold.creative.library.entity.ManagedEntity;
import sh.harold.creative.library.entity.house.HouseServiceEntity;
import sh.harold.creative.library.entity.paper.PaperEntityPlatform;
import sh.harold.creative.library.entity.paper.citizens.PaperCitizensEntityPlatform;
import sh.harold.creative.library.entity.house.HousePromptMode;
import sh.harold.creative.library.entity.house.HouseRole;
import sh.harold.creative.library.entity.house.HouseServiceSpec;

public final class PaperEntityExamplePlugin extends JavaPlugin {

    private PaperEntityPlatform nativePlatform;
    private PaperCitizensEntityPlatform citizensPlatform;
    private ManagedEntity nativeVillager;
    private ManagedEntity temporaryStand;
    private HouseServiceEntity bankerService;
    private HouseServiceEntity citizensGuideService;

    @Override
    public void onEnable() {
        nativePlatform = new PaperEntityPlatform(this);

        World world = Bukkit.getWorlds().getFirst();
        Location spawn = world.getSpawnLocation();

        nativeVillager = nativePlatform.spawn(world, EntitySpec.builder(EntityTypes.VILLAGER)
                .transform(new EntityTransform(spawn.getX() + 2.0, spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch()))
                .flags(CommonEntityFlags.builder().customName(Component.text("Entity Example Villager")).customNameVisible(true).build())
                .interactionHandler(context -> getLogger().info("Native villager interaction: " + context.kind() + " by " + context.interactor().uniqueId()))
                .build());

        temporaryStand = nativePlatform.spawn(world, EntitySpec.builder(EntityTypes.ARMOR_STAND)
                .transform(new EntityTransform(spawn.getX() + 3.5, spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch()))
                .flags(CommonEntityFlags.builder().customName(Component.text("Smoke Temp Stand")).customNameVisible(true).gravity(false).build())
                .build());

        bankerService = nativePlatform.spawnService(world, HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.VILLAGER)
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
            citizensGuideService = citizensPlatform.spawnService(world, HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.PLAYER_LIKE_HUMANOID)
                            .transform(new EntityTransform(spawn.getX() + 6.0, spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch()))
                            .flags(CommonEntityFlags.builder().gravity(false).build())
                            .build())
                    .name(Component.text("Gideon"))
                    .role(HouseRole.of(Component.text("Guide")))
                    .promptMode(HousePromptMode.TALK)
                    .clickHandler(context -> getLogger().info("Citizens guide clicked by " + context.interactor().uniqueId()))
                    .build());
        } else {
            getLogger().info("Citizens not present; creative:player_like_humanoid remains intentionally unsupported on native Paper.");
        }

        scheduleSmoke(world, spawn);
        getLogger().info("Paper entity smoke harness ready. Right-click the villager, Meredith, and Gideon when present.");
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

    private void scheduleSmoke(World world, Location spawn) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            nativeVillager.customName(Component.text("Entity Smoke Villager"));
            nativeVillager.glowing(true);
            nativeVillager.silent(true);
            getLogger().info("Smoke step 1: renamed native villager and applied glowing + silent.");
        }, 20L);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            nativeVillager.teleport(new EntityTransform(spawn.getX() + 2.0, spawn.getY(), spawn.getZ() + 1.5, spawn.getYaw(), spawn.getPitch()));
            bankerService.entity().gravity(false);
            getLogger().info("Smoke step 2: teleported native villager and reaffirmed House service gravity toggle.");
        }, 40L);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            temporaryStand.despawn();
            getLogger().info("Smoke step 3: despawned temporary armor stand.");
        }, 60L);

        if (citizensGuideService != null) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                citizensGuideService.entity().customName(Component.text("Gideon (Smoke)"));
                citizensGuideService.entity().glowing(true);
                citizensGuideService.teleport(new EntityTransform(spawn.getX() + 6.0, spawn.getY(), spawn.getZ() + 1.5, spawn.getYaw(), spawn.getPitch()));
                getLogger().info("Smoke step 4: renamed, toggled, and teleported the Citizens-backed guide.");
            }, 80L);
        }
    }
}
