package sh.harold.creative.library.telegraph;

public record TelegraphTiming(long fadeInTicks, long holdTicks, long fadeOutTicks, double baseAlpha, double baseThickness) {

    public TelegraphTiming {
        if (fadeInTicks < 0L || holdTicks < 0L || fadeOutTicks < 0L) {
            throw new IllegalArgumentException("telegraph timing ticks cannot be negative");
        }
        if (fadeInTicks + holdTicks + fadeOutTicks <= 0L) {
            throw new IllegalArgumentException("telegraph must last at least one tick");
        }
        if (!Double.isFinite(baseAlpha) || baseAlpha < 0.0) {
            throw new IllegalArgumentException("baseAlpha must be finite and non-negative");
        }
        if (!Double.isFinite(baseThickness) || baseThickness < 0.0) {
            throw new IllegalArgumentException("baseThickness must be finite and non-negative");
        }
    }

    public long totalDurationTicks() {
        return fadeInTicks + holdTicks + fadeOutTicks;
    }
}
