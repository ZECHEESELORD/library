package sh.harold.library.movement.core;

import org.junit.jupiter.api.Test;
import sh.harold.library.camera.rig.CameraChannel;
import sh.harold.library.camera.rig.CameraState;
import sh.harold.library.camera.rig.ViewInput;
import sh.harold.library.camera.rig.core.StandardCameraRig;
import sh.harold.library.camera.rig.cue.CameraCue;
import sh.harold.library.camera.rig.cue.ChannelCurve;
import sh.harold.library.camera.rig.cue.CueCurve;
import sh.harold.library.movement.JumpPreset;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.SlideVariant;
import sh.harold.library.movement.core.camera.MoveCameras;
import sh.harold.library.movement.core.tuning.Moveset;
import sh.harold.library.movement.core.tuning.RollTuning;
import sh.harold.library.movement.core.tuning.SlideTuning;
import sh.harold.library.reload.DataDecodeException;
import sh.harold.library.spatial.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuningAndCameraTest {

    @Test
    void defaultsCarry02sNumbers() {
        Moveset moveset = Moveset.DEFAULT;
        assertEquals(Set.copyOf(MoveKeys.STOCK), moveset.enabled());
        assertEquals(4, moveset.core().inputBufferTicks());
        assertEquals(3, moveset.core().coyoteTicks());
        assertEquals(JumpPreset.A, moveset.jump().preset());
        assertEquals(0.028, moveset.jump().boost().gravity());
        assertEquals(0.57, moveset.jump().charge().launchSpeed(8));
        assertEquals(10.0, moveset.roll().damageReduction());
        assertEquals(SlideVariant.S1, moveset.slide().variant());
        assertEquals(0.48, moveset.wallJump().kickUp());
        assertTrue(moveset.enables(MoveKeys.WALL_SLIDE));
        assertTrue(Moveset.none().enabled().isEmpty());
    }

    @Test
    void editedTextChangesOneNumber() {
        Map<String, String> texts = new HashMap<>(Moveset.defaultTexts());
        texts.put("roll.json", Moveset.defaultText("roll.json").replace("\"damageReduction\": 10", "\"damageReduction\": 7"));
        assertEquals(7.0, Moveset.decode(Set.of(MoveKeys.ROLL), texts).roll().damageReduction());
    }

    @Test
    void badFileFailsWithItsPath() {
        Map<String, String> texts = new HashMap<>(Moveset.defaultTexts());
        texts.put("jump.json", Moveset.defaultText("jump.json").replace("\"preset\": \"A\"", "\"preset\": \"D\""));
        DataDecodeException error = assertThrows(DataDecodeException.class, () -> Moveset.decode(Set.of(), texts));
        assertTrue(error.getMessage().contains("preset"), error.getMessage());
        texts.put("jump.json", Moveset.defaultText("jump.json").replace("\"gravity\": 0.028,", "").replace(
                "\"gravity\": 0.028", ""));
        assertThrows(DataDecodeException.class, () -> Moveset.decode(Set.of(), texts));
    }

    static CueCurve.TrackCurve curve(CameraCue cue, CameraChannel channel, boolean reduced) {
        ChannelCurve curve = cue.curves().stream().filter(c -> c.channel() == channel).findFirst().orElseThrow();
        return (CueCurve.TrackCurve) curve.curveFor(reduced);
    }

    @Test
    void rollTurnsAFullCircleAndReducedDips() {
        RollTuning roll = Moveset.DEFAULT.roll();
        CameraCue cue = MoveCameras.roll(roll);
        var pitch = curve(cue, CameraChannel.PITCH, false).track();
        assertEquals(0.0, pitch.sample(0.0), 1.0e-9);
        assertEquals(180.0, pitch.sample(6.0), 1.0e-9, "180 degrees has passed at the end of T5");
        assertEquals(360.0, pitch.sample(10.0), 1.0e-9, "back where it started at T10");
        assertTrue(cue.curves().stream().anyMatch(c -> c.channel() == CameraChannel.PITCH && c.unwound()));
        var dip = curve(cue, CameraChannel.PITCH, true).track();
        assertEquals(25.0, dip.sample(4.0), 1.0e-9, "Reduced: a clean 25 degree dip");
        assertEquals(0.0, dip.sample(10.0), 1.0e-9);
        var fov = curve(cue, CameraChannel.FOV, false).track();
        assertEquals(6.0, fov.sample(4.0), 1.0e-9, "FOV peak at T3");
        assertEquals(0.0, fov.sample(10.0), 1.0e-9);
    }

    @Test
    void rollEyeIsAbsoluteAndFollows02() {
        RollTuning roll = Moveset.DEFAULT.roll();
        ChannelCurve eye = MoveCameras.roll(roll).curves().stream()
                .filter(c -> c.channel() == CameraChannel.EYE_HEIGHT).findFirst().orElseThrow();
        assertTrue(eye.absolute(), "the roll owns the drawn eye height");
        var designed = MoveCameras.rollDesignedEye(roll);
        assertEquals(1.62, designed.sample(1.0), 1.0e-9, "standing at the end of T0");
        assertEquals(1.44, designed.sample(2.0), 1.0e-9, "ease-in: a quarter of the way at the end of T1");
        for (int k = 2; k <= 6; k++) {
            assertEquals(0.9, designed.sample(k + 1.0), 1.0e-9, "tucked at the end of T" + k);
        }
        assertEquals(1.62, designed.sample(10.0), 1.0e-9, "standing by the end of T9");
        assertEquals(10.0 + MoveCameras.EYE_HOLD_TICKS, designed.endTime(), 1.0e-9, "held past the body standing");
    }

    /**
     * The roll through the real rig against a model of the host's camera: vanilla's eye eases halfway to the body's
     * eye each tick, held at the body's eye while the last frame held an absolute eye (the adapter's rule). The
     * player crouched for the sneak press, so the host camera starts mid-ease at 1.31375, as the gametest recorded.
     */
    @Test
    void rollDrawnEyeFollows02FromACrouchedStart() {
        RollTuning roll = Moveset.DEFAULT.roll();
        double[] drawn = new double[16];
        double[] offset = new double[16];
        boolean[] held = new boolean[16];
        simulate(MoveCameras.roll(roll), 1.31375, 1.27,
                k -> k < roll.ticks() ? roll.eyeHeight() : 1.62, drawn, offset, held);
        assertEquals(1.44, drawn[1], 1.0e-9, "T1");
        for (int k = 2; k <= 6; k++) {
            assertEquals(0.9, drawn[k], 1.0e-9, "T" + k + " stays tucked");
        }
        assertTrue(drawn[8] > 0.9 && drawn[8] < 1.62, "rising at T8");
        for (int k = 9; k < drawn.length; k++) {
            assertEquals(1.62, drawn[k], 1.0e-6, "standing at T" + k);
        }
        for (int k = 11; k < offset.length; k++) {
            assertEquals(0.0, offset[k], "no offset after T10 (T" + k + ")");
        }
        assertTrue(held[11] && !held[13], "the hold lets go once the body and camera stand");
    }

    @Test
    void slideDrawnEyeReaches05AtT3AndReturnsOver3Ticks() {
        SlideTuning slide = Moveset.DEFAULT.slide();
        double[] drawn = new double[12];
        double[] offset = new double[12];
        boolean[] held = new boolean[12];
        simulate(MoveCameras.slideEye(slide), 1.62, 1.62, k -> slide.eyeHeight(), drawn, offset, held);
        assertEquals(0.5, drawn[3], 1.0e-9, "eye 0.5 at T3");
        assertEquals(0.5, drawn[11], 1.0e-9, "held while sliding");

        double[] exitDrawn = new double[10];
        double[] exitOffset = new double[10];
        boolean[] exitHeld = new boolean[10];
        double from = MoveCameras.slideDesignedEye(slide).sample(12.0);
        simulate(MoveCameras.slideExit(slide, from, false), slide.eyeHeight(), slide.eyeHeight(), k -> 1.62,
                exitDrawn, exitOffset, exitHeld);
        assertTrue(exitDrawn[0] < 1.62, "still returning on the end tick");
        assertEquals(1.62, exitDrawn[slide.camera().eyeReturnTicks() - 1], 1.0e-9, "standing after 3 ticks");
        for (int k = slide.camera().eyeReturnTicks() + 1; k < exitOffset.length; k++) {
            assertEquals(0.0, exitOffset[k], "no offset once standing (k=" + k + ")");
            assertEquals(1.62, exitDrawn[k], 1.0e-6);
        }
    }

    /**
     * Plays {@code cue} from move tick 0 as the client does (started one rig tick back, so local time {@code k + 1}
     * is the end of {@code Tk}) and records, per tick, the drawn eye (host eye plus the rig's offset), the offset and
     * whether the frame held an absolute eye.
     */
    private static void simulate(CameraCue cue, double startCamera, double startBody, IntUnaryDouble bodyEye,
                                 double[] drawn, double[] offset, boolean[] held) {
        StandardCameraRig rig = new StandardCameraRig();
        double camera = startCamera;
        double body = startBody;
        boolean hold = false;
        double start = Double.NaN;
        for (int k = 0; k < drawn.length; k++) {
            rig.tick();
            camera += (body - camera) * 0.5;
            if (hold) {
                camera = body;
            }
            body = bodyEye.at(k);
            if (k == 0) {
                start = rig.now() - 1.0;
                rig.start(cue, start);
            }
            ViewInput view = new ViewInput(new Vec3(0.0, 64.0 + camera, 0.0), 0.0, 0.0, 70.0, camera);
            CameraState state = rig.evaluate(view, start + k + 1.0);
            offset[k] = state.layers().eyeHeight();
            drawn[k] = state.position().y() - 64.0;
            hold = !Double.isNaN(state.layers().eyeTarget());
            held[k] = hold;
        }
    }

    @FunctionalInterface
    private interface IntUnaryDouble {
        double at(int k);
    }

    @Test
    void wallKickLeansAwayAndHolds() {
        CameraCue cue = MoveCameras.wallKick(Moveset.DEFAULT.wallJump().camera(), -1.0);
        var roll = curve(cue, CameraChannel.ROLL, false).track();
        assertEquals(-7.0, roll.sample(3.0), 1.0e-9);
        assertEquals(-7.0, roll.sample(5.0), 1.0e-9);
        assertEquals(0.0, roll.sample(11.0), 1.0e-9);
    }
}
