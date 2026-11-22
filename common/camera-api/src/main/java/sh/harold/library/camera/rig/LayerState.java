package sh.harold.library.camera.rig;

import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The additive layers after stage 7: what stages 5 and 7 together added on top of the anchor this frame. Telemetry
 * reads it ({@code camera.pitch_add}, {@code camera.fov_add}, and so on).
 *
 * @param pitchAdd       degrees of pitch added, unwound
 * @param roll           degrees of roll added (dutch included)
 * @param fovAdd         degrees of FOV added
 * @param eyeHeight      blocks of eye height added
 * @param shakeAmplitude rotational shake amplitude in degrees, after the caps
 * @param shakePitch     the shake's pitch offset this frame
 * @param shakeRoll      the shake's roll offset this frame
 * @param lookScale      the look-input scale
 * @param pitchUnwound   whether a live pitch curve is unwound (allowed past straight up or down)
 * @param activeChannels channels with a live contribution
 * @param activeCues     keys of the live cues, in start order
 */
public record LayerState(
        double pitchAdd,
        double roll,
        double fovAdd,
        double eyeHeight,
        double shakeAmplitude,
        double shakePitch,
        double shakeRoll,
        double lookScale,
        boolean pitchUnwound,
        Set<CameraChannel> activeChannels,
        List<Key> activeCues
) {

    public static final LayerState IDENTITY = new LayerState(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, false,
            Set.of(), List.of());

    public LayerState {
        activeChannels = Set.copyOf(Objects.requireNonNull(activeChannels, "activeChannels"));
        activeCues = List.copyOf(Objects.requireNonNull(activeCues, "activeCues"));
    }

    /** Whether every offset is at its identity, so the camera is exactly the anchor's. */
    public boolean isIdentity() {
        return pitchAdd == 0.0 && roll == 0.0 && fovAdd == 0.0 && eyeHeight == 0.0
                && shakePitch == 0.0 && shakeRoll == 0.0 && lookScale == 1.0;
    }

    public LayerState withShake(double pitch, double rollOffset) {
        return new LayerState(pitchAdd, roll, fovAdd, eyeHeight, shakeAmplitude, pitch, rollOffset, lookScale,
                pitchUnwound, activeChannels, activeCues);
    }
}
