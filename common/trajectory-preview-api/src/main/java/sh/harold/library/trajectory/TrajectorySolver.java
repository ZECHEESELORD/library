package sh.harold.library.trajectory;

public interface TrajectorySolver {

    TrajectoryPreviewResult solve(TrajectoryMotion motion, CollisionQuery collisionQuery);
}
