package sh.harold.creative.library.trajectory.core;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.spatial.Angle;
import sh.harold.creative.library.spatial.Vec3;
import sh.harold.creative.library.tick.InstanceConflictPolicy;
import sh.harold.creative.library.tick.KeyedHandle;
import sh.harold.creative.library.trajectory.CollisionQuery;
import sh.harold.creative.library.trajectory.CollisionResponseMode;
import sh.harold.creative.library.trajectory.PreviewRecomputePolicy;
import sh.harold.creative.library.trajectory.PreviewScope;
import sh.harold.creative.library.trajectory.TrajectoryMotion;
import sh.harold.creative.library.trajectory.TrajectoryPreviewSnapshot;
import sh.harold.creative.library.trajectory.TrajectoryPreviewSpec;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardTrajectoryPreviewControllerTest {

    @Test
    void oneShotPreviewCachesUntilForceRefresh() {
        StandardTrajectoryPreviewController controller = new StandardTrajectoryPreviewController();
        AtomicReference<TrajectoryMotion> motion = new AtomicReference<>(motion(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0)));
        controller.start(spec("dash", motion::get, new PreviewRecomputePolicy.OneShot(), InstanceConflictPolicy.REPLACE));

        TrajectoryPreviewSnapshot first = controller.tick().getFirst();
        assertTrue(first.recomputed());
        assertEquals(1.0, first.result().endPosition().x(), 1.0e-6);

        motion.set(motion(new Vec3(5.0, 0.0, 0.0), new Vec3(2.0, 0.0, 0.0)));
        TrajectoryPreviewSnapshot second = controller.tick().getFirst();
        assertFalse(second.recomputed());
        assertEquals(1.0, second.result().endPosition().x(), 1.0e-6);

        assertTrue(controller.refresh(Key.key("test", "dash")));
        TrajectoryPreviewSnapshot third = controller.tick().getFirst();
        assertTrue(third.recomputed());
        assertEquals(7.0, third.result().endPosition().x(), 1.0e-6);
        assertEquals(0L, third.ageTicks());
    }

    @Test
    void thresholdedPreviewIgnoresSmallOriginDriftAndRecomputesAfterThreshold() {
        StandardTrajectoryPreviewController controller = new StandardTrajectoryPreviewController();
        AtomicReference<TrajectoryMotion> motion = new AtomicReference<>(motion(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0)));
        controller.start(spec(
                "blink",
                motion::get,
                new PreviewRecomputePolicy.Thresholded(1.0, Angle.degrees(15.0)),
                InstanceConflictPolicy.REPLACE
        ));

        assertTrue(controller.tick().getFirst().recomputed());

        motion.set(motion(new Vec3(0.5, 0.0, 0.0), new Vec3(1.0, 0.0, 0.0)));
        assertFalse(controller.tick().getFirst().recomputed());

        motion.set(motion(new Vec3(1.25, 0.0, 0.0), new Vec3(1.0, 0.0, 0.0)));
        TrajectoryPreviewSnapshot recomputed = controller.tick().getFirst();
        assertTrue(recomputed.recomputed());
        assertEquals(2.25, recomputed.result().endPosition().x(), 1.0e-6);
    }

    @Test
    void refreshPolicyPreservesGenerationAndUpdatesSpec() {
        StandardTrajectoryPreviewController controller = new StandardTrajectoryPreviewController();
        AtomicReference<TrajectoryMotion> firstMotion = new AtomicReference<>(motion(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0)));
        AtomicReference<TrajectoryMotion> refreshedMotion = new AtomicReference<>(motion(Vec3.ZERO, new Vec3(3.0, 0.0, 0.0)));
        KeyedHandle firstHandle = controller.start(spec(
                "pull",
                firstMotion::get,
                new PreviewRecomputePolicy.EveryTick(),
                InstanceConflictPolicy.REPLACE
        ));

        controller.tick();

        KeyedHandle refreshedHandle = controller.start(spec(
                "pull",
                refreshedMotion::get,
                new PreviewRecomputePolicy.EveryTick(),
                InstanceConflictPolicy.REFRESH
        ));

        assertTrue(firstHandle.active());
        assertTrue(refreshedHandle.active());

        TrajectoryPreviewSnapshot snapshot = controller.tick().getFirst();
        assertTrue(snapshot.recomputed());
        assertEquals(3.0, snapshot.result().endPosition().x(), 1.0e-6);
        assertEquals(0L, snapshot.ageTicks());
    }

    @Test
    void replaceInvalidatesOldHandle() {
        StandardTrajectoryPreviewController controller = new StandardTrajectoryPreviewController();
        KeyedHandle first = controller.start(spec("lob", () -> motion(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0)),
                new PreviewRecomputePolicy.EveryTick(), InstanceConflictPolicy.REPLACE));
        KeyedHandle second = controller.start(spec("lob", () -> motion(Vec3.ZERO, new Vec3(2.0, 0.0, 0.0)),
                new PreviewRecomputePolicy.EveryTick(), InstanceConflictPolicy.REPLACE));

        assertFalse(first.active());
        assertTrue(second.active());
    }

    private static TrajectoryPreviewSpec spec(
            String key,
            sh.harold.creative.library.trajectory.TrajectoryMotionSource motionSource,
            PreviewRecomputePolicy recomputePolicy,
            InstanceConflictPolicy conflictPolicy
    ) {
        return new TrajectoryPreviewSpec(
                Key.key("test", key),
                new PreviewScope.OwnerOnly(UUID.randomUUID()),
                motionSource,
                noCollision(),
                recomputePolicy,
                conflictPolicy
        );
    }

    private static CollisionQuery noCollision() {
        return (segment, radius) -> Optional.empty();
    }

    private static TrajectoryMotion motion(Vec3 initialPosition, Vec3 initialVelocity) {
        return new TrajectoryMotion(
                initialPosition,
                initialVelocity,
                Vec3.ZERO,
                new Vec3(1.0, 1.0, 1.0),
                0.0,
                1L,
                CollisionResponseMode.STOP_ON_HIT
        );
    }
}
