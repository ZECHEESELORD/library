package sh.harold.library.net.protocol;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.OptionalLong;

/**
 * The client's shared-tick estimate (architecture section 5, "Shared tick"): each {@code net/tick} sample gives
 * {@code serverTick - clientTick} at receipt, and the offset is the median of the last 5 samples, which rejects a
 * single late or early packet. With an even number of samples (while warming up) it takes the lower median.
 * Not thread-safe; use it from the client thread.
 */
public final class TickOffsetFilter {

    public static final int WINDOW = 5;

    private final Deque<Long> samples = new ArrayDeque<>(WINDOW);

    public void addSample(long serverTick, long clientTick) {
        if (samples.size() == WINDOW) {
            samples.removeFirst();
        }
        samples.addLast(serverTick - clientTick);
    }

    public int sampleCount() {
        return samples.size();
    }

    public OptionalLong offset() {
        if (samples.isEmpty()) {
            return OptionalLong.empty();
        }
        long[] sorted = samples.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(sorted);
        return OptionalLong.of(sorted[(sorted.length - 1) / 2]);
    }

    /** The server tick matching {@code clientTick}, once at least one sample has arrived. */
    public OptionalLong toServerTick(long clientTick) {
        OptionalLong offset = offset();
        return offset.isPresent() ? OptionalLong.of(clientTick + offset.getAsLong()) : OptionalLong.empty();
    }

    public void reset() {
        samples.clear();
    }
}
