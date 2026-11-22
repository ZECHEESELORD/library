package sh.harold.library.prediction.core;

import java.util.Optional;

/**
 * A fixed-capacity ring of values keyed by consecutive ticks. Writing tick {@code t} keeps at most the last
 * {@code capacity} ticks up to {@code t}; writing a tick older than the newest discards everything newer
 * (history is rewritten from there). Not thread-safe.
 */
public final class HistoryRing<T> {

    public static final int DEFAULT_CAPACITY = 64;

    private final Object[] slots;
    private final long[] ticks;
    private long newestTick = Long.MIN_VALUE;
    private int size;

    public HistoryRing() {
        this(DEFAULT_CAPACITY);
    }

    public HistoryRing(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.slots = new Object[capacity];
        this.ticks = new long[capacity];
    }

    public int capacity() {
        return slots.length;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public long newestTick() {
        if (size == 0) {
            throw new IllegalStateException("history is empty");
        }
        return newestTick;
    }

    public long oldestTick() {
        if (size == 0) {
            throw new IllegalStateException("history is empty");
        }
        return newestTick - size + 1;
    }

    public boolean contains(long tick) {
        return size > 0 && tick <= newestTick && tick >= oldestTick();
    }

    /**
     * Stores {@code value} at {@code tick}. The tick must be at most one past the newest, or the ring must be
     * empty; a gap would leave ticks with no value.
     */
    public void put(long tick, T value) {
        if (size > 0) {
            if (tick > newestTick + 1) {
                throw new IllegalArgumentException("tick " + tick + " leaves a gap after " + newestTick);
            }
            if (tick < oldestTick()) {
                clear();
            } else if (tick <= newestTick) {
                size -= (int) (newestTick - tick + 1);
                newestTick = tick - 1;
            }
        }
        int slot = slot(tick);
        slots[slot] = value;
        ticks[slot] = tick;
        newestTick = tick;
        size = Math.min(size + 1, slots.length);
    }

    /** Overwrites the value at a stored {@code tick} in place, keeping every other tick. */
    public void set(long tick, T value) {
        if (!contains(tick)) {
            throw new IllegalArgumentException("tick " + tick + " is not stored");
        }
        slots[slot(tick)] = value;
    }

    @SuppressWarnings("unchecked")
    public Optional<T> get(long tick) {
        if (!contains(tick)) {
            return Optional.empty();
        }
        int slot = slot(tick);
        if (ticks[slot] != tick) {
            return Optional.empty();
        }
        return Optional.ofNullable((T) slots[slot]);
    }

    public void clear() {
        java.util.Arrays.fill(slots, null);
        size = 0;
        newestTick = Long.MIN_VALUE;
    }

    private int slot(long tick) {
        return (int) Math.floorMod(tick, (long) slots.length);
    }
}
