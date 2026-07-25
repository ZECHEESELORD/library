package sh.harold.library.npc.behavior;

public record NpcCooldownRange(long minimumTicks, long maximumTicks) {
    public static final NpcCooldownRange NONE = new NpcCooldownRange(0L, 0L);

    public NpcCooldownRange {
        if (minimumTicks < 0L) {
            throw new IllegalArgumentException("minimumTicks cannot be negative");
        }
        if (maximumTicks < minimumTicks) {
            throw new IllegalArgumentException("maximumTicks cannot be smaller than minimumTicks");
        }
    }

    public static NpcCooldownRange ticks(long minimumTicks, long maximumTicks) {
        return new NpcCooldownRange(minimumTicks, maximumTicks);
    }

    public static NpcCooldownRange seconds(double minimumSeconds, double maximumSeconds) {
        if (!Double.isFinite(minimumSeconds) || !Double.isFinite(maximumSeconds)
                || minimumSeconds < 0.0 || maximumSeconds < minimumSeconds) {
            throw new IllegalArgumentException("seconds must be finite, non-negative, and ordered");
        }
        return new NpcCooldownRange(
                Math.round(minimumSeconds * 20.0),
                Math.round(maximumSeconds * 20.0)
        );
    }
}
