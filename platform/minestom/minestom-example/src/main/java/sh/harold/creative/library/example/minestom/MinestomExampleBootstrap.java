package sh.harold.creative.library.example.minestom;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import sh.harold.creative.library.camera.minestom.MinestomCameraMotionPlatform;
import sh.harold.creative.library.menu.minestom.MinestomMenuPlatform;
import sh.harold.creative.library.sound.SoundCueKeys;
import sh.harold.creative.library.sound.minestom.MinestomSoundCuePlatform;
import sh.harold.creative.library.overlay.minestom.MinestomScreenOverlayPlatform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MinestomExampleBootstrap {

    private static final String HOST = "0.0.0.0";
    private static final int PORT = 25565;
    private static final List<Material> TEST_HOTBAR_POOL = List.of(
            Material.COMPASS,
            Material.SLIME_BALL,
            Material.HOPPER,
            Material.CHEST,
            Material.GOLDEN_HOE,
            Material.BOOK,
            Material.FISHING_ROD,
            Material.ENDER_PEARL,
            Material.COOKIE,
            Material.CLOCK,
            Material.MAP,
            Material.SHIELD,
            Material.EMERALD,
            Material.WHEAT,
            Material.DIAMOND_SWORD,
            Material.BLAZE_POWDER,
            Material.NETHER_STAR,
            Material.SPYGLASS
    );

    private MinestomExampleBootstrap() {
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

        MinestomSoundCuePlatform sounds = new MinestomSoundCuePlatform();
        MinestomCameraMotionPlatform camera = new MinestomCameraMotionPlatform();
        MinestomScreenOverlayPlatform overlays = new MinestomScreenOverlayPlatform(MinecraftServer.getGlobalEventHandler());
        MinestomMenuPlatform menus = new MinestomMenuPlatform(new sh.harold.creative.library.menu.core.StandardMenuService(),
                MinecraftServer.getGlobalEventHandler(), sounds);
        MinestomMenuExampleMenus examples = new MinestomMenuExampleMenus(menus);
        MinestomDevHarnessMessages feedback = new MinestomDevHarnessMessages();
        MinestomCameraMotionExamples cameraExamples = new MinestomCameraMotionExamples(camera, feedback);
        MinestomScreenOverlayExamples overlayExamples = new MinestomScreenOverlayExamples(overlays, feedback);
        MinestomMessageFacadeExamples messageExamples = new MinestomMessageFacadeExamples();
        MinestomPrimitiveExamples primitiveExamples = new MinestomPrimitiveExamples(
                MinecraftServer.getSchedulerManager(),
                sounds,
                overlays,
                camera,
                feedback
        );
        Pos spawn = new Pos(0.5, 42.0, 0.5);
        MinestomCooldownHarness cooldownExamples = new MinestomCooldownHarness(instance, spawn, menus, feedback);
        MinestomEntityExampleHarness entityExamples = new MinestomEntityExampleHarness(instance, spawn, menus, examples, sounds, feedback);

        entityExamples.reset();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            cooldownExamples.close();
            primitiveExamples.close();
            entityExamples.close();
            camera.close();
            overlays.close();
            menus.close();
            sounds.close();
        }));

        MinecraftServer.getCommandManager().register(new MinestomMenuExamplesCommand(menus, examples, feedback));
        MinecraftServer.getCommandManager().register(new MinestomMessageFacadeCommand(messageExamples, feedback));
        MinecraftServer.getCommandManager().register(new MinestomSoundCueCommand(new MinestomSoundCueExamples(sounds, feedback), feedback));
        MinecraftServer.getCommandManager().register(new MinestomCameraMotionCommand(cameraExamples, feedback));
        MinecraftServer.getCommandManager().register(new MinestomScreenOverlayCommand(overlayExamples, feedback));
        MinecraftServer.getCommandManager().register(new MinestomEntityExamplesCommand(entityExamples, feedback));
        MinecraftServer.getCommandManager().register(new MinestomCooldownCommand(cooldownExamples, feedback));
        MinecraftServer.getCommandManager().register(new MinestomHarnessVariantCommand(
                "testtweens",
                "testtween",
                "all",
                primitiveExamples.tweenUsage(),
                new String[]{"all", "easings", "envelopes", "vectors", "angles", "delay", "zero", "repeat", "pause", "replace", "refresh", "clear", "help"},
                feedback,
                (player, variant, extraArgs) -> primitiveExamples.playTween(player, variant)
        ));
        MinecraftServer.getCommandManager().register(new MinestomHarnessVariantCommand(
                "testcurves",
                "testcurve",
                "all",
                primitiveExamples.curveUsage(),
                new String[]{"all", "line", "quadratic", "cubic", "catmull", "arc", "split", "trim", "reverse", "resample", "clear", "help"},
                feedback,
                (player, variant, extraArgs) -> primitiveExamples.playCurve(player, variant)
        ));
        MinecraftServer.getCommandManager().register(new MinestomHarnessVariantCommand(
                "testtelegraphs",
                "testtelegraph",
                "showcase",
                primitiveExamples.telegraphUsage(),
                new String[]{"showcase", "countdown", "circle", "ring", "rectangle", "line", "corridor", "cone", "arc", "path", "refresh", "replace", "missinganchor", "scopes", "clear", "help"},
                feedback,
                primitiveExamples::playTelegraph
        ));
        MinecraftServer.getCommandManager().register(new MinestomHarnessVariantCommand(
                "testtrajectories",
                "testtrajectory",
                "showcase",
                primitiveExamples.trajectoryUsage(),
                new String[]{"showcase", "throwable", "arrow", "responses", "oneshot", "everytick", "threshold", "refresh", "replace", "scopes", "clear", "help"},
                feedback,
                primitiveExamples::playTrajectory
        ));
        MinecraftServer.getCommandManager().register(new MinestomHarnessVariantCommand(
                "testimpulses",
                "testimpulse",
                "showcase",
                primitiveExamples.impulseUsage(),
                new String[]{"showcase", "add", "set", "clamped", "dash", "pull", "push", "launch", "local", "masks", "stack", "refresh", "replace", "clear", "help"},
                feedback,
                (player, variant, extraArgs) -> primitiveExamples.playImpulse(player, variant)
        ));
        MinecraftServer.getCommandManager().register(new MinestomHarnessVariantCommand(
                "testambient",
                "testambientzone",
                "all",
                primitiveExamples.ambientUsage(),
                new String[]{"all", "channels", "hardedge", "feather", "weights", "blend", "priority", "ttl", "refresh", "clear", "help"},
                feedback,
                (player, variant, extraArgs) -> primitiveExamples.playAmbient(player, variant)
        ));
        MinecraftServer.getCommandManager().register(new MinestomHarnessVariantCommand(
                "testprimitives",
                "testprimitive",
                "all",
                primitiveExamples.primitivesUsage(),
                new String[]{"all", "pathwarning", "previewimpact", "dashassist", "zonepulse", "clear", "help"},
                feedback,
                (player, variant, extraArgs) -> primitiveExamples.playPrimitives(player, variant)
        ));
        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(instance);
            event.getPlayer().setRespawnPoint(spawn);
        });
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent.class, event -> {
            if (!event.isFirstSpawn()) {
                primitiveExamples.discard(event.getPlayer().getUuid());
            }
            populateTestingHotbar(event.getPlayer());
            if (event.isFirstSpawn()) {
                entityExamples.ensureSpawned();
                sounds.play(event.getPlayer(), SoundCueKeys.REWARD_DISCOVERY);
                menus.open(event.getPlayer(), examples.tabsGallery());
                feedback.sendQuickStart(event.getPlayer());
            }
        });
        MinecraftServer.getGlobalEventHandler().addListener(PlayerDisconnectEvent.class, event ->
                primitiveExamples.discard(event.getPlayer().getUuid()));

        log("Unified Minestom dev harness ready on localhost:" + PORT
                + ". Use /testmenus, /testmessages, /testsoundfx, /testcamera, /testoverlays, /testnpcs, /testcooldowns, and /testprimitives.");
        minecraftServer.start(HOST, PORT);
    }

    private static void log(String message) {
        System.out.println("[minestom-example] " + message);
    }

    private static void populateTestingHotbar(Player player) {
        List<Material> materials = new ArrayList<>(TEST_HOTBAR_POOL);
        Collections.shuffle(materials);
        for (int slot = 0; slot < 9; slot++) {
            player.getInventory().setItemStack(slot, ItemStack.of(materials.get(slot)));
        }
        player.setHeldItemSlot((byte) 0);
    }
}
