package sh.harold.library.camera.rig.cue;

import sh.harold.library.tween.Easing;
import sh.harold.library.tween.EasingFunction;
import sh.harold.library.tween.spring.SpringSpec;
import sh.harold.library.tween.track.StatelessSampler;
import sh.harold.library.tween.track.Track;

import java.util.Objects;

/**
 * The shape of one channel's offset over a cue's local time {@code tau} (ticks since the cue started, fractional).
 * Every curve is a pure function of {@code tau}, so a cue gives the same value at any frame rate and on any seek
 * (catalog R2, R6). A curve contributes nothing before {@code tau = 0} or after its end. A finite curve should end at
 * its identity value (or a whole turn, for unwound pitch); a held curve ({@link Pulse} with infinite hold) lasts until
 * its cue is released or cancelled.
 *
 * <p>Releasing a cue freezes each curve at its value on the release time and fades it to zero: a {@link Pulse} over
 * its own release, any other curve over the cue's release.
 */
public sealed interface CueCurve permits CueCurve.TrackCurve, CueCurve.Pulse, CueCurve.SpringKick, CueCurve.Sampled {

    /** A keyed {@link Track}, sampled at {@code tau}. It ends at its last key. */
    record TrackCurve(Track<Double> track) implements CueCurve {
        public TrackCurve {
            Objects.requireNonNull(track, "track");
        }
    }

    /**
     * Attack, hold and release: rises from 0 to {@code amount} over {@code attackTicks} along {@code attackEasing},
     * holds for {@code holdTicks} (infinite holds until released), then falls back to 0 over {@code releaseTicks}
     * along {@code releaseEasing} (the easing gives the fraction released).
     */
    record Pulse(
            double amount,
            double attackTicks,
            EasingFunction attackEasing,
            double holdTicks,
            double releaseTicks,
            EasingFunction releaseEasing
    ) implements CueCurve {
        public Pulse {
            if (!Double.isFinite(amount)) {
                throw new IllegalArgumentException("amount must be finite");
            }
            requireDuration(attackTicks, "attackTicks");
            requireDuration(releaseTicks, "releaseTicks");
            if (!(holdTicks >= 0.0)) {
                throw new IllegalArgumentException("holdTicks must be non-negative (infinite holds until released)");
            }
            Objects.requireNonNull(attackEasing, "attackEasing");
            Objects.requireNonNull(releaseEasing, "releaseEasing");
        }

        public boolean held() {
            return holdTicks == Double.POSITIVE_INFINITY;
        }
    }

    /**
     * A spring kicked from rest at 0 toward 0: it starts displaced by {@code displacement} and moving at
     * {@code velocityPerTick}, and rings out along the exact spring solution. It ends once it has settled.
     */
    record SpringKick(SpringSpec spring, double displacement, double velocityPerTick) implements CueCurve {
        public SpringKick {
            Objects.requireNonNull(spring, "spring");
            if (!Double.isFinite(displacement) || !Double.isFinite(velocityPerTick)) {
                throw new IllegalArgumentException("displacement and velocity must be finite");
            }
            if (spring.dampingRatio() == 0.0) {
                throw new IllegalArgumentException("an undamped spring never settles");
            }
        }
    }

    /** Any stateless sampler of {@code tau}, ending at {@code endTicks} (infinite holds until released). */
    record Sampled(StatelessSampler<Double> sampler, double endTicks) implements CueCurve {
        public Sampled {
            Objects.requireNonNull(sampler, "sampler");
            if (!(endTicks >= 0.0)) {
                throw new IllegalArgumentException("endTicks must be non-negative");
            }
        }
    }

    static CueCurve track(Track<Double> track) {
        return new TrackCurve(track);
    }

    static CueCurve pulse(double amount, double attackTicks, EasingFunction attackEasing, double holdTicks,
                          double releaseTicks, EasingFunction releaseEasing) {
        return new Pulse(amount, attackTicks, attackEasing, holdTicks, releaseTicks, releaseEasing);
    }

    /** Eases in to {@code amount} and holds until the cue is released, then eases out over {@code releaseTicks}. */
    static CueCurve held(double amount, double attackTicks, EasingFunction attackEasing,
                         double releaseTicks, EasingFunction releaseEasing) {
        return new Pulse(amount, attackTicks, attackEasing, Double.POSITIVE_INFINITY, releaseTicks, releaseEasing);
    }

    /** {@code amount} from {@code tau = 0} for {@code ticks}, then nothing. */
    static CueCurve constant(double amount, double ticks) {
        return new Pulse(amount, 0.0, Easing.LINEAR, ticks, 0.0, Easing.LINEAR);
    }

    static CueCurve spring(SpringSpec spring, double displacement, double velocityPerTick) {
        return new SpringKick(spring, displacement, velocityPerTick);
    }

    static CueCurve sampled(StatelessSampler<Double> sampler, double endTicks) {
        return new Sampled(sampler, endTicks);
    }

    /** No offset at all: a Reduced substitute that drops a curve. */
    static CueCurve none() {
        return new Pulse(0.0, 0.0, Easing.LINEAR, 0.0, 0.0, Easing.LINEAR);
    }

    private static void requireDuration(double ticks, String name) {
        if (!Double.isFinite(ticks) || ticks < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
