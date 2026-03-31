package sh.harold.creative.library.trajectory;

import sh.harold.creative.library.spatial.Segment3;
import sh.harold.creative.library.spatial.Vec3;

import java.util.Objects;

public record CollisionHit(Vec3 position, Vec3 normal, Segment3 sweep, long tickIndex) {

    public CollisionHit {
        position = Objects.requireNonNull(position, "position");
        normal = Objects.requireNonNull(normal, "normal");
        sweep = Objects.requireNonNull(sweep, "sweep");
        if (tickIndex < 0L) {
            throw new IllegalArgumentException("tickIndex cannot be negative");
        }
    }
}
