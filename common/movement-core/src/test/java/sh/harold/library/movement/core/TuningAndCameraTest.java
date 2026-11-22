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
        assertTrue(eye.fromEye().isPresent(), "the tuck starts from the current eye");
        var designed = MoveCameras.rollDesignedEye(roll);
        assertEquals(1.62, designed.sample(1.0), 1.0e-9, "standing at the end of T0");
        assertEquals(1.44, designed.sample(2.0), 1.0e-9, "ease-in: a quarter of the way at the end of T1");
        for (int k = 2; k <= 6; k++) {
            assertEquals(0.9, designed.sample(k + 1.0), 1.0e-9, "tucked at the end of T" + k);
        }
        assertEquals(1.62, designed.sample(10.0), 1.0e-9, "standing by the end of T9");
        assertEquals(10.0 + MoveCameras.EYE_HOLD_TICKS, designed.endTime(), 1.0e-9, "held past the body standing");
        var crouched = MoveCameras.rollDesignedEye(roll, 1.445);
        assertEquals(1.445, crouched.sample(1.0), 1.0e-9, "from a crouch: held where the camera was through T0");
        assertEquals(0.9, crouched.sample(3.0), 1.0e-9, "and the same tuck by T2");
        for (double t = 3.0; t <= 12.0; t += 0.25) {
            assertEquals(designed.sample(t), crouched.sample(t), 1.0e-12, "T3 onward unchanged, t=" + t);
        }
    }

    /**
     * The roll through the real rig against a model of the host's camera: vanilla's eye eases halfway to the body's
     * eye each tick, held at the body's eye while the last frame held an absolute eye (the adapter's rule). The
     * sneak press crouched the player the tick before, so the last frame before the roll drew 1.445 (as the gametest
     * recorded) and vanilla is still easing down toward the crouch's 1.27.
     */
    @Test
    void rollDrawnEyeTucksFromACrouchedStartWithoutRising() {
        RollTuning roll = Moveset.DEFAULT.roll();
        Run run = simulate(MoveCameras.roll(roll), 1.62, 1.27, k -> k < roll.ticks() ? roll.eyeHeight() : 1.62, 16);
        assertEquals(1.445, run.before(), 1.0e-9, "the last frame before the roll");
        assertEquals(1.445, run.drawn()[0], 1.0e-9, "T0 starts where the camera was");
        assertEquals(1.445 - (1.445 - 0.9) * 0.25, run.drawn()[1], 1.0e-9, "T1: the ease-in from there");
        double previous = run.before();
        for (int k = 0; k <= 2; k++) {
            assertTrue(run.drawn()[k] <= previous + 1.0e-12, "no rise at T" + k);
            previous = run.drawn()[k];
        }
        for (int k = 2; k <= 6; k++) {
            assertEquals(0.9, run.drawn()[k], 1.0e-9, "T" + k + " stays tucked");
        }
        assertTrue(run.drawn()[8] > 0.9 && run.drawn()[8] < 1.62, "rising at T8");
        for (int k = 9; k < run.drawn().length; k++) {
            assertEquals(1.62, run.drawn()[k], 1.0e-6, "standing at T" + k);
        }
        for (int k = 11; k < run.offset().length; k++) {
            assertEquals(0.0, run.offset()[k], "no offset after T10 (T" + k + ")");
        }
        assertTrue(run.held()[11] && !run.held()[13], "the hold lets go once the body and camera stand");
    }

    @Test
    void rollFromStandingIs02Exactly() {
        RollTuning roll = Moveset.DEFAULT.roll();
        Run run = simulate(MoveCameras.roll(roll), 1.62, 1.62, k -> k < roll.ticks() ? roll.eyeHeight() : 1.62, 12);
        var designed = MoveCameras.rollDesignedEye(roll);
        for (int k = 0; k < run.drawn().length; k++) {
            assertEquals(designed.sample(k + 1.0), run.drawn()[k], 1.0e-9, "T" + k);
        }
    }

    @Test
    void slideDrawnEyeReaches05AtT3AndReturnsOver3Ticks() {
        SlideTuning slide = Moveset.DEFAULT.slide();
        Run run = simulate(MoveCameras.slideEye(slide), 1.62, 1.62, k -> slide.eyeHeight(), 12);
        assertEquals(1.62, run.before(), 1.0e-9);
        assertEquals(MoveCameras.slideDesignedEye(slide).sample(1.0), run.drawn()[0], 1.0e-9, "T0 eases out from 1.62");
        assertEquals(0.5, run.drawn()[3], 1.0e-9, "eye 0.5 at T3");
        assertEquals(0.5, run.drawn()[11], 1.0e-9, "held while sliding");

        Run crouched = simulate(MoveCameras.slideEye(slide), 1.62, 1.27, k -> slide.eyeHeight(), 6);
        assertTrue(crouched.drawn()[0] < crouched.before(), "from a crouching camera the slide still only falls");
        assertEquals(0.5, crouched.drawn()[3], 1.0e-9);

        Run exit = simulate(MoveCameras.slideExit(slide, false), slide.eyeHeight(), slide.eyeHeight(), k -> 1.62, 10);
        assertTrue(exit.drawn()[0] > exit.before() && exit.drawn()[0] < 1.62, "returning from where the camera was");
        assertEquals(1.62, exit.drawn()[slide.camera().eyeReturnTicks() - 1], 1.0e-9, "standing after 3 ticks");
        for (int k = slide.camera().eyeReturnTicks() + 1; k < exit.offset().length; k++) {
            assertEquals(0.0, exit.offset()[k], "no offset once standing (k=" + k + ")");
            assertEquals(1.62, exit.drawn()[k], 1.0e-6);
        }
    }

    /** One simulated move: the drawn eye of the last frame before it, then per move tick drawn, offset and hold. */
    private record Run(double before, double[] drawn, double[] offset, boolean[] held) {
    }

    /**
     * Draws one frame with no cue, then plays {@code cue} from move tick 0 as the client does (started one rig tick
     * back, so local time {@code k + 1} is the end of {@code Tk}) and records, per tick, the drawn eye (host eye plus
     * the rig's offset), the offset and whether the frame held an absolute eye.
     */
    private static Run simulate(CameraCue cue, double startCamera, double startBody, IntUnaryDouble bodyEye,
                                int ticks) {
        double[] drawn = new double[ticks];
        double[] offset = new double[ticks];
        boolean[] held = new boolean[ticks];
        StandardCameraRig rig = new StandardCameraRig();
        double camera = startCamera;
        double body = startBody;
        rig.tick();
        camera += (body - camera) * 0.5;
        double before = rig.evaluate(view(camera), rig.now() - 1.0).position().y() - 64.0;
        boolean hold = false;
        double start = Double.NaN;
        for (int k = 0; k < ticks; k++) {
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
            CameraState state = rig.evaluate(view(camera), start + k + 1.0);
            offset[k] = state.layers().eyeHeight();
            drawn[k] = state.position().y() - 64.0;
            hold = !Double.isNaN(state.layers().eyeTarget());
            held[k] = hold;
        }
        return new Run(before, drawn, offset, held);
    }

    private static ViewInput view(double eyeHeight) {
        return new ViewInput(new Vec3(0.0, 64.0 + eyeHeight, 0.0), 0.0, 0.0, 70.0, eyeHeight);
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
