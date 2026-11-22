package sh.harold.library.movement;

/**
 * What a move does to one velocity axis before the host's {@code travel} runs.
 *
 * @param mode  the operation
 * @param value its operand, blocks per tick
 */
public record VelocityRule(Mode mode, double value) {

    public static final VelocityRule KEEP = new VelocityRule(Mode.KEEP, 0.0);

    public VelocityRule {
        if (mode == null) {
            throw new IllegalArgumentException("mode");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be finite");
        }
    }

    public static VelocityRule set(double value) {
        return new VelocityRule(Mode.SET, value);
    }

    public static VelocityRule add(double value) {
        return new VelocityRule(Mode.ADD, value);
    }

    /** Keeps the axis at or above {@code -limit}: a fall-speed cap. */
    public static VelocityRule capFall(double limit) {
        return new VelocityRule(Mode.MIN, -Math.abs(limit));
    }

    public double apply(double current) {
        return switch (mode) {
            case KEEP -> current;
            case SET -> value;
            case ADD -> current + value;
            case MIN -> Math.max(current, value);
        };
    }

    public enum Mode {
        KEEP,
        SET,
        ADD,
        /** Raise the axis to at least {@code value}. */
        MIN
    }
}
