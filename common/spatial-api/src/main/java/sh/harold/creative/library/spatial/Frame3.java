package sh.harold.creative.library.spatial;

import java.util.Objects;

public record Frame3(Vec3 origin, Vec3 right, Vec3 up, Vec3 forward) {

    private static final double EPSILON = 1.0e-9;

    public Frame3 {
        origin = Objects.requireNonNull(origin, "origin");
        right = requireDirection(right, "right");
        up = requireDirection(up, "up");
        forward = requireDirection(forward, "forward");
    }

    public static Frame3 of(Vec3 origin, Vec3 forward, Vec3 upHint) {
        Objects.requireNonNull(origin, "origin");
        Vec3 forwardUnit = requireDirection(forward, "forward");
        Vec3 upSeed = requireDirection(upHint, "upHint");
        Vec3 right = upSeed.cross(forwardUnit).normalize();
        if (right.isZero(EPSILON)) {
            throw new IllegalArgumentException("forward and upHint cannot be parallel");
        }
        Vec3 correctedUp = forwardUnit.cross(right).normalize();
        return new Frame3(origin, right, correctedUp, forwardUnit);
    }

    public static Frame3 world(Vec3 origin) {
        return of(origin, Vec3.UNIT_Z, Vec3.UNIT_Y);
    }

    public Vec3 localToWorldPoint(Vec3 localPoint) {
        return origin
                .add(right.multiply(localPoint.x()))
                .add(up.multiply(localPoint.y()))
                .add(forward.multiply(localPoint.z()));
    }

    public Vec3 localToWorldVector(Vec3 localVector) {
        return right.multiply(localVector.x())
                .add(up.multiply(localVector.y()))
                .add(forward.multiply(localVector.z()));
    }

    public Vec3 worldToLocalPoint(Vec3 worldPoint) {
        Vec3 offset = worldPoint.subtract(origin);
        return new Vec3(offset.dot(right), offset.dot(up), offset.dot(forward));
    }

    public Vec3 worldToLocalVector(Vec3 worldVector) {
        return new Vec3(worldVector.dot(right), worldVector.dot(up), worldVector.dot(forward));
    }

    public Frame3 translated(Vec3 delta) {
        return new Frame3(origin.add(delta), right, up, forward);
    }

    private static Vec3 requireDirection(Vec3 vector, String name) {
        Objects.requireNonNull(vector, name);
        Vec3 normalized = vector.normalize();
        if (normalized.isZero(EPSILON)) {
            throw new IllegalArgumentException(name + " cannot be zero");
        }
        return normalized;
    }
}
