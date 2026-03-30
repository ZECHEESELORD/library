package sh.harold.creative.library.example.minestom;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import sh.harold.creative.library.camera.minestom.MinestomCameraMotionPlatform;
import sh.harold.creative.library.menu.minestom.MinestomMenuPlatform;
import sh.harold.creative.library.sound.SoundCueKeys;
import sh.harold.creative.library.sound.minestom.MinestomSoundCuePlatform;
import sh.harold.creative.library.overlay.minestom.MinestomScreenOverlayPlatform;

public final class MinestomExampleBootstrap {

    private static final String HOST = "0.0.0.0";
    private static final int PORT = 25565;

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
        Pos spawn = new Pos(0.5, 42.0, 0.5);
        MinestomEntityExampleHarness entityExamples = new MinestomEntityExampleHarness(instance, spawn, menus, examples, sounds, feedback);

        entityExamples.reset();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
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
        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(instance);
            event.getPlayer().setRespawnPoint(spawn);
        });
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn()) {
                entityExamples.ensureSpawned();
                sounds.play(event.getPlayer(), SoundCueKeys.REWARD_DISCOVERY);
                menus.open(event.getPlayer(), examples.tabsGallery());
                feedback.sendQuickStart(event.getPlayer());
            }
        });

        log("Unified Minestom dev harness ready on localhost:" + PORT
                + ". Use /testmenus, /testmessages, /testsoundfx, /testcamera, /testoverlays, and /testnpcs.");
        minecraftServer.start(HOST, PORT);
    }

    private static void log(String message) {
        System.out.println("[minestom-example] " + message);
    }
}
