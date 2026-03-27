package sh.harold.creative.library.example.minestom;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;
import sh.harold.creative.library.entity.CommonEntityFlags;
import sh.harold.creative.library.entity.EntitySpec;
import sh.harold.creative.library.entity.EntityTransform;
import sh.harold.creative.library.entity.EntityTypes;
import sh.harold.creative.library.entity.InteractionKind;
import sh.harold.creative.library.entity.InteractorRef;
import sh.harold.creative.library.entity.ManagedEntity;
import sh.harold.creative.library.entity.house.HousePromptMode;
import sh.harold.creative.library.entity.house.HouseRole;
import sh.harold.creative.library.entity.house.HouseServiceEntity;
import sh.harold.creative.library.entity.house.HouseServiceSpec;
import sh.harold.creative.library.entity.minestom.MinestomEntityPlatform;
import sh.harold.creative.library.menu.minestom.MinestomMenuPlatform;
import sh.harold.creative.library.message.Message;
import sh.harold.creative.library.sound.SoundCueKeys;
import sh.harold.creative.library.sound.minestom.MinestomSoundCuePlatform;

import java.util.Objects;
import java.util.function.Consumer;

final class MinestomEntityExampleHarness implements AutoCloseable {

    private final InstanceContainer instance;
    private final Pos spawn;
    private final MinestomMenuPlatform menus;
    private final MinestomMenuExampleMenus menuExamples;
    private final MinestomSoundCuePlatform sounds;
    private final MinestomDevHarnessMessages feedback;
    private final MinestomEntityPlatform platform;

    private ManagedEntity nativeVillager;
    private ManagedEntity temporaryStand;
    private HouseServiceEntity bankerService;
    private HouseServiceEntity guideService;

    MinestomEntityExampleHarness(
            InstanceContainer instance,
            Pos spawn,
            MinestomMenuPlatform menus,
            MinestomMenuExampleMenus menuExamples,
            MinestomSoundCuePlatform sounds,
            MinestomDevHarnessMessages feedback
    ) {
        this.instance = Objects.requireNonNull(instance, "instance");
        this.spawn = Objects.requireNonNull(spawn, "spawn");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.menuExamples = Objects.requireNonNull(menuExamples, "menuExamples");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.platform = new MinestomEntityPlatform();
    }

    void ensureSpawned() {
        if (!isSpawned()) {
            reset();
        }
    }

    void reset() {
        clear();

        nativeVillager = platform.spawn(instance, EntitySpec.builder(EntityTypes.VILLAGER)
                .transform(transform(spawn.x() + 2.0, spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()))
                .flags(CommonEntityFlags.builder()
                        .customName(Component.text("Entity Example Villager"))
                        .customNameVisible(true)
                        .build())
                .interactionHandler(context -> {
                    log("Native villager interaction: " + context.kind() + " by " + context.interactor().uniqueId());
                    withInteractor(context.interactor(), player -> {
                        sounds.play(player, SoundCueKeys.INTERACTION_NPC);
                        feedback.info(
                                player,
                                "Interacted with the native villager using {kind}.",
                                Message.slot("kind", interactionLabel(context.kind()))
                        );
                    });
                })
                .build());

        temporaryStand = platform.spawn(instance, EntitySpec.builder(EntityTypes.ARMOR_STAND)
                .transform(transform(spawn.x() + 3.5, spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()))
                .flags(CommonEntityFlags.builder()
                        .customName(Component.text("Smoke Temp Stand"))
                        .customNameVisible(true)
                        .gravity(false)
                        .build())
                .build());

        bankerService = platform.spawnService(instance, HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.VILLAGER)
                        .transform(transform(spawn.x() + 4.0, spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()))
                        .flags(CommonEntityFlags.builder().gravity(false).build())
                        .build())
                .name(Component.text("Meredith"))
                .role(HouseRole.of(Component.text("Banker")))
                .promptMode(HousePromptMode.VIEW)
                .clickHandler(context -> withInteractor(context.interactor(), player -> {
                    sounds.play(player, SoundCueKeys.INTERACTION_NPC);
                    menus.open(player, menuExamples.tabsGallery());
                    feedback.success(player, "{name} opened the tabs gallery.", Message.slot("name", "Meredith"));
                }))
                .build());

        guideService = platform.spawnService(instance, HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.PLAYER_LIKE_HUMANOID)
                        .transform(transform(spawn.x() + 6.0, spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()))
                        .flags(CommonEntityFlags.builder().gravity(false).build())
                        .build())
                .name(Component.text("Gideon"))
                .role(HouseRole.of(Component.text("Guide")))
                .promptMode(HousePromptMode.TALK)
                .clickHandler(context -> withInteractor(context.interactor(), player -> {
                    sounds.play(player, SoundCueKeys.INTERACTION_NPC);
                    feedback.sendSummary(player);
                }))
                .build());

        log("Reset Minestom entity and House preview near spawn.");
    }

    void clear() {
        despawn(guideService);
        guideService = null;
        despawn(bankerService);
        bankerService = null;
        despawn(nativeVillager);
        nativeVillager = null;
        despawn(temporaryStand);
        temporaryStand = null;
    }

    @Override
    public void close() {
        clear();
        platform.close();
    }

    private boolean isSpawned() {
        return nativeVillager != null || temporaryStand != null || bankerService != null || guideService != null;
    }

    private void withInteractor(InteractorRef interactor, Consumer<Player> action) {
        Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(interactor.uniqueId());
        if (player != null) {
            action.accept(player);
        }
    }

    private static void despawn(ManagedEntity entity) {
        if (entity != null) {
            entity.despawn();
        }
    }

    private static void despawn(HouseServiceEntity entity) {
        if (entity != null) {
            entity.despawn();
        }
    }

    private static String interactionLabel(InteractionKind kind) {
        return switch (kind) {
            case PRIMARY -> "primary click";
            case SECONDARY -> "secondary click";
            case ATTACK -> "attack";
        };
    }

    private static EntityTransform transform(double x, double y, double z, float yaw, float pitch) {
        return new EntityTransform(x, y, z, yaw, pitch);
    }

    private static void log(String message) {
        System.out.println("[minestom-example] " + message);
    }
}
