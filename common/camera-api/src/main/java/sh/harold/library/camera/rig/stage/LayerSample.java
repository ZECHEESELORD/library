package sh.harold.library.camera.rig.stage;

import net.kyori.adventure.key.Key;
import sh.harold.library.camera.rig.CameraChannel;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Stage 5's raw output, before stage 7. Each channel is split into the part the intensity profile may scale and the
 * part that is exempt because it came from a Reduced substitute (already authored for the profile).
 *
 * @param pitch        pitch offset, profile-scalable
 * @param pitchExempt  pitch offset from Reduced substitutes
 * @param roll         roll offset, profile-scalable
 * @param rollExempt   roll offset from Reduced substitutes
 * @param fov          FOV offset, profile-scalable
 * @param fovExempt    FOV offset from Reduced substitutes
 * @param eyeHeight    eye height offset
 * @param shake        rotational shake amplitude in degrees (cues plus the trauma bus)
 * @param lookScale    the product of every live look-scale factor
 * @param pitchUnwound whether a live pitch curve may pass straight up or down
 * @param activeChannels channels with a live contribution
 * @param activeCues   keys of the live cues, in start order
 */
public record LayerSample(
        double pitch,
        double pitchExempt,
        double roll,
        double rollExempt,
        double fov,
        double fovExempt,
        double eyeHeight,
        double shake,
        double lookScale,
        boolean pitchUnwound,
        Set<CameraChannel> activeChannels,
        List<Key> activeCues
) {

    public static final LayerSample EMPTY = new LayerSample(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, false,
            Set.of(), List.of());

    public LayerSample {
        activeChannels = Set.copyOf(Objects.requireNonNull(activeChannels, "activeChannels"));
        activeCues = List.copyOf(Objects.requireNonNull(activeCues, "activeCues"));
    }
}
