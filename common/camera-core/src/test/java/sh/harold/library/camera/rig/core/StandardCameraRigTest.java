package sh.harold.library.camera.rig.core;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import sh.harold.library.camera.rig.CameraChannel;
import sh.harold.library.camera.rig.CameraState;
import sh.harold.library.camera.rig.IntensityProfile;
import sh.harold.library.camera.rig.LayerState;
import sh.harold.library.camera.rig.ViewAngles;
import sh.harold.library.camera.rig.ViewInput;
import sh.harold.library.camera.rig.cue.CameraCue;
import sh.harold.library.camera.rig.cue.CameraCueHandle;
import sh.harold.library.camera.rig.cue.ChannelCurve;
import sh.harold.library.camera.rig.cue.CueCurve;
import sh.harold.library.spatial.Quaternion;
import sh.harold.library.spatial.Vec3;
import sh.harold.library.tick.InstanceConflictPolicy;
import sh.harold.library.tick.KeyedHandle;
import sh.harold.library.tween.Easing;
import sh.harold.library.tween.signal.SignalChannelSpec;
import sh.harold.library.tween.spring.SpringSpec;
import sh.harold.library.tween.track.Track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardCameraRigTest {

    private static final double EPS = 1e-9;
    private static final ViewInput VIEW = new ViewInput(new Vec3(3.5, 65.62, -7.25), 33.0, 12.5, 70.0);

    private static Key key(String value) {
        return Key.key("test", value);
    }

    private static CameraCue fov(String name, double amount, double ticks) {
        return CameraCue.builder(key(name)).channel(CameraChannel.FOV, CueCurve.constant(amount, ticks)).build();
    }

    /** The roll from the architecture doc: an unwound 360 degree pitch flip, a 25 degree dip in Reduced. */
    private static CameraCue roll() {
        Track<Double> flip = Track.scalar()
                .eased(0, 0.0, Easing.QUAD_IN)
                .eased(5, 180.0, Easing.QUAD_OUT)
                .hermite(10, 360.0)
                .build();
        return CameraCue.builder(key("roll"))
                .owner(key("moves"))
                .curve(ChannelCurve.of(CameraChannel.PITCH, CueCurve.track(flip))
                        .withReduced(CueCurve.pulse(25.0, 3, Easing.QUAD_OUT, 2, 5, Easing.QUAD_IN_OUT))
                        .unwoundPitch())
                .channel(CameraChannel.FOV, CueCurve.pulse(6.0, 3, Easing.QUAD_OUT, 2, 5, Easing.QUAD_IN_OUT))
                .channel(CameraChannel.ROLL, CueCurve.constant(10.0, 10))
                .lookScale(0.2, 10)
                .build();
    }

    @Test
    void idleRigIsTheAnchorExactly() {
        StandardCameraRig rig = new StandardCameraRig();
        CameraState state = rig.sample(VIEW, 0.5);
        CameraState anchor = CameraState.anchored(VIEW.eyePosition(), new ViewAngles(33.0, 12.5, 0.0), 70.0);
        assertEquals(anchor, state);
        assertTrue(state.layers().isIdentity());
        assertEquals(1.0, state.lookInputScale());
        assertTrue(rig.idle());
    }

    @Test
    void cuesSumPerChannelAndLookScalesMultiply() {
        StandardCameraRig rig = new StandardCameraRig();
        rig.start(fov("a", 5.0, 20));
        rig.start(fov("b", 3.0, 20));
        rig.start(CameraCue.builder(key("p1")).channel(CameraChannel.PITCH, CueCurve.constant(4.0, 20)).build());
        rig.start(CameraCue.builder(key("p2")).channel(CameraChannel.PITCH, CueCurve.constant(-1.5, 20)).build());
        rig.start(CameraCue.builder(key("e")).channel(CameraChannel.EYE_HEIGHT, CueCurve.constant(-0.4, 20)).build());
        rig.start(CameraCue.builder(key("l1")).lookScale(0.5, 20).build());
        rig.start(CameraCue.builder(key("l2")).lookScale(0.4, 20).build());

        CameraState state = rig.sample(VIEW, 2.25);
        assertEquals(8.0, state.layers().fovAdd(), EPS);
        assertEquals(78.0, state.fovDegrees(), EPS);
        assertEquals(2.5, state.layers().pitchAdd(), EPS);
        assertEquals(15.0, state.angles().pitch(), EPS);
        assertEquals(33.0, state.angles().yaw(), EPS);
        assertEquals(-0.4, state.eyeHeightOffset(), EPS);
        assertEquals(VIEW.eyePosition().y() - 0.4, state.position().y(), EPS);
        assertEquals(0.2, state.lookInputScale(), EPS);
        assertEquals(7, state.layers().activeCues().size());
        // The orientation is the anchor pitched locally by the added pitch.
        Quaternion expected = ViewAngles.yawPitch(33.0, 12.5).multiply(ViewAngles.pitchQuaternion(2.5));
        assertTrue(expected.angleTo(state.orientation()) < 1e-6);
    }

    @Test
    void cancelRemovesTheCueAtOnceAndReleaseFadesIt() {
        StandardCameraRig rig = new StandardCameraRig();
        CameraCueHandle cancelled = rig.start(fov("cancel", 5.0, 40));
        cancelled.cancel();
        assertFalse(cancelled.active());
        assertTrue(rig.sample(VIEW, 1.0).layers().isIdentity());

        CameraCueHandle held = rig.start(CameraCue.builder(key("held"))
                .channel(CameraChannel.FOV, CueCurve.held(10.0, 0, Easing.LINEAR, 4, Easing.LINEAR)).build());
        rig.tick();
        rig.tick();
        held.release();
        assertTrue(held.released());
        assertEquals(10.0, rig.sample(VIEW, 1.5).layers().fovAdd(), EPS);
        assertEquals(10.0, rig.sample(VIEW, 2.0).layers().fovAdd(), EPS);
        assertEquals(5.0, rig.sample(VIEW, 4.0).layers().fovAdd(), EPS);
        assertEquals(0.0, rig.sample(VIEW, 6.5).layers().fovAdd(), EPS);
        for (int i = 0; i < 8; i++) {
            rig.tick();
        }
        assertFalse(held.active());
        assertTrue(rig.idle());
    }

    @Test
    void conflictPoliciesReplaceRefreshAndReject() {
        StandardCameraRig rig = new StandardCameraRig();
        CameraCueHandle first = rig.start(fov("same", 5.0, 40));
        CameraCueHandle second = rig.start(fov("same", 2.0, 40));
        assertFalse(first.active());
        assertTrue(second.active());
        assertEquals(2.0, rig.sample(VIEW, 1.0).layers().fovAdd(), EPS);

        CameraCue reject = CameraCue.builder(key("same")).channel(CameraChannel.FOV, CueCurve.constant(9.0, 40))
                .conflict(InstanceConflictPolicy.REJECT).build();
        assertSame(second, rig.start(reject));
        assertEquals(2.0, rig.sample(VIEW, 1.0).layers().fovAdd(), EPS);

        rig.tick();
        rig.tick();
        CameraCue refresh = CameraCue.builder(key("same")).channel(CameraChannel.FOV, CueCurve.constant(7.0, 40))
                .conflict(InstanceConflictPolicy.REFRESH).build();
        CameraCueHandle refreshed = rig.start(refresh);
        assertSame(second, refreshed);
        assertEquals(2.0, refreshed.startTime(), EPS);
        assertEquals(7.0, rig.sample(VIEW, 2.5).layers().fovAdd(), EPS);

        CameraCueHandle replaced = refreshed.replace(fov("other", 1.0, 40));
        assertFalse(refreshed.active());
        assertTrue(replaced.active());
        assertEquals(1.0, rig.sample(VIEW, 2.5).layers().fovAdd(), EPS);
    }

    @Test
    void cancelAllEndsOnlyThatOwnersCues() {
        StandardCameraRig rig = new StandardCameraRig();
        CameraCueHandle mine = rig.start(roll());
        CameraCueHandle other = rig.start(fov("other", 3.0, 40));
        rig.cancelAll(key("moves"));
        assertFalse(mine.active());
        assertTrue(other.active());
        assertEquals(3.0, rig.sample(VIEW, 1.0).layers().fovAdd(), EPS);
    }

    @Test
    void reducedHalvesFovRemovesRollUsesSubstitutesAndCapsShake() {
        StandardCameraRig rig = new StandardCameraRig();
        rig.start(roll());
        rig.addTrauma(1.0);

        CameraState full = rig.sample(VIEW, 4.0);
        assertEquals(6.0, full.layers().fovAdd(), EPS);
        assertTrue(full.layers().roll() > 9.0);
        assertTrue(full.layers().pitchAdd() > 90.0, "full plays the flip");
        assertTrue(full.layers().pitchUnwound());
        assertTrue(full.layers().shakeAmplitude() > 0.0);
        assertEquals(0.2, full.lookInputScale(), EPS);

        rig.setIntensity(IntensityProfile.REDUCED);
        CameraState reduced = rig.sample(VIEW, 4.0);
        assertEquals(3.0, reduced.layers().fovAdd(), EPS);
        assertEquals(0.0, reduced.layers().roll(), EPS);
        assertEquals(0.0, reduced.layers().shakeRoll(), EPS);
        assertEquals(0.0, reduced.layers().shakeAmplitude(), EPS);
        // The 25 degree dip is used as authored, not scaled again.
        assertEquals(25.0, reduced.layers().pitchAdd(), EPS);
        assertFalse(reduced.layers().pitchUnwound(), "the substitute is not unwound, so the pitch clamp applies");
        assertEquals(0.2, reduced.lookInputScale(), EPS);
    }

    @Test
    void framesSampleExactlyAtFractionalTicks() {
        StandardCameraRig rig = new StandardCameraRig();
        Track<Double> ramp = Track.scalar().eased(0, 0.0, Easing.LINEAR).hermite(10, 20.0).build();
        rig.start(CameraCue.builder(key("ramp")).channel(CameraChannel.FOV, CueCurve.track(ramp)).build(), 0.0);
        for (int frame = 0; frame <= 30; frame++) {
            double time = frame / 3.0;
            assertEquals(ramp.sample(time), rig.sample(VIEW, time).layers().fovAdd(), EPS, "frame " + frame);
            assertEquals(2.0 * time, rig.sample(VIEW, time).layers().fovAdd(), 1e-9);
        }
    }

    @Test
    void seekingInAnyOrderGivesTheSameStates() {
        StandardCameraRig rig = new StandardCameraRig();
        rig.start(roll(), 0.0);
        rig.addTrauma(0.8);
        List<Double> times = new ArrayList<>();
        for (int frame = 0; frame < 90; frame++) {
            times.add(frame / 6.0 - 0.5);
        }
        List<CameraState> forward = new ArrayList<>();
        for (double time : times) {
            forward.add(rig.sample(VIEW, time));
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            order.add(i);
        }
        Collections.shuffle(order, new Random(42));
        for (int i : order) {
            assertEquals(forward.get(i), rig.sample(VIEW, times.get(i)), "time " + times.get(i));
        }
    }

    @Test
    void pitchIsClampedUnlessUnwound() {
        StandardCameraRig rig = new StandardCameraRig();
        ViewInput lookingDown = new ViewInput(VIEW.eyePosition(), 0.0, 80.0, 70.0);
        CameraCueHandle dip = rig.start(CameraCue.builder(key("dip"))
                .channel(CameraChannel.PITCH, CueCurve.constant(30.0, 20)).build());
        CameraState clamped = rig.sample(lookingDown, 1.0);
        assertEquals(10.0, clamped.layers().pitchAdd(), EPS);
        assertEquals(90.0, clamped.angles().pitch(), EPS);
        dip.cancel();

        rig.start(CameraCue.builder(key("flip"))
                .curve(ChannelCurve.of(CameraChannel.PITCH, CueCurve.constant(30.0, 20)).unwoundPitch()).build());
        CameraState unwound = rig.sample(lookingDown, 1.0);
        assertEquals(30.0, unwound.layers().pitchAdd(), EPS);
        assertEquals(110.0, unwound.angles().pitch(), EPS);
    }

    @Test
    void safetyLimitsBoundOnlyTheAddedPart() {
        StandardCameraRig rig = new StandardCameraRig();
        rig.start(fov("huge", 100.0, 20));
        CameraState state = rig.sample(VIEW, 1.0);
        assertEquals(40.0, state.layers().fovAdd(), EPS);
        assertEquals(110.0, state.fovDegrees(), EPS);

        // A wide base FOV above the limit is never pulled down; only the addition is refused.
        CameraState wide = rig.sample(new ViewInput(VIEW.eyePosition(), 0.0, 0.0, 120.0), 1.0);
        assertEquals(0.0, wide.layers().fovAdd(), EPS);
        assertEquals(120.0, wide.fovDegrees(), EPS);
    }

    @Test
    void traumaDecaysLinearlyAndDrivesSeededShake() {
        StandardCameraRig rig = new StandardCameraRig();
        rig.tick();
        rig.addTrauma(1.0);
        assertEquals(1.0, rig.trauma(1.0), EPS);
        assertEquals(0.5, rig.trauma(11.0), EPS);
        assertEquals(0.0, rig.trauma(25.0), EPS);
        assertEquals(0.0, rig.trauma(0.5), EPS);

        CameraState shaken = rig.sample(VIEW, 1.4);
        assertEquals(3.0 * 0.98 * 0.98, shaken.layers().shakeAmplitude(), EPS);
        assertTrue(Math.abs(shaken.layers().shakePitch()) <= shaken.layers().shakeAmplitude() + EPS);

        StandardCameraRig twin = new StandardCameraRig();
        twin.tick();
        twin.addTrauma(1.0);
        assertEquals(shaken, twin.sample(VIEW, 1.4), "same seed, same shake");
    }

    @Test
    void signalLayersAreContinuousAcrossTicks() {
        StandardCameraRig rig = new StandardCameraRig();
        double[] speed = {0.0};
        KeyedHandle layer = rig.addSignalLayer(key("speed"), CameraChannel.FOV,
                new SignalChannelSpec(value -> value * 2.0, SpringSpec.critical(2.0)), () -> speed[0]);
        speed[0] = 5.0;
        double last = 0.0;
        for (int tick = 0; tick < 20; tick++) {
            double before = rig.sample(VIEW, rig.ticks()).layers().fovAdd();
            rig.tick();
            double after = rig.sample(VIEW, rig.ticks() - 1.0).layers().fovAdd();
            assertEquals(before, after, 1e-9, "tick " + tick);
            for (int sub = 1; sub <= 3; sub++) {
                double value = rig.sample(VIEW, rig.ticks() - 1.0 + sub / 3.0).layers().fovAdd();
                assertTrue(value >= last - 1e-9, "rises toward the target");
                last = value;
            }
        }
        assertTrue(last > 5.0 && last <= 10.0 + 1e-9);
        assertFalse(rig.idle());
        layer.close();
        assertFalse(layer.active());
    }

    @Test
    void viewAnglesRoundTripThroughTheQuaternion() {
        for (ViewAngles angles : List.of(new ViewAngles(30.0, -40.0, 15.0), new ViewAngles(-170.0, 75.0, -60.0),
                new ViewAngles(0.0, 0.0, 0.0), new ViewAngles(90.0, 10.0, 0.0))) {
            ViewAngles back = ViewAngles.fromQuaternion(angles.toQuaternion());
            assertEquals(angles.yaw(), back.yaw(), 1e-6);
            assertEquals(angles.pitch(), back.pitch(), 1e-6);
            assertEquals(angles.roll(), back.roll(), 1e-6);
        }
    }

    @Test
    void layerRecordReportsActiveChannelsAndCues() {
        StandardCameraRig rig = new StandardCameraRig();
        rig.start(roll());
        LayerState layers = rig.sample(VIEW, 2.0).layers();
        assertTrue(layers.activeChannels().contains(CameraChannel.PITCH));
        assertTrue(layers.activeChannels().contains(CameraChannel.LOOK_SCALE));
        assertEquals(List.of(key("roll")), layers.activeCues());
    }

    @Test
    void closingEndsEverything() {
        StandardCameraRig rig = new StandardCameraRig();
        CameraCueHandle cue = rig.start(fov("x", 4.0, 20));
        rig.evaluate(VIEW, 0.0);
        rig.close();
        assertFalse(cue.active());
        assertFalse(rig.active());
        assertNull(rig.state());
        assertThrows(IllegalStateException.class, () -> rig.start(fov("y", 1.0, 5)));
        assertTrue(rig.sample(VIEW, 1.0).layers().isIdentity());
    }

    @Test
    void clearCuesKeepsTheRigOpen() {
        StandardCameraRig rig = new StandardCameraRig();
        CameraCueHandle cue = rig.start(fov("x", 4.0, 20));
        rig.addTrauma(0.5);
        rig.clearCues();
        assertFalse(cue.active());
        assertTrue(rig.active());
        assertTrue(rig.idle());
    }
}
