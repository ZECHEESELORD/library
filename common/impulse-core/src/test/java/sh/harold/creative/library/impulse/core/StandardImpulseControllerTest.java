package sh.harold.creative.library.impulse.core;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.impulse.AxisMask;
import sh.harold.creative.library.impulse.ComposedImpulse;
import sh.harold.creative.library.impulse.ImpulseActorState;
import sh.harold.creative.library.impulse.ImpulseMode;
import sh.harold.creative.library.impulse.ImpulseSpec;
import sh.harold.creative.library.impulse.ImpulseStackMode;
import sh.harold.creative.library.impulse.ImpulseVector;
import sh.harold.creative.library.spatial.Frame3;
import sh.harold.creative.library.spatial.Vec3;
import sh.harold.creative.library.tick.InstanceConflictPolicy;
import sh.harold.creative.library.tick.KeyedHandle;
import sh.harold.creative.library.tween.Envelope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardImpulseControllerTest {

    @Test
    void sameKeyReplacementInvalidatesOldHandle() {
        StandardImpulseController controller = new StandardImpulseController();

        KeyedHandle first = controller.start(spec("push", ImpulseMode.ADD_VELOCITY, new ImpulseVector.World(new Vec3(1.0, 0.0, 0.0))));
        KeyedHandle second = controller.start(spec("push", ImpulseMode.ADD_VELOCITY, new ImpulseVector.World(new Vec3(2.0, 0.0, 0.0))));

        assertFalse(first.active());
        assertTrue(second.active());
    }

    @Test
    void refreshPreservesHandleAndUsesNewImpulse() {
        StandardImpulseController controller = new StandardImpulseController();
        KeyedHandle handle = controller.start(spec(
                "push",
                ImpulseMode.ADD_VELOCITY,
                new ImpulseVector.World(new Vec3(1.0, 0.0, 0.0)),
                InstanceConflictPolicy.REPLACE
        ));

        controller.start(spec(
                "push",
                ImpulseMode.ADD_VELOCITY,
                new ImpulseVector.World(new Vec3(3.0, 0.0, 0.0)),
                InstanceConflictPolicy.REFRESH
        ));

        assertTrue(handle.active());
        assertEquals(3.0, controller.tick(actor(Vec3.UNIT_Z)).additiveVelocity().x(), 1.0e-6);
    }

    @Test
    void localLookVectorResolvesAgainstActorFrame() {
        StandardImpulseController controller = new StandardImpulseController();
        controller.start(spec("look", ImpulseMode.ADD_VELOCITY, new ImpulseVector.LocalLook(new Vec3(0.0, 0.0, 1.0))));

        ComposedImpulse sample = controller.tick(actor(new Vec3(1.0, 0.0, 0.0)));
        assertEquals(1.0, sample.additiveVelocity().x(), 1.0e-6);
    }

    @Test
    void absoluteVelocityWinnerUsesPriority() {
        StandardImpulseController controller = new StandardImpulseController();
        controller.start(new ImpulseSpec(
                Key.key("test", "slow"),
                ImpulseMode.SET_VELOCITY,
                new ImpulseVector.World(new Vec3(1.0, 0.0, 0.0)),
                0L,
                2L,
                Envelope.constant(1.0),
                AxisMask.ALL,
                ImpulseStackMode.PRIORITY_WINNER,
                0.0,
                1,
                InstanceConflictPolicy.REPLACE
        ));
        controller.start(new ImpulseSpec(
                Key.key("test", "fast"),
                ImpulseMode.SET_VELOCITY,
                new ImpulseVector.World(new Vec3(3.0, 0.0, 0.0)),
                0L,
                2L,
                Envelope.constant(1.0),
                AxisMask.ALL,
                ImpulseStackMode.PRIORITY_WINNER,
                0.0,
                5,
                InstanceConflictPolicy.REPLACE
        ));

        assertEquals(3.0, controller.tick(actor(Vec3.UNIT_Z)).absoluteVelocity().orElseThrow().x(), 1.0e-6);
    }

    @Test
    void maxMagnitudePerAxisPreservesLargestSignedAxis() {
        StandardImpulseController controller = new StandardImpulseController();
        controller.start(new ImpulseSpec(
                Key.key("test", "a"),
                ImpulseMode.ADD_VELOCITY,
                new ImpulseVector.World(new Vec3(1.0, 3.0, 0.0)),
                0L,
                2L,
                Envelope.constant(1.0),
                AxisMask.ALL,
                ImpulseStackMode.MAX_MAGNITUDE_PER_AXIS,
                0.0,
                0,
                InstanceConflictPolicy.REPLACE
        ));
        controller.start(new ImpulseSpec(
                Key.key("test", "b"),
                ImpulseMode.ADD_VELOCITY,
                new ImpulseVector.World(new Vec3(-2.0, 1.0, 4.0)),
                0L,
                2L,
                Envelope.constant(1.0),
                AxisMask.ALL,
                ImpulseStackMode.MAX_MAGNITUDE_PER_AXIS,
                0.0,
                0,
                InstanceConflictPolicy.REPLACE
        ));

        ComposedImpulse sample = controller.tick(actor(Vec3.UNIT_Z));
        assertEquals(-2.0, sample.additiveVelocity().x(), 1.0e-6);
        assertEquals(3.0, sample.additiveVelocity().y(), 1.0e-6);
        assertEquals(4.0, sample.additiveVelocity().z(), 1.0e-6);
    }

    private static ImpulseSpec spec(String key, ImpulseMode mode, ImpulseVector vector) {
        return spec(key, mode, vector, InstanceConflictPolicy.REPLACE);
    }

    private static ImpulseSpec spec(String key, ImpulseMode mode, ImpulseVector vector, InstanceConflictPolicy conflictPolicy) {
        return new ImpulseSpec(
                Key.key("test", key),
                mode,
                vector,
                0L,
                3L,
                Envelope.constant(1.0),
                AxisMask.ALL,
                ImpulseStackMode.ADD,
                0.0,
                0,
                conflictPolicy
        );
    }

    private static ImpulseActorState actor(Vec3 forward) {
        return new ImpulseActorState(Vec3.ZERO, Frame3.of(Vec3.ZERO, forward, Vec3.UNIT_Y));
    }
}
