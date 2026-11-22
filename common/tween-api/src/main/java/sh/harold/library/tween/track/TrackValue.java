package sh.harold.library.tween.track;

/**
 * The arithmetic a {@link Track} needs for one value type. Deltas and tangents are values of the same type:
 * a delta is what {@link #difference(Object, Object)} returns, and a tangent is a delta per tick.
 *
 * <p>Laws: {@code add(a, difference(a, b))} equals {@code b}; {@code scale(d, 0)} is {@link #identity()};
 * {@code add(a, identity())} equals {@code a}.
 */
public interface TrackValue<T> {

    T lerp(T from, T to, double u);

    T add(T base, T delta);

    T scale(T delta, double factor);

    T difference(T from, T to);

    T identity();

    /**
     * Cubic Hermite interpolation on one segment of length {@code h} ticks, with tangents {@code m0} and
     * {@code m1} in units per tick. The default works for any vector space; rotation types override it.
     */
    default T hermite(T p0, T m0, T p1, T m1, double h, double u) {
        double u2 = u * u;
        double u3 = u2 * u;
        double h10 = u3 - (2.0 * u2) + u;
        double h01 = (-2.0 * u3) + (3.0 * u2);
        double h11 = u3 - u2;
        T offset = add(add(scale(difference(p0, p1), h01), scale(m0, h10 * h)), scale(m1, h11 * h));
        return add(p0, offset);
    }
}
