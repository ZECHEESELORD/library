package sh.harold.creative.library.spatial.core;

import sh.harold.creative.library.spatial.Bounds3;
import sh.harold.creative.library.spatial.Vec3;
import sh.harold.creative.library.spatial.Volume;

import java.util.Objects;

public record AabbVolume(Bounds3 bounds) implements Volume {

    public AabbVolume {
        bounds = Objects.requireNonNull(bounds, "bounds");
    }

    @Override
    public boolean contains(Vec3 point) {
        return bounds.contains(point);
    }

    @Override
    public Vec3 nearestPoint(Vec3 point) {
        return new Vec3(
                clamp(point.x(), bounds.min().x(), bounds.max().x()),
                clamp(point.y(), bounds.min().y(), bounds.max().y()),
                clamp(point.z(), bounds.min().z(), bounds.max().z())
        );
    }

    @Override
    public double distanceToBoundary(Vec3 point) {
        if (!contains(point)) {
            return point.distance(nearestPoint(point));
        }
        double dx = Math.min(point.x() - bounds.min().x(), bounds.max().x() - point.x());
        double dy = Math.min(point.y() - bounds.min().y(), bounds.max().y() - point.y());
        double dz = Math.min(point.z() - bounds.min().z(), bounds.max().z() - point.z());
        return Math.min(dx, Math.min(dy, dz));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
