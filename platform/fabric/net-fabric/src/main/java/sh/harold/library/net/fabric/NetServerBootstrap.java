package sh.harold.library.net.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationPacketListenerImpl;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import sh.harold.library.net.protocol.HandshakeCheck;
import sh.harold.library.net.protocol.ProtocolManifest;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registers the library's own payloads and the Fabric events behind the handshake and tick sync. Runs once,
 * from the mod entrypoint; calling it again does nothing. With no feature declared the handshake is skipped, so
 * vanilla clients join as normal.
 */
public final class NetServerBootstrap {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    /** Connections waiting for a hello reply, with the manifest the server sent them. */
    private static final Map<ServerConfigurationPacketListenerImpl, ProtocolManifest> PENDING =
            Collections.synchronizedMap(new WeakHashMap<>());

    private NetServerBootstrap() {
    }

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        PayloadTypeRegistry.clientboundConfiguration().register(HelloPayload.TYPE, HelloPayload.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(HelloPayload.TYPE, HelloPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TickPayload.TYPE, TickPayload.CODEC);

        ServerConfigurationConnectionEvents.CONFIGURE.register(NetServerBootstrap::configure);
        ServerConfigurationNetworking.registerGlobalReceiver(HelloPayload.TYPE,
                (payload, context) -> context.server().execute(() -> answer(context.packetListener(), payload)));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendTick(handler.player, server));
        ServerTickEvents.END_SERVER_TICK.register(NetServerBootstrap::broadcastTick);
    }

    private static void configure(ServerConfigurationPacketListenerImpl handler, MinecraftServer server) {
        ProtocolManifest manifest = LibraryNet.manifest();
        if (manifest.features().isEmpty()) {
            return;
        }
        if (!ServerConfigurationNetworking.canSend(handler, HelloPayload.TYPE)) {
            handler.disconnect(Component.literal(HandshakeCheck.missingClientMessage(manifest)));
            return;
        }
        PENDING.put(handler, manifest);
        ((FabricServerConfigurationPacketListenerImpl) handler).addTask(new HandshakeTask(manifest));
    }

    private static void answer(ServerConfigurationPacketListenerImpl handler, HelloPayload reply) {
        ProtocolManifest sent = PENDING.remove(handler);
        if (sent == null) {
            return; // unsolicited or duplicate reply
        }
        HandshakeCheck.Verdict verdict = HandshakeCheck.compare(sent, reply.manifest());
        if (!verdict.accepted()) {
            handler.disconnect(Component.literal(verdict.message()));
            return;
        }
        ((FabricServerConfigurationPacketListenerImpl) handler).completeTask(HandshakeTask.TYPE);
    }

    private static void broadcastTick(MinecraftServer server) {
        if (server.getTickCount() % LibraryNet.TICK_INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTick(player, server);
        }
    }

    private static void sendTick(ServerPlayer player, MinecraftServer server) {
        if (ServerPlayNetworking.canSend(player, TickPayload.TYPE)) {
            ServerPlayNetworking.send(player, new TickPayload(server.getTickCount()));
        }
    }
}
