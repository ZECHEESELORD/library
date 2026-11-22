package sh.harold.library.camera.rig;

import sh.harold.library.spatial.Vec3;

import java.util.Objects;

/**
 * What the host's own camera shows this frame before the rig runs: the interpolated eye position, the player's
 * interpolated view angles and the FOV the host computed (the player's setting with its own modifiers). Stage 1's eye
 * anchor starts from these, so an idle rig hands them back unchanged.
 */
public record ViewInput(Vec3 eyePosition, double yaw, double pitch, double fovDegrees) {

    public ViewInput {
        Objects.requireNonNull(eyePosition, "eyePosition");
        if (!Double.isFinite(yaw) || !Double.isFinite(pitch)) {
            throw new IllegalArgumentException("yaw and pitch must be finite");
        }
        if (!(fovDegrees > 0.0 && fovDegrees < 180.0)) {
            throw new IllegalArgumentException("fovDegrees must be in (0, 180)");
        }
    }

    /** A fixture input for headless sampling: standing at the origin with eye height 1.62, looking south, FOV 70. */
    public static ViewInput fixture() {
        return new ViewInput(new Vec3(0.0, 1.62, 0.0), 0.0, 0.0, 70.0);
    }
}
