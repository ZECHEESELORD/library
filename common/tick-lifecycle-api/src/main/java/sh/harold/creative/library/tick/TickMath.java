package sh.harold.creative.library.tick;

public final class TickMath {

    private TickMath() {
    }

    public static long localTick(long currentTick, long startTick) {
        return Math.max(0L, currentTick - startTick);
    }

    public static long activeTick(long currentTick, long startTick, long delayTicks) {
        if (delayTicks < 0L) {
            throw new IllegalArgumentException("delayTicks cannot be negative");
        }
        return Math.max(0L, localTick(currentTick, startTick) - delayTicks);
    }

    public static double progress(long activeTick, long durationTicks) {
        if (activeTick < 0L) {
            throw new IllegalArgumentException("activeTick cannot be negative");
        }
        if (durationTicks < 0L) {
            throw new IllegalArgumentException("durationTicks cannot be negative");
        }
        if (durationTicks == 0L) {
            return 1.0;
        }
        return clamp01(activeTick / (double) durationTicks);
    }

    public static double clamp01(double value) {
        if (value <= 0.0) {
            return 0.0;
        }
        if (value >= 1.0) {
            return 1.0;
        }
        return value;
    }
}
