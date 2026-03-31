package sh.harold.creative.library.trajectory;

import org.junit.jupiter.api.Test;
import sh.harold.creative.library.spatial.Vec3;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrajectoryApiTest {

    @Test
    void motionKeepsConfiguredInputs() {
        TrajectoryMotion motion = new TrajectoryMotion(
                Vec3.ZERO,
                new Vec3(1.0, 2.0, 3.0),
                new Vec3(0.0, -0.1, 0.0),
                new Vec3(1.0, 1.0, 1.0),
                0.5,
                20L,
                CollisionResponseMode.STOP_ON_HIT
        );

        assertEquals(20L, motion.maxSimulationTicks());
    }

    @Test
    void previewResultCanBeValidWithoutHit() {
        TrajectoryPreviewResult result = new TrajectoryPreviewResult(
                java.util.List.of(Vec3.ZERO),
                0.0,
                0L,
                Optional.empty(),
                Vec3.ZERO,
                true,
                PreviewInvalidReason.NONE
        );

        assertTrue(result.valid());
    }
}
