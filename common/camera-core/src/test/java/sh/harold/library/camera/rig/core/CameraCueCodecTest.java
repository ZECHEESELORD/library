package sh.harold.library.camera.rig.core;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import sh.harold.library.camera.rig.CameraChannel;
import sh.harold.library.camera.rig.IntensityProfile;
import sh.harold.library.camera.rig.ViewInput;
import sh.harold.library.camera.rig.cue.CameraCue;
import sh.harold.library.camera.rig.cue.ChannelCurve;
import sh.harold.library.camera.rig.cue.CueCurve;
import sh.harold.library.reload.DataDecodeException;
import sh.harold.library.reload.core.JsonReader;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraCueCodecTest {

    private static final String ROLL = """
            {
              "key": "movement:roll",
              "owner": "movement:moves",
              "release": { "ticks": 4, "easing": "quad_out" },
              "curves": [
                { "channel": "pitch", "unwound": true,
                  "track": { "keys": [ { "t": 0, "v": 0, "interp": "quad_in" },
                                       { "t": 5, "v": 180, "interp": "quad_out" },
                                       { "t": 9, "v": 360 } ] },
                  "reduced": { "pulse": { "amount": 25, "attack": 3, "hold": 1, "release": 5 } } },
                { "channel": "fov", "pulse": { "amount": 6, "attack": 3, "attack_easing": "quad_out", "release": 7 } },
                { "channel": "look_scale", "constant": { "amount": -0.8, "ticks": 10 } }
              ]
            }
            """;

    @Test
    void decodesTheDocumentedRoll() {
        CameraCue cue = CameraCueCodec.CUE.decode(JsonReader.parse(ROLL));
        assertEquals(Key.key("movement:roll"), cue.key());
        assertEquals(Key.key("movement:moves"), cue.owner());
        assertEquals(4.0, cue.releaseTicks());
        assertEquals(3, cue.curves().size());
        ChannelCurve pitch = cue.curves().getFirst();
        assertEquals(CameraChannel.PITCH, pitch.channel());
        assertTrue(pitch.unwound());
        assertInstanceOf(CueCurve.TrackCurve.class, pitch.curve());
        assertInstanceOf(CueCurve.Pulse.class, pitch.reduced().orElseThrow());

        StandardCameraRig rig = new StandardCameraRig();
        rig.start(cue, 0.0);
        assertEquals(360.0, rig.sample(ViewInput.fixture(), 9.0).layers().pitchAdd(), 1e-9);
        assertEquals(0.2, rig.sample(ViewInput.fixture(), 5.0).lookInputScale(), 1e-9);
        rig.setIntensity(IntensityProfile.REDUCED);
        assertEquals(25.0, rig.sample(ViewInput.fixture(), 3.5).layers().pitchAdd(), 1e-9);
    }

    @Test
    void decodesNamedCueMapsAndSprings() {
        Map<String, CameraCue> cues = CameraCueCodec.CUE_MAP.decode(JsonReader.parse("""
                {
                  "land": { "key": "movement:land", "curves": [
                    { "channel": "eye_height", "spring": { "frequency_hz": 3, "damping": 0.4, "velocity": -0.2 } } ] },
                  "slide": { "key": "movement:slide", "conflict": "refresh", "curves": [
                    { "channel": "fov", "pulse": { "amount": 4, "attack": 2, "hold": "held", "release": 6,
                      "release_easing": [0.25, 0.1, 0.25, 1.0] } } ] }
                }
                """));
        assertEquals(2, cues.size());
        assertInstanceOf(CueCurve.SpringKick.class, cues.get("land").curves().getFirst().curve());
        CueCurve.Pulse slide = (CueCurve.Pulse) cues.get("slide").curves().getFirst().curve();
        assertTrue(slide.held());
    }

    @Test
    void errorsNameThePathAndLine() {
        DataDecodeException unknownChannel = assertThrows(DataDecodeException.class, () -> CameraCueCodec.CUE.decode(
                JsonReader.parse("""
                        {
                          "key": "a:b",
                          "curves": [
                            { "channel": "zoom", "constant": { "amount": 1, "ticks": 2 } }
                          ]
                        }
                        """)));
        assertEquals("$.curves[0].channel", unknownChannel.path());
        assertEquals(4, unknownChannel.line());

        DataDecodeException wrongType = assertThrows(DataDecodeException.class, () -> CameraCueCodec.CUE.decode(
                JsonReader.parse("""
                        { "key": "a:b", "curves": [ { "channel": "fov", "pulse": { "amount": "six" } } ] }
                        """)));
        assertEquals("$.curves[0].pulse.amount", wrongType.path());

        DataDecodeException twoForms = assertThrows(DataDecodeException.class, () -> CameraCueCodec.CUE.decode(
                JsonReader.parse("""
                        { "key": "a:b", "curves": [ { "channel": "fov",
                          "pulse": { "amount": 1 }, "constant": { "amount": 1, "ticks": 1 } } ] }
                        """)));
        assertTrue(twoForms.reason().contains("pulse, constant"), twoForms.reason());

        DataDecodeException unwoundFov = assertThrows(DataDecodeException.class, () -> CameraCueCodec.CUE.decode(
                JsonReader.parse("""
                        { "key": "a:b", "curves": [ { "channel": "fov", "unwound": true,
                          "constant": { "amount": 1, "ticks": 1 } } ] }
                        """)));
        assertEquals("$.curves[0].unwound", unwoundFov.path());

        DataDecodeException badEasing = assertThrows(DataDecodeException.class, () -> CameraCueCodec.CUE.decode(
                JsonReader.parse("""
                        { "key": "a:b", "curves": [ { "channel": "fov",
                          "pulse": { "amount": 1, "attack_easing": "wobble" } } ] }
                        """)));
        assertTrue(badEasing.reason().contains("wobble"));

        DataDecodeException absoluteFov = assertThrows(DataDecodeException.class, () -> CameraCueCodec.CUE.decode(
                JsonReader.parse("""
                        { "key": "a:b", "curves": [ { "channel": "fov", "absolute": true,
                          "constant": { "amount": 1, "ticks": 1 } } ] }
                        """)));
        assertEquals("$.curves[0].absolute", absoluteFov.path());

        assertThrows(DataDecodeException.class, () -> CameraCueCodec.CUE.decode(
                JsonReader.parse("{ \"key\": \"Not A Key\", \"curves\": [] }")));
    }

    @Test
    void absoluteEyeHeightDecodes() {
        CameraCue cue = CameraCueCodec.CUE.decode(JsonReader.parse("""
                { "key": "a:tuck", "curves": [ { "channel": "eye_height", "absolute": true,
                  "constant": { "amount": 0.9, "ticks": 10 } } ] }
                """));
        assertTrue(cue.curves().get(0).absolute());
    }
}
