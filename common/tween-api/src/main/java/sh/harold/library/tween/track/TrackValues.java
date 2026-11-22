package sh.harold.library.tween.track;

import sh.harold.library.spatial.Angle;
import sh.harold.library.spatial.Quaternion;
import sh.harold.library.spatial.Vec3;

/**
 * Stock {@link TrackValue}s: scalars, unwound angles, vectors and rotation quaternions.
 */
public final class TrackValues {

    /** Plain doubles. */
    public static final TrackValue<Double> SCALAR = new TrackValue<>() {
        @Override
        public Double lerp(Double from, Double to, double u) {
            return from + ((to - from) * u);
        }

        @Override
        public Double add(Double base, Double delta) {
            return base + delta;
        }

        @Override
        public Double scale(Double delta, double factor) {
            return delta * factor;
        }

        @Override
        public Double difference(Double from, Double to) {
            return to - from;
        }

        @Override
        public Double identity() {
            return 0.0;
        }
    };

    /**
     * Unwound angles (catalog R7): interpolation is linear in radians and never wraps, so a key at 0 degrees and
     * a key at 720 degrees animate two full turns. Wrap explicitly with {@link Angle#shortestDeltaTo(Angle)} when
     * shortest-path motion is wanted.
     */
    public static final TrackValue<Angle> UNWOUND_ANGLE = new TrackValue<>() {
        @Override
        public Angle lerp(Angle from, Angle to, double u) {
            return Angle.radians(from.radians() + ((to.radians() - from.radians()) * u));
        }

        @Override
        public Angle add(Angle base, Angle delta) {
            return base.add(delta);
        }

        @Override
        public Angle scale(Angle delta, double factor) {
            return delta.multiply(factor);
        }

        @Override
        public Angle difference(Angle from, Angle to) {
            return to.subtract(from);
        }

        @Override
        public Angle identity() {
            return Angle.radians(0.0);
        }
    };

    public static final TrackValue<Vec3> VEC3 = new TrackValue<>() {
        @Override
        public Vec3 lerp(Vec3 from, Vec3 to, double u) {
            return from.add(to.subtract(from).multiply(u));
        }

        @Override
        public Vec3 add(Vec3 base, Vec3 delta) {
            return base.add(delta);
        }

        @Override
        public Vec3 scale(Vec3 delta, double factor) {
            return delta.multiply(factor);
        }

        @Override
        public Vec3 difference(Vec3 from, Vec3 to) {
            return to.subtract(from);
        }

        @Override
        public Vec3 identity() {
            return Vec3.ZERO;
        }
    };

    /**
     * Rotation quaternions. {@code lerp} is slerp along the short arc, deltas are local rotations
     * ({@code add(a, d) = a * d}), scaling is a power along the short arc, and Hermite segments interpolate in
     * the log space of the segment start, so eased keys give slerp and Hermite keys give a smooth path through
     * the keys. Euler angles are never lerped.
     */
    public static final TrackValue<Quaternion> QUATERNION = new TrackValue<>() {
        @Override
        public Quaternion lerp(Quaternion from, Quaternion to, double u) {
            return from.slerp(to, u);
        }

        @Override
        public Quaternion add(Quaternion base, Quaternion delta) {
            return base.multiply(delta).normalize();
        }

        @Override
        public Quaternion scale(Quaternion delta, double factor) {
            return delta.pow(factor);
        }

        @Override
        public Quaternion difference(Quaternion from, Quaternion to) {
            Quaternion target = from.dot(to) < 0.0 ? to.negate() : to;
            return from.conjugate().multiply(target).normalize();
        }

        @Override
        public Quaternion identity() {
            return Quaternion.IDENTITY;
        }

        @Override
        public Quaternion hermite(Quaternion p0, Quaternion m0, Quaternion p1, Quaternion m1, double h, double u) {
            double u2 = u * u;
            double u3 = u2 * u;
            double h10 = u3 - (2.0 * u2) + u;
            double h01 = (-2.0 * u3) + (3.0 * u2);
            double h11 = u3 - u2;
            Vec3 span = difference(p0, p1).log();
            Vec3 offset = span.multiply(h01)
                    .add(m0.log().multiply(h10 * h))
                    .add(m1.log().multiply(h11 * h));
            return p0.multiply(Quaternion.exp(offset)).normalize();
        }
    };

    private TrackValues() {
    }
}
