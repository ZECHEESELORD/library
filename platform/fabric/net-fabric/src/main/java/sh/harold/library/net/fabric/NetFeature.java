package sh.harold.library.net.fabric;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A declared network feature: its id, its protocol version, and the payloads it registers. Payload ids are
 * {@code creative_library:<feature>/<name>}. Register payload types during mod initialisation, on both sides
 * (the feature's {@code -fabric} module is loaded by both).
 *
 * <p>Closing the feature removes it from the handshake manifest and unregisters every server receiver it
 * registered. Fabric cannot unregister payload types, so a type stays registered for the JVM's life; declaring
 * the feature again reuses it, and the codec must not change. Idempotent and thread-safe.
 */
public final class NetFeature implements AutoCloseable {

    private static final Set<Identifier> REGISTERED = new HashSet<>();

    private final String id;
    private final int protocolVersion;
    private final List<Identifier> serverReceivers = new ArrayList<>();
    private boolean closed;

    NetFeature(String id, int protocolVersion) {
        this.id = id;
        this.protocolVersion = protocolVersion;
    }

    public String id() {
        return id;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public synchronized boolean active() {
        return !closed;
    }

    /** A server-to-client play payload. */
    public <T extends CustomPacketPayload> CustomPacketPayload.Type<T> clientboundPlay(
            String name, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        CustomPacketPayload.Type<T> type = LibraryNet.type(id, name);
        registerOnce("clientbound_play", type, () -> PayloadTypeRegistry.clientboundPlay().register(type, codec));
        return type;
    }

    /** A client-to-server play payload. */
    public <T extends CustomPacketPayload> CustomPacketPayload.Type<T> serverboundPlay(
            String name, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        CustomPacketPayload.Type<T> type = LibraryNet.type(id, name);
        registerOnce("serverbound_play", type, () -> PayloadTypeRegistry.serverboundPlay().register(type, codec));
        return type;
    }

    /** A server-to-client configuration-phase payload. */
    public <T extends CustomPacketPayload> CustomPacketPayload.Type<T> clientboundConfiguration(
            String name, StreamCodec<? super FriendlyByteBuf, T> codec) {
        CustomPacketPayload.Type<T> type = LibraryNet.type(id, name);
        registerOnce("clientbound_configuration", type,
                () -> PayloadTypeRegistry.clientboundConfiguration().register(type, codec));
        return type;
    }

    /** A client-to-server configuration-phase payload. */
    public <T extends CustomPacketPayload> CustomPacketPayload.Type<T> serverboundConfiguration(
            String name, StreamCodec<? super FriendlyByteBuf, T> codec) {
        CustomPacketPayload.Type<T> type = LibraryNet.type(id, name);
        registerOnce("serverbound_configuration", type,
                () -> PayloadTypeRegistry.serverboundConfiguration().register(type, codec));
        return type;
    }

    /**
     * Registers a global server receiver for a serverbound play payload of this feature. Closing the feature
     * unregisters it. Fabric runs play handlers on the server thread.
     */
    public synchronized <T extends CustomPacketPayload> void onServerPlay(
            CustomPacketPayload.Type<T> type, ServerPlayNetworking.PlayPayloadHandler<T> handler) {
        ensureOpen();
        requireOwn(type);
        if (!ServerPlayNetworking.registerGlobalReceiver(type, handler)) {
            throw new IllegalStateException("a server receiver for " + type.id() + " is already registered");
        }
        serverReceivers.add(type.id());
    }

    @Override
    public void close() {
        List<Identifier> receivers;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            receivers = List.copyOf(serverReceivers);
            serverReceivers.clear();
        }
        for (Identifier receiver : receivers) {
            ServerPlayNetworking.unregisterGlobalReceiver(receiver);
        }
        LibraryNet.remove(this);
    }

    private void registerOnce(String direction, CustomPacketPayload.Type<?> type, Runnable register) {
        ensureOpen();
        Identifier key = Identifier.fromNamespaceAndPath(type.id().getNamespace(), direction + "/" + type.id().getPath());
        synchronized (REGISTERED) {
            if (REGISTERED.add(key)) {
                register.run();
            }
        }
    }

    private void requireOwn(CustomPacketPayload.Type<?> type) {
        if (!type.id().getPath().startsWith(id + "/")) {
            throw new IllegalArgumentException(type.id() + " does not belong to feature '" + id + "'");
        }
    }

    private synchronized void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("network feature '" + id + "' is closed");
        }
    }
}
