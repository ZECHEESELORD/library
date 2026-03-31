package sh.harold.creative.library.spatial.core;

import sh.harold.creative.library.spatial.Bounds3;
import sh.harold.creative.library.spatial.Vec3;
import sh.harold.creative.library.spatial.Volume;

import java.util.Objects;

public record SphereVolume(Vec3 center, double radius) implements Volume {

    public SphereVolume {
        center = Objects.requireNonNull(center, "center");
        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException("radius must be finite and non-negative");
        }
    }

    @Override
    public boolean contains(Vec3 point) {
        return center.distanceSquared(point) <= radius * radius;
    }

    @Override
    public Vec3 nearestPoint(Vec3 point) {
        Vec3 offset = point.subtract(center);
        Vec3 direction = offset.normalize();
        if (direction.equals(Vec3.ZERO)) {
            return center.add(new Vec3(radius, 0.0, 0.0));
        }
        double distance = Math.min(radius, offset.length());
        return center.add(direction.multiply(distance));
    }

    @Override
    public double distanceToBoundary(Vec3 point) {
        return Math.abs(center.distance(point) - radius);
    }

    @Override
    public Bounds3 bounds() {
        Vec3 delta = new Vec3(radius, radius, radius);
        return new Bounds3(center.subtract(delta), center.add(delta));
    }
}
