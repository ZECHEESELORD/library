package sh.harold.creative.library.spatial.core;

import sh.harold.creative.library.spatial.Bounds3;
import sh.harold.creative.library.spatial.Segment3;
import sh.harold.creative.library.spatial.Vec3;
import sh.harold.creative.library.spatial.Volume;

import java.util.Objects;

public record CapsuleVolume(Segment3 spine, double radius) implements Volume {

    public CapsuleVolume {
        spine = Objects.requireNonNull(spine, "spine");
        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException("radius must be finite and non-negative");
        }
    }

    @Override
    public boolean contains(Vec3 point) {
        return distanceToSegment(point) <= radius;
    }

    @Override
    public Vec3 nearestPoint(Vec3 point) {
        Vec3 closestCenter = nearestPointOnSegment(point);
        Vec3 offset = point.subtract(closestCenter);
        Vec3 direction = offset.normalize();
        if (direction.equals(Vec3.ZERO)) {
            return closestCenter.add(new Vec3(radius, 0.0, 0.0));
        }
        return closestCenter.add(direction.multiply(Math.min(radius, offset.length())));
    }

    @Override
    public double distanceToBoundary(Vec3 point) {
        return Math.abs(distanceToSegment(point) - radius);
    }

    @Override
    public Bounds3 bounds() {
        Vec3 delta = new Vec3(radius, radius, radius);
        return new Bounds3(
                new Vec3(
                        Math.min(spine.start().x(), spine.end().x()),
                        Math.min(spine.start().y(), spine.end().y()),
                        Math.min(spine.start().z(), spine.end().z())
                ).subtract(delta),
                new Vec3(
                        Math.max(spine.start().x(), spine.end().x()),
                        Math.max(spine.start().y(), spine.end().y()),
                        Math.max(spine.start().z(), spine.end().z())
                ).add(delta)
        );
    }

    private Vec3 nearestPointOnSegment(Vec3 point) {
        Vec3 delta = spine.delta();
        double lengthSquared = delta.lengthSquared();
        if (lengthSquared == 0.0) {
            return spine.start();
        }
        double progress = point.subtract(spine.start()).dot(delta) / lengthSquared;
        return spine.pointAt(Math.max(0.0, Math.min(1.0, progress)));
    }

    private double distanceToSegment(Vec3 point) {
        return point.distance(nearestPointOnSegment(point));
    }
}
