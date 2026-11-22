package sh.harold.library.net.fabric.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import sh.harold.library.net.fabric.NetFeature;

import java.util.ArrayList;
import java.util.List;

/**
 * A closable group of client play receivers for one network feature. Closing it unregisters every receiver it
 * registered, so a feature's client service can tear down cleanly. Fabric runs play handlers on the client
 * thread.
 */
public final class ClientReceivers implements AutoCloseable {

    private final NetFeature feature;
    private final List<Identifier> registered = new ArrayList<>();
    private boolean closed;

    private ClientReceivers(NetFeature feature) {
        this.feature = feature;
    }

    public static ClientReceivers forFeature(NetFeature feature) {
        return new ClientReceivers(feature);
    }

    public synchronized <T extends CustomPacketPayload> ClientReceivers onPlay(
            CustomPacketPayload.Type<T> type, ClientPlayNetworking.PlayPayloadHandler<T> handler) {
        if (closed) {
            throw new IllegalStateException("receivers for '" + feature.id() + "' are closed");
        }
        if (!type.id().getPath().startsWith(feature.id() + "/")) {
            throw new IllegalArgumentException(type.id() + " does not belong to feature '" + feature.id() + "'");
        }
        if (!ClientPlayNetworking.registerGlobalReceiver(type, handler)) {
            throw new IllegalStateException("a client receiver for " + type.id() + " is already registered");
        }
        registered.add(type.id());
        return this;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (Identifier id : registered) {
            ClientPlayNetworking.unregisterGlobalReceiver(id);
        }
        registered.clear();
    }
}
