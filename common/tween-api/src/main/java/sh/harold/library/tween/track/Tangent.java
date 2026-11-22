package sh.harold.library.tween.track;

import java.util.Objects;

/**
 * How a Hermite key's tangent is chosen, on one side of the key. Tangents are in value units per tick.
 */
public sealed interface Tangent<T> permits Tangent.Auto, Tangent.Flat, Tangent.Linear, Tangent.Explicit {

    /** Catmull-Rom: the slope through the neighbouring keys; one-sided at the ends of the track and at cuts. */
    static <T> Tangent<T> auto() {
        return new Auto<>();
    }

    /** Zero slope: the value eases in or out of the key. */
    static <T> Tangent<T> flat() {
        return new Flat<>();
    }

    /** The slope of the straight line to the neighbouring key on this side. */
    static <T> Tangent<T> linear() {
        return new Linear<>();
    }

    static <T> Tangent<T> explicit(T perTick) {
        return new Explicit<>(perTick);
    }

    record Auto<T>() implements Tangent<T> {
    }

    record Flat<T>() implements Tangent<T> {
    }

    record Linear<T>() implements Tangent<T> {
    }

    record Explicit<T>(T perTick) implements Tangent<T> {
        public Explicit {
            Objects.requireNonNull(perTick, "perTick");
        }
    }
}
