package sh.harold.library.spatial;

/**
 * A rotation quaternion {@code w + xi + yj + zk}. Factory methods return unit quaternions; the record
 * constructor accepts any finite components so intermediate math stays cheap. Rotations compose right to left:
 * {@code a.multiply(b).rotate(v) == a.rotate(b.rotate(v))}.
 */
public record Quaternion(double w, double x, double y, double z) {

    public static final Quaternion IDENTITY = new Quaternion(1.0, 0.0, 0.0, 0.0);

    private static final double EPSILON = 1.0e-12;

    public Quaternion {
        if (!Double.isFinite(w) || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("quaternion components must be finite");
        }
    }

    /** A rotation of {@code radians} about {@code axis} (right-handed). */
    public static Quaternion axisAngle(Vec3 axis, double radians) {
        if (!Double.isFinite(radians)) {
            throw new IllegalArgumentException("radians must be finite");
        }
        Vec3 unit = axis.normalize();
        if (unit.isZero(EPSILON)) {
            throw new IllegalArgumentException("axis cannot be zero");
        }
        double half = radians * 0.5;
        double s = Math.sin(half);
        return new Quaternion(Math.cos(half), unit.x() * s, unit.y() * s, unit.z() * s);
    }

    /** The exponential map: a rotation by {@code |v|} radians about {@code v}. Inverse of {@link #log()}. */
    public static Quaternion exp(Vec3 rotationVector) {
        double angle = rotationVector.length();
        if (angle < EPSILON) {
            return new Quaternion(1.0, rotationVector.x() * 0.5, rotationVector.y() * 0.5, rotationVector.z() * 0.5)
                    .normalize();
        }
        return axisAngle(rotationVector, angle);
    }

    public Quaternion multiply(Quaternion o) {
        return new Quaternion(
                (w * o.w) - (x * o.x) - (y * o.y) - (z * o.z),
                (w * o.x) + (x * o.w) + (y * o.z) - (z * o.y),
                (w * o.y) - (x * o.z) + (y * o.w) + (z * o.x),
                (w * o.z) + (x * o.y) - (y * o.x) + (z * o.w)
        );
    }

    public Quaternion conjugate() {
        return new Quaternion(w, -x, -y, -z);
    }

    public Quaternion inverse() {
        double n = lengthSquared();
        if (n < EPSILON) {
            throw new IllegalStateException("cannot invert a zero quaternion");
        }
        return new Quaternion(w / n, -x / n, -y / n, -z / n);
    }

    public double dot(Quaternion o) {
        return (w * o.w) + (x * o.x) + (y * o.y) + (z * o.z);
    }

    public double lengthSquared() {
        return dot(this);
    }

    public Quaternion normalize() {
        double n = Math.sqrt(lengthSquared());
        if (n < EPSILON) {
            return IDENTITY;
        }
        return new Quaternion(w / n, x / n, y / n, z / n);
    }

    public Quaternion negate() {
        return new Quaternion(-w, -x, -y, -z);
    }

    /**
     * The rotation vector (axis times angle, radians) of this unit quaternion, taking the short way round
     * ({@code |log| <= pi}).
     */
    public Vec3 log() {
        Quaternion q = w < 0.0 ? negate() : this;
        double vectorLength = Math.sqrt((q.x * q.x) + (q.y * q.y) + (q.z * q.z));
        if (vectorLength < EPSILON) {
            return new Vec3(q.x * 2.0, q.y * 2.0, q.z * 2.0);
        }
        double angle = 2.0 * Math.atan2(vectorLength, q.w);
        double scale = angle / vectorLength;
        return new Vec3(q.x * scale, q.y * scale, q.z * scale);
    }

    /** This rotation scaled by {@code exponent} along the short arc: {@code pow(0)} is identity, {@code pow(1)} is this. */
    public Quaternion pow(double exponent) {
        return exp(log().multiply(exponent));
    }

    /** Spherical linear interpolation along the short arc. {@code u} may leave {@code [0, 1]} to extrapolate. */
    public Quaternion slerp(Quaternion to, double u) {
        Quaternion target = dot(to) < 0.0 ? to.negate() : to;
        Quaternion delta = conjugate().multiply(target).normalize();
        return multiply(delta.pow(u)).normalize();
    }

    public Vec3 rotate(Vec3 v) {
        Quaternion p = new Quaternion(0.0, v.x(), v.y(), v.z());
        Quaternion r = multiply(p).multiply(conjugate());
        return new Vec3(r.x, r.y, r.z);
    }

    /** The smallest angle in radians between this rotation and {@code other}. */
    public double angleTo(Quaternion other) {
        double d = Math.min(1.0, Math.abs(normalize().dot(other.normalize())));
        return 2.0 * Math.acos(d);
    }
}
