package sh.harold.creative.library.tween;

import java.util.Objects;

public sealed interface Envelope permits Envelope.AttackHoldRelease, Envelope.Constant, Envelope.EaseOut,
        Envelope.ExponentialDecay, Envelope.ExponentialHalfLife, Envelope.LinearDecay {

    double sample(long activeTick, long durationTicks);

    static Envelope constant(double strength) {
        return new Constant(strength);
    }

    record Constant(double strength) implements Envelope {

        public Constant {
            validateNonNegative(strength, "strength");
        }

        @Override
        public double sample(long activeTick, long durationTicks) {
            return strength;
        }
    }

    record LinearDecay(double startStrength, double endStrength) implements Envelope {

        public LinearDecay {
            validateNonNegative(startStrength, "startStrength");
            validateNonNegative(endStrength, "endStrength");
        }

        @Override
        public double sample(long activeTick, long durationTicks) {
            double u = durationTicks <= 0L ? 1.0 : Math.max(0.0, Math.min(1.0, activeTick / (double) durationTicks));
            return startStrength + ((endStrength - startStrength) * u);
        }
    }

    record ExponentialDecay(double startStrength, double lambda) implements Envelope {

        public ExponentialDecay {
            validateNonNegative(startStrength, "startStrength");
            if (!Double.isFinite(lambda) || lambda < 0.0) {
                throw new IllegalArgumentException("lambda must be finite and non-negative");
            }
        }

        @Override
        public double sample(long activeTick, long durationTicks) {
            return startStrength * Math.exp(-lambda * Math.max(0L, activeTick));
        }
    }

    record ExponentialHalfLife(double startStrength, long halfLifeTicks) implements Envelope {

        public ExponentialHalfLife {
            validateNonNegative(startStrength, "startStrength");
            if (halfLifeTicks <= 0L) {
                throw new IllegalArgumentException("halfLifeTicks must be positive");
            }
        }

        @Override
        public double sample(long activeTick, long durationTicks) {
            return startStrength * Math.exp(-(Math.log(2.0) / halfLifeTicks) * Math.max(0L, activeTick));
        }
    }

    record AttackHoldRelease(long attackTicks, long holdTicks, long releaseTicks, double peakStrength) implements Envelope {

        public AttackHoldRelease {
            validateNonNegative(attackTicks, "attackTicks");
            validateNonNegative(holdTicks, "holdTicks");
            validateNonNegative(releaseTicks, "releaseTicks");
            validateNonNegative(peakStrength, "peakStrength");
            if (attackTicks + holdTicks + releaseTicks <= 0L) {
                throw new IllegalArgumentException("attack, hold, and release cannot all be zero");
            }
        }

        @Override
        public double sample(long activeTick, long durationTicks) {
            long tick = Math.max(0L, activeTick);
            if (attackTicks > 0L && tick < attackTicks) {
                return peakStrength * ((tick + 1.0) / attackTicks);
            }
            tick -= attackTicks;
            if (tick < holdTicks) {
                return peakStrength;
            }
            tick -= holdTicks;
            if (releaseTicks > 0L && tick < releaseTicks) {
                return peakStrength * (1.0 - (tick / (double) releaseTicks));
            }
            return 0.0;
        }
    }

    record EaseOut(double startStrength, Easing easing) implements Envelope {

        public EaseOut {
            validateNonNegative(startStrength, "startStrength");
            easing = Objects.requireNonNull(easing, "easing");
        }

        @Override
        public double sample(long activeTick, long durationTicks) {
            if (durationTicks <= 0L) {
                return 0.0;
            }
            double remaining = 1.0 - Math.max(0.0, Math.min(1.0, activeTick / (double) durationTicks));
            return easing.apply(remaining) * startStrength;
        }
    }

    private static void validateNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
