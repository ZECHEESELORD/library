package sh.harold.library.tween.spring;

/**
 * A damped spring, given as frequency and damping ratio, or as the authoring pair overshoot and settle time
 * (catalog 1.2). Damping ratio 1 is critically damped (no overshoot), below 1 underdamped, above 1 overdamped.
 *
 * <p>Frequency is in hertz of real time; one tick is {@code 1 / 20} s.
 */
public record SpringSpec(double frequencyHz, double dampingRatio) {

    public static final double TICKS_PER_SECOND = 20.0;

    public SpringSpec {
        if (!Double.isFinite(frequencyHz) || frequencyHz <= 0.0) {
            throw new IllegalArgumentException("frequencyHz must be finite and positive");
        }
        if (!Double.isFinite(dampingRatio) || dampingRatio < 0.0) {
            throw new IllegalArgumentException("dampingRatio must be finite and non-negative");
        }
    }

    public static SpringSpec critical(double frequencyHz) {
        return new SpringSpec(frequencyHz, 1.0);
    }

    /**
     * The spring that overshoots by {@code overshootPercent} (0 for none) and settles to within 2% in about
     * {@code settleTicks}, using {@code overshoot = e^(-pi z / sqrt(1 - z^2))} and {@code settle = 4 / (z w)}.
     */
    public static SpringSpec fromOvershoot(double overshootPercent, double settleTicks) {
        if (!Double.isFinite(overshootPercent) || overshootPercent < 0.0 || overshootPercent >= 100.0) {
            throw new IllegalArgumentException("overshootPercent must be in [0, 100)");
        }
        if (!Double.isFinite(settleTicks) || settleTicks <= 0.0) {
            throw new IllegalArgumentException("settleTicks must be finite and positive");
        }
        double zeta;
        if (overshootPercent == 0.0) {
            zeta = 1.0;
        } else {
            double ln = Math.log(overshootPercent / 100.0);
            zeta = -ln / Math.sqrt((Math.PI * Math.PI) + (ln * ln));
        }
        double settleSeconds = settleTicks / TICKS_PER_SECOND;
        double omega = 4.0 / (zeta * settleSeconds);
        return new SpringSpec(omega / (2.0 * Math.PI), zeta);
    }

    /** Angular frequency in radians per second. */
    public double angularFrequency() {
        return 2.0 * Math.PI * frequencyHz;
    }

    /** Angular frequency in radians per tick, the unit stateful samplers step in. */
    public double angularFrequencyPerTick() {
        return angularFrequency() / TICKS_PER_SECOND;
    }

    /** Peak overshoot as a fraction of the step (0.16 for 16%); 0 when critically or over damped. */
    public double overshootFraction() {
        if (dampingRatio >= 1.0) {
            return 0.0;
        }
        return Math.exp(-Math.PI * dampingRatio / Math.sqrt(1.0 - (dampingRatio * dampingRatio)));
    }

    /** The catalog's settle estimate {@code 4 / (z w)} in ticks; infinite for an undamped spring. */
    public double settleTicks() {
        if (dampingRatio == 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return 4.0 / (dampingRatio * angularFrequencyPerTick());
    }
}
