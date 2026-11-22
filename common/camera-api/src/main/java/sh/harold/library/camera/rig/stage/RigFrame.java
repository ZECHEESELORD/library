package sh.harold.library.camera.rig.stage;

import sh.harold.library.camera.rig.IntensityProfile;
import sh.harold.library.camera.rig.ViewInput;

import java.util.Objects;

/**
 * One evaluation of the stack: the rig time of the frame ({@code tick + partial}, fractional), the host's view for the
 * frame and the intensity profile.
 */
public record RigFrame(double time, ViewInput input, IntensityProfile profile) {

    public RigFrame {
        if (!Double.isFinite(time)) {
            throw new IllegalArgumentException("time must be finite");
        }
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(profile, "profile");
    }

    public boolean reduced() {
        return profile == IntensityProfile.REDUCED;
    }
}
