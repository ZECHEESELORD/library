package sh.harold.creative.library.spatial.core;

import sh.harold.creative.library.spatial.Bounds3;
import sh.harold.creative.library.spatial.Frame3;
import sh.harold.creative.library.spatial.Vec3;
import sh.harold.creative.library.spatial.Volume;

import java.util.Objects;

public record OrientedBoxVolume(Frame3 frame, Vec3 halfExtents) implements Volume {

    public OrientedBoxVolume {
        frame = Objects.requireNonNull(frame, "frame");
        halfExtents = Objects.requireNonNull(halfExtents, "halfExtents");
        if (halfExtents.x() < 0.0 || halfExtents.y() < 0.0 || halfExtents.z() < 0.0) {
            throw new IllegalArgumentException("halfExtents cannot be negative");
        }
    }

    @Override
    public boolean contains(Vec3 point) {
        Vec3 local = frame.worldToLocalPoint(point);
        return Math.abs(local.x()) <= halfExtents.x()
                && Math.abs(local.y()) <= halfExtents.y()
                && Math.abs(local.z()) <= halfExtents.z();
    }

    @Override
    public Vec3 nearestPoint(Vec3 point) {
        Vec3 local = frame.worldToLocalPoint(point);
        Vec3 clamped = new Vec3(
                clamp(local.x(), -halfExtents.x(), halfExtents.x()),
                clamp(local.y(), -halfExtents.y(), halfExtents.y()),
                clamp(local.z(), -halfExtents.z(), halfExtents.z())
        );
        return frame.localToWorldPoint(clamped);
    }

    @Override
    public double distanceToBoundary(Vec3 point) {
        Vec3 local = frame.worldToLocalPoint(point);
        if (!contains(point)) {
            return point.distance(nearestPoint(point));
        }
        double dx = halfExtents.x() - Math.abs(local.x());
        double dy = halfExtents.y() - Math.abs(local.y());
        double dz = halfExtents.z() - Math.abs(local.z());
        return Math.min(dx, Math.min(dy, dz));
    }

    @Override
    public Bounds3 bounds() {
        Vec3[] corners = new Vec3[8];
        int index = 0;
        for (int xSign = -1; xSign <= 1; xSign += 2) {
            for (int ySign = -1; ySign <= 1; ySign += 2) {
                for (int zSign = -1; zSign <= 1; zSign += 2) {
                    corners[index++] = frame.localToWorldPoint(new Vec3(
                            halfExtents.x() * xSign,
                            halfExtents.y() * ySign,
                            halfExtents.z() * zSign
                    ));
                }
            }
        }
        Vec3 min = corners[0];
        Vec3 max = corners[0];
        for (int i = 1; i < corners.length; i++) {
            Vec3 corner = corners[i];
            min = new Vec3(
                    Math.min(min.x(), corner.x()),
                    Math.min(min.y(), corner.y()),
                    Math.min(min.z(), corner.z())
            );
            max = new Vec3(
                    Math.max(max.x(), corner.x()),
                    Math.max(max.y(), corner.y()),
                    Math.max(max.z(), corner.z())
            );
        }
        return new Bounds3(min, max);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
