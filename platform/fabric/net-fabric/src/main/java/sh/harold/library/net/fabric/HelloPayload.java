package sh.harold.library.net.fabric;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import sh.harold.library.net.protocol.ProtocolManifest;

import java.util.Map;
import java.util.TreeMap;

/**
 * {@code creative_library:net/hello}, sent in the configuration phase: the server sends its manifest, and the
 * client answers with its own on the same id.
 */
public record HelloPayload(ProtocolManifest manifest) implements CustomPacketPayload {

    public static final Type<HelloPayload> TYPE = LibraryNet.type(LibraryNet.FEATURE, "hello");

    private static final int MAX_FEATURES = 256;

    public static final StreamCodec<FriendlyByteBuf, HelloPayload> CODEC =
            CustomPacketPayload.codec(HelloPayload::write, HelloPayload::read);

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(manifest.libraryVersion(), 64);
        Map<String, Integer> features = manifest.sortedFeatures();
        buffer.writeVarInt(features.size());
        for (Map.Entry<String, Integer> feature : features.entrySet()) {
            buffer.writeUtf(feature.getKey(), 64);
            buffer.writeVarInt(feature.getValue());
        }
    }

    private static HelloPayload read(FriendlyByteBuf buffer) {
        String libraryVersion = buffer.readUtf(64);
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_FEATURES) {
            throw new IllegalArgumentException("bad feature count " + count);
        }
        Map<String, Integer> features = new TreeMap<>();
        for (int i = 0; i < count; i++) {
            features.put(buffer.readUtf(64), buffer.readVarInt());
        }
        return new HelloPayload(new ProtocolManifest(libraryVersion, features));
    }

    @Override
    public Type<HelloPayload> type() {
        return TYPE;
    }
}
