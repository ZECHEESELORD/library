package sh.harold.library.devtools.fabric.client;

/**
 * Exact frame times for a capture at a fixed frame rate: frame {@code k} at {@code fps} frames per second shows game
 * time {@code k * tps / fps} ticks, split into whole ticks and a partial tick with integer arithmetic so no rounding
 * error builds up. Use it to give client gametest screenshots the same partial tick the capture clock would.
 */
public final class CaptureTimes {

    /** Vanilla's tick rate. */
    public static final int TICKS_PER_SECOND = 20;

    private CaptureTimes() {
    }

    /** Whole ticks elapsed at frame {@code frame}. */
    public static long wholeTicks(long frame, int fps) {
        return wholeTicks(frame, fps, TICKS_PER_SECOND);
    }

    public static long wholeTicks(long frame, int fps, int tps) {
        check(frame, fps, tps);
        return Math.floorDiv(Math.multiplyExact(frame, (long) tps), fps);
    }

    /** The partial tick at frame {@code frame}, in {@code [0, 1)}. */
    public static float partialTick(long frame, int fps) {
        return partialTick(frame, fps, TICKS_PER_SECOND);
    }

    public static float partialTick(long frame, int fps, int tps) {
        check(frame, fps, tps);
        return (float) Math.floorMod(Math.multiplyExact(frame, (long) tps), fps) / fps;
    }

    /** Ticks the game runs before drawing frame {@code frame}: 0 for frame 0. */
    public static int ticksBefore(long frame, int fps) {
        return ticksBefore(frame, fps, TICKS_PER_SECOND);
    }

    public static int ticksBefore(long frame, int fps, int tps) {
        if (frame == 0) {
            check(frame, fps, tps);
            return 0;
        }
        return Math.toIntExact(wholeTicks(frame, fps, tps) - wholeTicks(frame - 1, fps, tps));
    }

    /** Game time in ticks between two frames. */
    public static float deltaTicks(int fps) {
        return deltaTicks(fps, TICKS_PER_SECOND);
    }

    public static float deltaTicks(int fps, int tps) {
        check(0, fps, tps);
        return (float) tps / fps;
    }

    /** Game time of frame {@code frame} in ticks, as one number. */
    public static double time(long frame, int fps) {
        return wholeTicks(frame, fps) + (double) partialTick(frame, fps);
    }

    private static void check(long frame, int fps, int tps) {
        if (frame < 0) {
            throw new IllegalArgumentException("frame must be non-negative: " + frame);
        }
        if (fps <= 0 || tps <= 0) {
            throw new IllegalArgumentException("fps and tps must be positive: " + fps + ", " + tps);
        }
    }
}
