package sh.harold.library.movement.core;

import org.junit.jupiter.api.Test;
import sh.harold.library.camera.rig.CameraChannel;
import sh.harold.library.camera.rig.cue.CameraCue;
import sh.harold.library.camera.rig.cue.ChannelCurve;
import sh.harold.library.camera.rig.cue.CueCurve;
import sh.harold.library.movement.JumpPreset;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.SlideVariant;
import sh.harold.library.movement.core.camera.EyeCompensation;
import sh.harold.library.movement.core.camera.MoveCameras;
import sh.harold.library.movement.core.tuning.Moveset;
import sh.harold.library.movement.core.tuning.RollTuning;
import sh.harold.library.reload.DataDecodeException;

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
    void eyeCueCompensatesVanillasCameraLag() {
        RollTuning roll = Moveset.DEFAULT.roll();
        double[] body = MoveCameras.rollBodyEye(roll);
        var designed = MoveCameras.rollDesignedEye(roll);
        int settle = EyeCompensation.settleTicks(1.62, body, designed.endTime());
        double[] model = EyeCompensation.cameraModel(1.62, body, settle);
        var cue = EyeCompensation.cue(designed, model);
        for (int t = 0; t <= settle; t++) {
            assertEquals(designed.sample(t), cue.sample(t) + model[t], 1.0e-12, "t=" + t);
        }
        assertEquals(0.9, designed.sample(3.0), 1.0e-9, "eye at 0.9 by the end of T2");
        assertEquals(1.62, designed.sample(10.0), 1.0e-9, "standing by the end of T9");
        assertEquals(1.62, model[1], 1.0e-12, "vanilla has not seen the hitbox drop yet");
        assertTrue(model[2] < 1.62, "it sees it one tick later");
        assertEquals(0.0, cue.sample(settle), 2.0e-3, "the cue fades out once vanilla settles");
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
