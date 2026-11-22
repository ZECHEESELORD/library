package sh.harold.library.tween.core;

import sh.harold.library.tween.spring.SpringSpec;
import sh.harold.library.tween.spring.StatefulSampler;

import java.util.Objects;

/**
 * A scalar damped spring stepped once per tick (catalog 1.2, R3). Each step and each per-frame sample is the
 * exact closed-form solution of {@code x'' = -w^2 (x - g) - 2 z w x'} from the last tick's state, so stepping
 * and sampling agree at any frame rate. Velocity is in units per tick.
 *
 * <p>{@link #retarget(double)} keeps velocity (a second press redirects in flight), {@link #kickVelocity(double)}
 * and {@link #kickDisplacement(double)} are impulses applied on the current tick, and {@link #reset(Double)}
 * is a cut: the value jumps and the spring comes to rest.
 */
public final class Spring implements StatefulSampler<Double> {

    private static final double CRITICAL_EPSILON = 1.0e-9;

    private final SpringSpec spec;
    private double position;
    private double velocity;
    private double target;

    public Spring(SpringSpec spec, double initial) {
        this.spec = Objects.requireNonNull(spec, "spec");
        requireFinite(initial, "initial");
        this.position = initial;
        this.target = initial;
    }

    public SpringSpec spec() {
        return spec;
    }

    public double position() {
        return position;
    }

    public double velocity() {
        return velocity;
    }

    public double target() {
        return target;
    }

    public void retarget(double target) {
        requireFinite(target, "target");
        this.target = target;
    }

    public void kickVelocity(double unitsPerTick) {
        requireFinite(unitsPerTick, "unitsPerTick");
        velocity += unitsPerTick;
    }

    public void kickDisplacement(double delta) {
        requireFinite(delta, "delta");
        position += delta;
    }

    @Override
    public void tick() {
        double[] next = advance(spec, position, velocity, target, 1.0);
        position = next[0];
        velocity = next[1];
    }

    @Override
    public Double sample(double partial) {
        return sampleDouble(partial);
    }

    public double sampleDouble(double partial) {
        if (partial == 0.0) {
            return position;
        }
        return advance(spec, position, velocity, target, partial)[0];
    }

    @Override
    public void reset(Double value) {
        reset(value, 0.0);
    }

    /** Jumps to {@code value} with {@code velocityPerTick}, and makes {@code value} the target. */
    public void reset(double value, double velocityPerTick) {
        requireFinite(value, "value");
        requireFinite(velocityPerTick, "velocityPerTick");
        position = value;
        velocity = velocityPerTick;
        target = value;
    }

    /**
     * The exact state {@code dt} ticks after {@code (x, v)} toward a fixed target {@code g}:
     * {@code [position, velocity]}.
     */
    public static double[] advance(SpringSpec spec, double x, double v, double g, double dt) {
        double omega = spec.angularFrequencyPerTick();
        double zeta = spec.dampingRatio();
        double a = x - g;
        if (Math.abs(zeta - 1.0) < CRITICAL_EPSILON) {
            double b = v + (omega * a);
            double e = Math.exp(-omega * dt);
            double offset = (a + (b * dt)) * e;
            double vel = (b - (omega * (a + (b * dt)))) * e;
            return new double[] {g + offset, vel};
        }
        if (zeta < 1.0) {
            double omegaD = omega * Math.sqrt(1.0 - (zeta * zeta));
            double b = (v + (zeta * omega * a)) / omegaD;
            double e = Math.exp(-zeta * omega * dt);
            double cos = Math.cos(omegaD * dt);
            double sin = Math.sin(omegaD * dt);
            double offset = e * ((a * cos) + (b * sin));
            double vel = e * ((-zeta * omega * ((a * cos) + (b * sin))) + (omegaD * ((b * cos) - (a * sin))));
            return new double[] {g + offset, vel};
        }
        double root = Math.sqrt((zeta * zeta) - 1.0);
        double r1 = -omega * (zeta - root);
        double r2 = -omega * (zeta + root);
        double c2 = (v - (r1 * a)) / (r2 - r1);
        double c1 = a - c2;
        double e1 = Math.exp(r1 * dt);
        double e2 = Math.exp(r2 * dt);
        return new double[] {g + (c1 * e1) + (c2 * e2), (c1 * r1 * e1) + (c2 * r2 * e2)};
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
