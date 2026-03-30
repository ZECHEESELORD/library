package sh.harold.creative.library.camera;

import java.util.Objects;

public sealed interface Envelope permits Envelope.AttackHoldRelease, Envelope.Constant, Envelope.EaseOut, Envelope.ExponentialDecay,
        Envelope.ExponentialHalfLife, Envelope.LinearDecay {

    long durationTicks();

    record Constant(long durationTicks, double strength) implements Envelope {

        public Constant {
            validateDuration(durationTicks, "durationTicks");
            validateStrength(strength, "strength");
        }
    }

    record LinearDecay(long durationTicks, double startStrength, double endStrength) implements Envelope {

        public LinearDecay {
            validateDuration(durationTicks, "durationTicks");
            validateStrength(startStrength, "startStrength");
            validateStrength(endStrength, "endStrength");
        }
    }

    record ExponentialDecay(long durationTicks, double startStrength, double lambda) implements Envelope {

        public ExponentialDecay {
            validateDuration(durationTicks, "durationTicks");
            validateStrength(startStrength, "startStrength");
            if (!Double.isFinite(lambda) || lambda < 0.0) {
                throw new IllegalArgumentException("lambda must be finite and non-negative");
            }
        }
    }

    record ExponentialHalfLife(long durationTicks, double startStrength, long halfLifeTicks) implements Envelope {

        public ExponentialHalfLife {
            validateDuration(durationTicks, "durationTicks");
            validateStrength(startStrength, "startStrength");
            if (halfLifeTicks <= 0L) {
                throw new IllegalArgumentException("halfLifeTicks must be positive");
            }
        }
    }

    record AttackHoldRelease(long attackTicks, long holdTicks, long releaseTicks, double peakStrength) implements Envelope {

        public AttackHoldRelease {
            validateNonNegative(attackTicks, "attackTicks");
            validateNonNegative(holdTicks, "holdTicks");
            validateNonNegative(releaseTicks, "releaseTicks");
            validateStrength(peakStrength, "peakStrength");
            if (Math.addExact(Math.addExact(attackTicks, holdTicks), releaseTicks) <= 0L) {
                throw new IllegalArgumentException("attack, hold, and release cannot all be zero");
            }
        }

        @Override
        public long durationTicks() {
            return Math.addExact(Math.addExact(attackTicks, holdTicks), releaseTicks);
        }
    }

    record EaseOut(long durationTicks, double startStrength, EaseOutCurve curve) implements Envelope {

        public EaseOut {
            validateDuration(durationTicks, "durationTicks");
            validateStrength(startStrength, "startStrength");
            Objects.requireNonNull(curve, "curve");
        }
    }

    private static void validateDuration(long durationTicks, String name) {
        if (durationTicks <= 0L) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void validateNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
    }

    private static void validateStrength(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }
}
