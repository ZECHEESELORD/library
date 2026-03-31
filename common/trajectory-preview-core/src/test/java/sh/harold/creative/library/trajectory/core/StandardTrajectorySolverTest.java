package sh.harold.creative.library.trajectory.core;

import org.junit.jupiter.api.Test;
import sh.harold.creative.library.spatial.Segment3;
import sh.harold.creative.library.spatial.Vec3;
import sh.harold.creative.library.trajectory.CollisionHit;
import sh.harold.creative.library.trajectory.CollisionQuery;
import sh.harold.creative.library.trajectory.CollisionResponseMode;
import sh.harold.creative.library.trajectory.TrajectoryMotion;
import sh.harold.creative.library.trajectory.TrajectoryPreviewResult;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardTrajectorySolverTest {

    @Test
    void ballisticMotionStepsDeterministically() {
        StandardTrajectorySolver solver = new StandardTrajectorySolver();
        TrajectoryPreviewResult result = solver.solve(
                new TrajectoryMotion(
                        Vec3.ZERO,
                        new Vec3(1.0, 1.0, 0.0),
                        new Vec3(0.0, -0.5, 0.0),
                        new Vec3(1.0, 1.0, 1.0),
                        0.0,
                        3L,
                        CollisionResponseMode.STOP_ON_HIT
                ),
                (segment, radius) -> Optional.empty()
        );

        assertEquals(4, result.sampledPoints().size());
        assertEquals(new Vec3(3.0, 1.5, 0.0), result.endPosition());
    }

    @Test
    void stopOnHitTerminatesAtCollisionPoint() {
        StandardTrajectorySolver solver = new StandardTrajectorySolver();
        CollisionQuery query = (segment, radius) -> segment.end().x() >= 2.0
                ? Optional.of(new CollisionHit(new Vec3(2.0, 0.0, 0.0), new Vec3(-1.0, 0.0, 0.0), segment, 1L))
                : Optional.empty();

        TrajectoryPreviewResult result = solver.solve(
                new TrajectoryMotion(
                        Vec3.ZERO,
                        new Vec3(1.0, 0.0, 0.0),
                        Vec3.ZERO,
                        new Vec3(1.0, 1.0, 1.0),
                        0.0,
                        5L,
                        CollisionResponseMode.STOP_ON_HIT
                ),
                query
        );

        assertTrue(result.firstHit().isPresent());
        assertEquals(2.0, result.endPosition().x(), 1.0e-9);
    }
}
