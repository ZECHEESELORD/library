package sh.harold.library.example.minestom.entity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import sh.harold.library.entity.minestom.MinestomEntityPlatform;

import java.util.concurrent.TimeUnit;

/**
 * Standalone, walk-up NPC behavior showcase. Run it and join localhost:25565,
 * then use {@code /npcdemo help} to drive the imperative parts of the API.
 */
public final class MinestomEntityExampleBootstrap {

    private static final String HOST = "0.0.0.0";
    private static final int PORT = 25565;
    private static final Pos PLAYER_SPAWN = new Pos(0.5, 42.0, 0.5, 0.0f, 0.0f);

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
        loadShowcaseChunks(instance);

        MinestomEntityPlatform platform = new MinestomEntityPlatform();
        MinestomNpcDioramas dioramas = new MinestomNpcDioramas(instance, platform);
        dioramas.buildScenery();
        dioramas.spawnAsync().whenComplete((ignored, failure) -> {
            if (failure == null) {
                log("All NPC dioramas are configured and live.");
            } else {
                log("NPC diorama initialization failed: " + failure.getMessage());
                failure.printStackTrace(System.err);
            }
        });

        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(instance);
            event.getPlayer().setRespawnPoint(PLAYER_SPAWN);
        });
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent.class, event -> {
            event.getPlayer().sendMessage(Component.text(
                    "NPC behavior dioramas: library ahead, watch post left, forge right.",
                    NamedTextColor.GOLD
            ));
            event.getPlayer().sendMessage(Component.text(
                    "Use /npcdemo help for speech, routines, manual attention, snapshots, and lifecycle controls.",
                    NamedTextColor.GRAY
            ));
        });
        MinecraftServer.getCommandManager().register(new MinestomNpcDioramaCommand(dioramas));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                dioramas.closeAsync().toCompletableFuture().orTimeout(10, TimeUnit.SECONDS).join();
                log("NPC diorama resources retired cleanly.");
            } catch (RuntimeException failure) {
                log("NPC diorama shutdown did not finish cleanly: " + failure.getMessage());
            }
        }, "minestom-npc-diorama-shutdown"));

        log("Starting standalone NPC showcase on localhost:" + PORT + ".");
        minecraftServer.start(HOST, PORT);
    }

    private static void loadShowcaseChunks(InstanceContainer instance) {
        for (int chunkX = -2; chunkX <= 2; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 2; chunkZ++) {
                instance.loadChunk(chunkX, chunkZ).join();
            }
        }
    }

    private static void log(String message) {
        System.out.println("[minestom-entity-example] " + message);
    }
}
