package sh.harold.library.telemetry;

/**
 * When a telemetry row was taken: a whole tick plus the fraction of the next tick, in {@code [0, 1)}. Per-tick
 * rows have {@code partial == 0}; per-frame rows carry the frame's partial tick.
 */
public record FrameTime(long tick, double partial) {

    public FrameTime {
        if (!(partial >= 0.0 && partial < 1.0)) {
            throw new IllegalArgumentException("partial must be in [0, 1)");
        }
    }

    public static FrameTime ofTick(long tick) {
        return new FrameTime(tick, 0.0);
    }

    /** {@code tick + partial}. */
    public double time() {
        return tick + partial;
    }
}
