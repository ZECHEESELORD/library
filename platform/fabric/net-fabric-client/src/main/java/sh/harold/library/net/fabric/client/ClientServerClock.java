package sh.harold.library.net.fabric.client;

import sh.harold.library.net.protocol.TickOffsetFilter;

import java.util.OptionalLong;

/**
 * The client's estimate of the server tick. The client counts its own ticks while in a world, and each
 * {@code net/tick} packet adds a (server tick, client tick) sample; the offset is the median of the last five,
 * so one late packet does not move it. Empty until the first packet arrives (for example on a server without
 * the library). Reset on disconnect. Read it from the client thread.
 */
public final class ClientServerClock {

    private static final TickOffsetFilter FILTER = new TickOffsetFilter();
    private static long clientTick;

    private ClientServerClock() {
    }

    /** Client ticks counted since joining the current world. */
    public static long clientTick() {
        return clientTick;
    }

    /** Server tick minus client tick, or empty before the first sample. */
    public static OptionalLong offset() {
        return FILTER.offset();
    }

    /** The server tick matching the current client tick, or empty before the first sample. */
    public static OptionalLong estimatedServerTick() {
        return FILTER.toServerTick(clientTick);
    }

    /** The number of samples in the median window (at most {@link TickOffsetFilter#WINDOW}). */
    public static int sampleCount() {
        return FILTER.sampleCount();
    }

    static void tick() {
        clientTick++;
    }

    static void sample(long serverTick) {
        FILTER.addSample(serverTick, clientTick);
    }

    static void reset() {
        clientTick = 0;
        FILTER.reset();
    }
}
