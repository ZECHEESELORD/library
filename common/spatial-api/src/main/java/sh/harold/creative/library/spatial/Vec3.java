package sh.harold.creative.library.spatial;

public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);
    public static final Vec3 UNIT_X = new Vec3(1.0, 0.0, 0.0);
    public static final Vec3 UNIT_Y = new Vec3(0.0, 1.0, 0.0);
    public static final Vec3 UNIT_Z = new Vec3(0.0, 0.0, 1.0);

    public Vec3 {
        validateFinite(x, "x");
        validateFinite(y, "y");
        validateFinite(z, "z");
    }

    public Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 subtract(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    public Vec3 multiply(double scalar) {
        validateFinite(scalar, "scalar");
        return new Vec3(x * scalar, y * scalar, z * scalar);
    }

    public Vec3 divide(double scalar) {
        validateFinite(scalar, "scalar");
        if (scalar == 0.0) {
            throw new IllegalArgumentException("scalar cannot be zero");
        }
        return new Vec3(x / scalar, y / scalar, z / scalar);
    }

    public double dot(Vec3 other) {
        return (x * other.x) + (y * other.y) + (z * other.z);
    }

    public Vec3 cross(Vec3 other) {
        return new Vec3(
                (y * other.z) - (z * other.y),
                (z * other.x) - (x * other.z),
                (x * other.y) - (y * other.x)
        );
    }

    public double lengthSquared() {
        return dot(this);
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public Vec3 normalize() {
        double length = length();
        if (length == 0.0) {
            return ZERO;
        }
        return divide(length);
    }

    public boolean isZero(double epsilon) {
        return lengthSquared() <= epsilon * epsilon;
    }

    public double distanceSquared(Vec3 other) {
        return subtract(other).lengthSquared();
    }

    public double distance(Vec3 other) {
        return Math.sqrt(distanceSquared(other));
    }

    public double horizontalLength() {
        return Math.sqrt((x * x) + (z * z));
    }

    public Vec3 withX(double x) {
        return new Vec3(x, y, z);
    }

    public Vec3 withY(double y) {
        return new Vec3(x, y, z);
    }

    public Vec3 withZ(double z) {
        return new Vec3(x, y, z);
    }

    private static void validateFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
