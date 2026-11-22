package sh.harold.library.camera.rig.core;

import sh.harold.library.camera.rig.cue.CueCurve;
import sh.harold.library.tween.EasingFunction;
import sh.harold.library.tween.core.Spring;

/**
 * Evaluates {@link CueCurve}s. Every function here is pure in the cue's local time {@code tau}.
 */
public final class CueCurves {

    /** A spring kick has settled once its offset and velocity stay under this fraction of the kick. */
    private static final double SETTLE_FRACTION = 1.0e-4;
    private static final int MAX_SPRING_TICKS = 1200;

    private CueCurves() {
    }

    /** The curve's offset at {@code tau}; 0 before 0 and after {@link #end(CueCurve)}. */
    public static double sample(CueCurve curve, double tau) {
        if (!(tau >= 0.0)) {
            return 0.0;
        }
        return switch (curve) {
            case CueCurve.TrackCurve track -> tau > end(curve) ? 0.0 : track.track().sample(tau);
            case CueCurve.Pulse pulse -> pulse(pulse, tau);
            case CueCurve.SpringKick kick -> tau > end(curve) ? 0.0
                    : Spring.advance(kick.spring(), kick.displacement(), kick.velocityPerTick(), 0.0, tau)[0];
            case CueCurve.Sampled sampled -> tau > sampled.endTicks() ? 0.0 : sampled.sampler().sample(tau);
        };
    }

    /** The local time the curve ends at; infinite for a held curve. */
    public static double end(CueCurve curve) {
        return switch (curve) {
            case CueCurve.TrackCurve track -> Math.max(0.0, track.track().endTime());
            case CueCurve.Pulse pulse -> pulse.held() ? Double.POSITIVE_INFINITY
                    : pulse.attackTicks() + pulse.holdTicks() + pulse.releaseTicks();
            case CueCurve.SpringKick kick -> springSettle(kick);
            case CueCurve.Sampled sampled -> sampled.endTicks();
        };
    }

    /**
     * The offset at {@code tau} for a curve released at local time {@code releaseAt}: frozen at its value on the
     * release and faded to zero over {@code releaseTicks} along {@code easing}.
     */
    public static double sampleReleased(CueCurve curve, double tau, double releaseAt, double releaseTicks,
                                        EasingFunction easing) {
        if (tau < releaseAt) {
            return sample(curve, tau);
        }
        if (releaseTicks <= 0.0) {
            return 0.0;
        }
        double u = (tau - releaseAt) / releaseTicks;
        if (u >= 1.0) {
            return 0.0;
        }
        return sample(curve, releaseAt) * (1.0 - easing.apply(u));
    }

    /** The local end of a curve released at {@code releaseAt}. */
    public static double releasedEnd(CueCurve curve, double releaseAt, double releaseTicks) {
        double natural = end(curve);
        if (releaseAt >= natural) {
            return natural;
        }
        return Math.max(0.0, releaseAt) + releaseTicks;
    }

    private static double pulse(CueCurve.Pulse pulse, double tau) {
        double attack = pulse.attackTicks();
        if (tau < attack) {
            return pulse.amount() * pulse.attackEasing().apply(tau / attack);
        }
        double holdEnd = attack + pulse.holdTicks();
        if (tau <= holdEnd) {
            return pulse.amount();
        }
        double release = pulse.releaseTicks();
        if (release <= 0.0 || tau >= holdEnd + release) {
            return 0.0;
        }
        return pulse.amount() * (1.0 - pulse.releaseEasing().apply((tau - holdEnd) / release));
    }

    private static double springSettle(CueCurve.SpringKick kick) {
        double scale = Math.abs(kick.displacement()) + Math.abs(kick.velocityPerTick());
        if (scale == 0.0) {
            return 0.0;
        }
        double threshold = scale * SETTLE_FRACTION;
        double x = kick.displacement();
        double v = kick.velocityPerTick();
        int lastLoud = 0;
        for (int tick = 1; tick <= MAX_SPRING_TICKS; tick++) {
            double[] next = Spring.advance(kick.spring(), x, v, 0.0, 1.0);
            x = next[0];
            v = next[1];
            if (Math.abs(x) >= threshold || Math.abs(v) >= threshold) {
                lastLoud = tick;
            } else if (tick - lastLoud > 4) {
                break;
            }
        }
        return lastLoud + 1.0;
    }
}
