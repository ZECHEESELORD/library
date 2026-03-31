package sh.harold.creative.library.spatial;

public record Angle(double radians) {

    private static final double TAU = Math.PI * 2.0;

    public Angle {
        if (!Double.isFinite(radians)) {
            throw new IllegalArgumentException("radians must be finite");
        }
    }

    public static Angle radians(double radians) {
        return new Angle(radians);
    }

    public static Angle degrees(double degrees) {
        return new Angle(Math.toRadians(degrees));
    }

    public double degrees() {
        return Math.toDegrees(radians);
    }

    public Angle normalized() {
        return new Angle(normalizeRadians(radians));
    }

    public Angle add(Angle other) {
        return new Angle(radians + other.radians);
    }

    public Angle subtract(Angle other) {
        return new Angle(radians - other.radians);
    }

    public Angle multiply(double scalar) {
        if (!Double.isFinite(scalar)) {
            throw new IllegalArgumentException("scalar must be finite");
        }
        return new Angle(radians * scalar);
    }

    public Angle shortestDeltaTo(Angle target) {
        return new Angle(normalizeRadians(target.radians - radians));
    }

    private static double normalizeRadians(double value) {
        double wrapped = value % TAU;
        if (wrapped <= -Math.PI) {
            return wrapped + TAU;
        }
        if (wrapped > Math.PI) {
            return wrapped - TAU;
        }
        return wrapped;
    }
}
