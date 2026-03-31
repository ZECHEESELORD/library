package sh.harold.creative.library.spatial;

public interface Volume {

    boolean contains(Vec3 point);

    Vec3 nearestPoint(Vec3 point);

    double distanceToBoundary(Vec3 point);

    Bounds3 bounds();

    default double distance(Vec3 point) {
        return point.distance(nearestPoint(point));
    }

    default double signedDistance(Vec3 point) {
        double distance = distanceToBoundary(point);
        return contains(point) ? -distance : distance;
    }
}
