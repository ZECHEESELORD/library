package sh.harold.library.tween.track;

import sh.harold.library.tween.EasingFunction;

import java.util.Objects;

/**
 * One key on a {@link Track}. {@code time} is in ticks on the owning clock and may be fractional.
 *
 * <p>An ordinary key has one value ({@code left} equals {@code right}). A cut key has two: frames with
 * {@code t < time} approach {@code left} and frames with {@code t >= time} show {@code right}; no frame mixes
 * them (catalog R4). {@code out} shapes the segment from this key to the next.
 */
public record TrackKey<T>(
        double time,
        T left,
        T right,
        boolean cut,
        Interpolation out,
        Tangent<T> inTangent,
        Tangent<T> outTangent
) {

    public TrackKey {
        if (!Double.isFinite(time)) {
            throw new IllegalArgumentException("time must be finite");
        }
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(inTangent, "inTangent");
        Objects.requireNonNull(outTangent, "outTangent");
        if (!cut && !left.equals(right)) {
            throw new IllegalArgumentException("only cut keys may have different left and right values");
        }
    }

    public static <T> TrackKey<T> hermite(double time, T value) {
        return hermite(time, value, Tangent.auto(), Tangent.auto());
    }

    public static <T> TrackKey<T> hermite(double time, T value, Tangent<T> inTangent, Tangent<T> outTangent) {
        return new TrackKey<>(time, value, value, false, Interpolation.HERMITE, inTangent, outTangent);
    }

    public static <T> TrackKey<T> eased(double time, T value, EasingFunction easing) {
        return new TrackKey<>(time, value, value, false, Interpolation.eased(easing), Tangent.auto(), Tangent.auto());
    }

    public static <T> TrackKey<T> stepped(double time, T value) {
        return new TrackKey<>(time, value, value, false, Interpolation.STEPPED, Tangent.auto(), Tangent.auto());
    }

    /**
     * A two-sided cut. Auto tangents on each side are one-sided, so neither side's curve sees the other side's
     * value.
     */
    public static <T> TrackKey<T> cut(double time, T left, T right, Interpolation out) {
        return new TrackKey<>(time, left, right, true, out, Tangent.auto(), Tangent.auto());
    }

    public static <T> TrackKey<T> cut(double time, T left, T right, Interpolation out,
                                      Tangent<T> inTangent, Tangent<T> outTangent) {
        return new TrackKey<>(time, left, right, true, out, inTangent, outTangent);
    }
}
