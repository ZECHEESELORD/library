package sh.harold.creative.library.spatial.core;

import sh.harold.creative.library.spatial.Bounds3;
import sh.harold.creative.library.spatial.Frame3;
import sh.harold.creative.library.spatial.Vec3;
import sh.harold.creative.library.spatial.Volume;

import java.util.Objects;

public record CylinderVolume(Frame3 frame, double radius, double halfHeight) implements Volume {

    public CylinderVolume {
        frame = Objects.requireNonNull(frame, "frame");
        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException("radius must be finite and non-negative");
        }
        if (!Double.isFinite(halfHeight) || halfHeight < 0.0) {
            throw new IllegalArgumentException("halfHeight must be finite and non-negative");
        }
    }

    @Override
    public boolean contains(Vec3 point) {
        Vec3 local = frame.worldToLocalPoint(point);
        double radial = Math.sqrt((local.x() * local.x()) + (local.z() * local.z()));
        return radial <= radius && Math.abs(local.y()) <= halfHeight;
    }

    @Override
    public Vec3 nearestPoint(Vec3 point) {
        Vec3 local = frame.worldToLocalPoint(point);
        double radial = Math.sqrt((local.x() * local.x()) + (local.z() * local.z()));
        double clampedY = clamp(local.y(), -halfHeight, halfHeight);
        Vec3 radialPoint;
        if (radial == 0.0) {
            radialPoint = new Vec3(radius, clampedY, 0.0);
        } else {
            double clampedRadial = Math.min(radial, radius);
            double scale = clampedRadial / radial;
            radialPoint = new Vec3(local.x() * scale, clampedY, local.z() * scale);
        }
        return frame.localToWorldPoint(radialPoint);
    }

    @Override
    public double distanceToBoundary(Vec3 point) {
        Vec3 local = frame.worldToLocalPoint(point);
        double radial = Math.sqrt((local.x() * local.x()) + (local.z() * local.z()));
        if (!contains(point)) {
            return point.distance(nearestPoint(point));
        }
        return Math.min(radius - radial, halfHeight - Math.abs(local.y()));
    }

    @Override
    public Bounds3 bounds() {
        return new OrientedBoxVolume(frame, new Vec3(radius, halfHeight, radius)).bounds();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
