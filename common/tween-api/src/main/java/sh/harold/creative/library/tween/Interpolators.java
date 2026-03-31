package sh.harold.creative.library.tween;

import sh.harold.creative.library.spatial.Angle;
import sh.harold.creative.library.spatial.Vec3;

public final class Interpolators {

    private Interpolators() {
    }

    public static Interpolator<Double> doubles() {
        return (from, to, progress) -> from + ((to - from) * progress);
    }

    public static Interpolator<Vec3> vectors() {
        return (from, to, progress) -> new Vec3(
                from.x() + ((to.x() - from.x()) * progress),
                from.y() + ((to.y() - from.y()) * progress),
                from.z() + ((to.z() - from.z()) * progress)
        );
    }

    public static Interpolator<Angle> shortestAngles() {
        return (from, to, progress) -> from.add(from.shortestDeltaTo(to).multiply(progress));
    }
}
