package sh.harold.library.net.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import sh.harold.library.net.fabric.HelloPayload;
import sh.harold.library.net.fabric.LibraryNet;
import sh.harold.library.net.fabric.NetServerBootstrap;
import sh.harold.library.net.fabric.TickPayload;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client entrypoint: answers the server's hello with this client's manifest (the server decides and
 * disconnects on a mismatch) and keeps {@link ClientServerClock} fed from {@code net/tick}.
 */
public final class NetFabricClientInit implements ClientModInitializer {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    @Override
    public void onInitializeClient() {
        install();
    }

    /** Idempotent; the entrypoint calls it. */
    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        NetServerBootstrap.install(); // payload types
        ClientConfigurationNetworking.registerGlobalReceiver(HelloPayload.TYPE,
                (payload, context) -> context.responseSender().sendPacket(new HelloPayload(LibraryNet.manifest())));
        ClientPlayNetworking.registerGlobalReceiver(TickPayload.TYPE,
                (payload, context) -> ClientServerClock.sample(payload.serverTick()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null) {
                ClientServerClock.tick();
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientServerClock::reset));
    }
}
