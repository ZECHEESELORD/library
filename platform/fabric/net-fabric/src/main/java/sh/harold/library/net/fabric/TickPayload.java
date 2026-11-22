package sh.harold.library.net.fabric;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code creative_library:net/tick}: the server's tick counter, sent on join and every
 * {@link LibraryNet#TICK_INTERVAL} ticks so clients can keep a shared-tick offset.
 */
public record TickPayload(long serverTick) implements CustomPacketPayload {

    public static final Type<TickPayload> TYPE = LibraryNet.type(LibraryNet.FEATURE, "tick");

    public static final StreamCodec<FriendlyByteBuf, TickPayload> CODEC =
            CustomPacketPayload.codec(TickPayload::write, TickPayload::read);

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarLong(serverTick);
    }

    private static TickPayload read(FriendlyByteBuf buffer) {
        return new TickPayload(buffer.readVarLong());
    }

    @Override
    public Type<TickPayload> type() {
        return TYPE;
    }
}
