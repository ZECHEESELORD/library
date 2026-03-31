package sh.harold.creative.library.impulse;

import sh.harold.creative.library.spatial.Vec3;

public record AxisMask(double x, double y, double z) {

    public static final AxisMask ALL = new AxisMask(1.0, 1.0, 1.0);
    public static final AxisMask HORIZONTAL = new AxisMask(1.0, 0.0, 1.0);
    public static final AxisMask VERTICAL = new AxisMask(0.0, 1.0, 0.0);

    public AxisMask {
        validate(x, "x");
        validate(y, "y");
        validate(z, "z");
    }

    public Vec3 apply(Vec3 vector) {
        return new Vec3(vector.x() * x, vector.y() * y, vector.z() * z);
    }

    private static void validate(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
