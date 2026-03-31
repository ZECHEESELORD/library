package sh.harold.creative.library.trajectory;

public interface TrajectorySolver {

    TrajectoryPreviewResult solve(TrajectoryMotion motion, CollisionQuery collisionQuery);
}
