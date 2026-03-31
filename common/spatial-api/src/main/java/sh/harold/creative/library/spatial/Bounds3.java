package sh.harold.creative.library.spatial;

import java.util.Objects;

public record Bounds3(Vec3 min, Vec3 max) {

    public Bounds3 {
        min = Objects.requireNonNull(min, "min");
        max = Objects.requireNonNull(max, "max");
        if (max.x() < min.x() || max.y() < min.y() || max.z() < min.z()) {
            throw new IllegalArgumentException("max must be greater than or equal to min on every axis");
        }
    }

    public static Bounds3 point(Vec3 point) {
        return new Bounds3(point, point);
    }

    public boolean contains(Vec3 point) {
        return point.x() >= min.x() && point.x() <= max.x()
                && point.y() >= min.y() && point.y() <= max.y()
                && point.z() >= min.z() && point.z() <= max.z();
    }

    public Vec3 center() {
        return new Vec3(
                (min.x() + max.x()) * 0.5,
                (min.y() + max.y()) * 0.5,
                (min.z() + max.z()) * 0.5
        );
    }

    public Bounds3 expand(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("amount must be finite and non-negative");
        }
        Vec3 delta = new Vec3(amount, amount, amount);
        return new Bounds3(min.subtract(delta), max.add(delta));
    }

    public Bounds3 union(Bounds3 other) {
        return new Bounds3(
                new Vec3(
                        Math.min(min.x(), other.min.x()),
                        Math.min(min.y(), other.min.y()),
                        Math.min(min.z(), other.min.z())
                ),
                new Vec3(
                        Math.max(max.x(), other.max.x()),
                        Math.max(max.y(), other.max.y()),
                        Math.max(max.z(), other.max.z())
                )
        );
    }
}
