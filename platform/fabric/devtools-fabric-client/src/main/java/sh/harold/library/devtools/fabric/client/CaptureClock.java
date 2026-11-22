package sh.harold.library.devtools.fabric.client;

import net.fabricmc.loader.api.FabricLoader;

/**
 * A deterministic capture clock for dev builds: while open, every rendered frame advances game time by exactly
 * {@code 20 / fps} ticks, whatever the wall clock does, so frame {@code k} always runs the same ticks and draws at the
 * same partial tick ({@link CaptureTimes}). Frame strips and frame telemetry then repeat run to run.
 *
 * <p>It replaces the client timer's game-time step ({@code DeltaTracker.Timer.advanceGameTime}); pause and tick
 * freezing keep their vanilla behaviour. Only one clock runs at a time. Available in a development environment, or
 * when the system property {@code creative_library.devtools} is {@code true}. Use from the client thread.
 */
public final class CaptureClock {

    /** The system property that enables the clock outside a development environment. */
    public static final String ENABLE_PROPERTY = "creative_library.devtools";

    private static volatile Session session;

    private CaptureClock() {
    }

    /** Whether the clock may run in this environment. */
    public static boolean available() {
        return Boolean.getBoolean(ENABLE_PROPERTY) || FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    /**
     * Starts the clock at {@code fps}; the next rendered frame is frame 0. Closing the handle returns the timer to the
     * wall clock.
     *
     * @throws IllegalStateException if a clock is already running or the environment does not allow it
     */
    public static synchronized Handle start(int fps) {
        if (fps <= 0) {
            throw new IllegalArgumentException("fps must be positive: " + fps);
        }
        if (!available()) {
            throw new IllegalStateException("the capture clock is a dev tool; set -D" + ENABLE_PROPERTY + "=true");
        }
        if (session != null) {
            throw new IllegalStateException("a capture clock is already running");
        }
        Session started = new Session(fps);
        session = started;
        return started;
    }

    /** The running clock, or {@code null}. */
    public static Handle current() {
        return session;
    }

    /** Internal, for the timer mixin: claims the next frame's step, or returns {@code null} when no clock runs. */
    public static Step nextFrame() {
        Session current = session;
        return current == null ? null : current.next();
    }

    /** One frame's step: ticks to run before drawing, the partial tick to draw at and the frame's game-time delta. */
    public record Step(long frame, int ticks, float partialTick, float deltaTicks) {
    }

    /** A running capture clock. */
    public interface Handle extends AutoCloseable {

        int fps();

        /** Frames stepped so far; the next frame drawn is this number. */
        long frames();

        boolean active();

        @Override
        void close();
    }

    private static final class Session implements Handle {

        private final int fps;
        private long frame;
        private volatile boolean active = true;

        private Session(int fps) {
            this.fps = fps;
        }

        private synchronized Step next() {
            long k = frame++;
            return new Step(k, CaptureTimes.ticksBefore(k, fps), CaptureTimes.partialTick(k, fps),
                    CaptureTimes.deltaTicks(fps));
        }

        @Override
        public int fps() {
            return fps;
        }

        @Override
        public synchronized long frames() {
            return frame;
        }

        @Override
        public boolean active() {
            return active;
        }

        @Override
        public void close() {
            synchronized (CaptureClock.class) {
                if (!active) {
                    return;
                }
                active = false;
                if (session == this) {
                    session = null;
                }
            }
        }
    }
}
