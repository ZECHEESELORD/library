package sh.harold.library.telemetry.core;

import sh.harold.library.telemetry.FrameTime;
import sh.harold.library.telemetry.TelemetryRegistry;
import sh.harold.library.telemetry.TelemetrySink;

import java.util.Objects;

/**
 * The headless sampler (architecture section 7.5): evaluates any cue, clip or core simulation with no client
 * and writes per-tick or per-frame rows in the same CSV format the game hosts write.
 *
 * <p>The context starts in its state at {@code startTick}. Before rows at time {@code t} are read, the stepper
 * has been called for every whole tick up to {@code floor(t)}, so a stateful sampler is stepped once per tick
 * and sampled at the frame's partial in between (catalog R3). Frame times are exact:
 * {@code startTick + k * 20 / fps} with the partial computed in integers.
 */
public final class HeadlessSampler {

    private static final long TICKS_PER_SECOND = 20L;

    private HeadlessSampler() {
    }

    /** One row per tick from {@code startTick} to {@code endTick} inclusive. Returns the row count. */
    public static <C> long sampleTicks(TelemetryRegistry<C> registry, C context, TickStepper stepper,
                                       long startTick, long endTick, TelemetrySink sink) {
        requireRange(startTick, endTick);
        try (TelemetryRecording<C> recording = TelemetryRecording.start(registry, sink)) {
            for (long tick = startTick; tick <= endTick; tick++) {
                if (tick > startTick) {
                    stepper.step(tick);
                }
                recording.record(context, FrameTime.ofTick(tick));
            }
            return recording.rows();
        }
    }

    /**
     * One row per rendered frame at {@code fps} from {@code startTick} up to and including {@code endTick}.
     * Returns the row count.
     */
    public static <C> long sampleFrames(TelemetryRegistry<C> registry, C context, TickStepper stepper,
                                        long startTick, long endTick, int fps, TelemetrySink sink) {
        requireRange(startTick, endTick);
        if (fps < 1) {
            throw new IllegalArgumentException("fps must be positive");
        }
        Objects.requireNonNull(stepper, "stepper");
        try (TelemetryRecording<C> recording = TelemetryRecording.start(registry, sink)) {
            long stepped = startTick;
            long span = endTick - startTick;
            for (long frame = 0; ; frame++) {
                long numerator = frame * TICKS_PER_SECOND;
                long whole = startTick + (numerator / fps);
                if (whole - startTick > span || (whole == endTick && numerator % fps != 0)) {
                    break;
                }
                while (stepped < whole) {
                    stepped++;
                    stepper.step(stepped);
                }
                double partial = (numerator % fps) / (double) fps;
                recording.record(context, new FrameTime(whole, partial));
            }
            return recording.rows();
        }
    }

    private static void requireRange(long startTick, long endTick) {
        if (endTick < startTick) {
            throw new IllegalArgumentException("endTick must not be before startTick");
        }
    }

    /** Advances the sampled context to the state at the end of {@code tick}. */
    @FunctionalInterface
    public interface TickStepper {

        void step(long tick);
    }
}
