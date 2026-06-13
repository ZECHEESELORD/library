package sh.harold.creative.library.trajectory.paper;

import org.bukkit.FluidCollisionMode;
import org.bukkit.World;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import sh.harold.creative.library.spatial.Segment3;
import sh.harold.creative.library.spatial.Vec3;
import sh.harold.creative.library.trajectory.CollisionHit;
import sh.harold.creative.library.trajectory.CollisionQuery;
import sh.harold.creative.library.trajectory.TrajectoryMotion;
import sh.harold.creative.library.trajectory.TrajectoryPreviewResult;
import sh.harold.creative.library.trajectory.core.StandardTrajectorySolver;

import java.util.Objects;
import java.util.Optional;

public final class PaperTrajectoryPreviewPlatform {

    private final StandardTrajectorySolver solver = new StandardTrajectorySolver();

    public TrajectoryPreviewResult solve(TrajectoryMotion motion, CollisionQuery collisionQuery) {
        return solver.solve(motion, collisionQuery);
    }

    public CollisionQuery blockCollision(World world) {
        World value = Objects.requireNonNull(world, "world");
        return (segment, radius) -> rayTrace(value, segment);
    }

    private static Optional<CollisionHit> rayTrace(World world, Segment3 segment) {
        Vec3 delta = segment.end().subtract(segment.start());
        double length = delta.length();
        if (length == 0.0) {
            return Optional.empty();
        }
        RayTraceResult result = world.rayTraceBlocks(
                new org.bukkit.Location(world, segment.start().x(), segment.start().y(), segment.start().z()),
                new Vector(delta.x(), delta.y(), delta.z()).normalize(),
                length,
                FluidCollisionMode.NEVER,
                true
        );
        if (result == null || result.getHitPosition() == null) {
            return Optional.empty();
        }
        Vec3 hitPosition = new Vec3(result.getHitPosition().getX(), result.getHitPosition().getY(), result.getHitPosition().getZ());
        Vec3 normal = result.getHitBlockFace() == null
                ? Vec3.ZERO
                : new Vec3(result.getHitBlockFace().getModX(), result.getHitBlockFace().getModY(), result.getHitBlockFace().getModZ());
        return Optional.of(new CollisionHit(hitPosition, normal, segment, 0L));
    }
}
