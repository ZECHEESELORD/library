package sh.harold.creative.library.trajectory.minestom;

import net.minestom.server.instance.Instance;
import sh.harold.creative.library.spatial.Segment3;
import sh.harold.creative.library.spatial.Vec3;
import sh.harold.creative.library.trajectory.CollisionHit;
import sh.harold.creative.library.trajectory.CollisionQuery;
import sh.harold.creative.library.trajectory.TrajectoryMotion;
import sh.harold.creative.library.trajectory.TrajectoryPreviewResult;
import sh.harold.creative.library.trajectory.core.StandardTrajectorySolver;

import java.util.Objects;
import java.util.Optional;

public final class MinestomTrajectoryPreviewPlatform {

    private final StandardTrajectorySolver solver = new StandardTrajectorySolver();

    public TrajectoryPreviewResult solve(TrajectoryMotion motion, CollisionQuery collisionQuery) {
        return solver.solve(motion, collisionQuery);
    }

    public CollisionQuery blockCollision(Instance instance) {
        Instance value = Objects.requireNonNull(instance, "instance");
        return (segment, radius) -> sampleAlongSegment(value, segment);
    }

    private static Optional<CollisionHit> sampleAlongSegment(Instance instance, Segment3 segment) {
        Vec3 delta = segment.end().subtract(segment.start());
        double length = delta.length();
        if (length == 0.0) {
            return Optional.empty();
        }
        int steps = Math.max(1, (int) Math.ceil(length / 0.25));
        for (int i = 1; i <= steps; i++) {
            Vec3 point = segment.pointAt(i / (double) steps);
            int blockX = (int) Math.floor(point.x());
            int blockY = (int) Math.floor(point.y());
            int blockZ = (int) Math.floor(point.z());
            if (!instance.getBlock(blockX, blockY, blockZ).isAir()) {
                return Optional.of(new CollisionHit(point, Vec3.ZERO, segment, 0L));
            }
        }
        return Optional.empty();
    }
}
