package sh.harold.library.tween.track;

import sh.harold.library.tween.EasingFunction;

import java.util.Objects;

/**
 * The shape of the segment that follows a key.
 */
public sealed interface Interpolation permits Interpolation.Hermite, Interpolation.Eased, Interpolation.Stepped {

    Interpolation HERMITE = new Hermite();
    Interpolation STEPPED = new Stepped();

    static Interpolation eased(EasingFunction easing) {
        return new Eased(easing);
    }

    /** Cubic Hermite using the tangents of the key and the next key. */
    record Hermite() implements Interpolation {
    }

    /** {@code from + (to - from) * easing(u)}; slerp for rotations. */
    record Eased(EasingFunction easing) implements Interpolation {
        public Eased {
            Objects.requireNonNull(easing, "easing");
        }
    }

    /** Holds the key's value until the next key, for whole ticks whatever the frame rate. */
    record Stepped() implements Interpolation {
    }
}
