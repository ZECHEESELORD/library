package sh.harold.library.net.fabric;

import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;
import sh.harold.library.net.protocol.ProtocolManifest;

import java.util.function.Consumer;

/**
 * The configuration task that sends the server's {@link HelloPayload} and holds the connection in the
 * configuration phase until the client answers with its own manifest.
 */
final class HandshakeTask implements ConfigurationTask {

    static final Type TYPE = new Type("creative_library:net/handshake");

    private final ProtocolManifest manifest;

    HandshakeTask(ProtocolManifest manifest) {
        this.manifest = manifest;
    }

    @Override
    public void start(Consumer<Packet<?>> sender) {
        sender.accept(ServerConfigurationNetworking.createClientboundPacket(new HelloPayload(manifest)));
    }

    @Override
    public Type type() {
        return TYPE;
    }
}
