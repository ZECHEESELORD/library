package sh.harold.creative.library.spatial;

import java.util.Objects;

public record Segment3(Vec3 start, Vec3 end) {

    public Segment3 {
        start = Objects.requireNonNull(start, "start");
        end = Objects.requireNonNull(end, "end");
    }

    public Vec3 delta() {
        return end.subtract(start);
    }

    public double length() {
        return start.distance(end);
    }

    public Vec3 pointAt(double progress) {
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        return start.add(delta().multiply(clamped));
    }
}
