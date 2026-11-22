package sh.harold.library.camera.rig;

import sh.harold.library.spatial.Quaternion;
import sh.harold.library.spatial.Vec3;

import java.util.Objects;

/**
 * The one output of the rig stack for one rendered frame (architecture 7.3, catalog 2.1). Wave 1 fills position,
 * orientation, FOV, eye height and look scale from stages 1, 5 and 7; the lens, focus and projection fields are part
 * of the type now so later stages slot in without a second composer.
 *
 * @param position        camera position in world space, eye height offset included
 * @param orientation     full orientation (yaw, pitch and roll) as a unit quaternion, in {@link ViewAngles}'
 *                        convention
 * @param angles          the unwound angles that built {@code orientation} (pitch may pass 90 during a turn)
 * @param fovDegrees      vertical field of view
 * @param eyeHeightOffset blocks added to the anchor's eye height, render only
 * @param focusDistance   focus distance in blocks for depth of field; {@code 0} means no focus pull
 * @param aperture        aperture for depth of field; {@code 0} means no depth of field
 * @param nearClip        near clip plane in blocks
 * @param projection      stage 6 projection and image changes
 * @param lookInputScale  factor on the player's look input (mouse turn) while this state is live
 * @param layers          the additive layers that produced this state
 */
public record CameraState(
        Vec3 position,
        Quaternion orientation,
        ViewAngles angles,
        double fovDegrees,
        double eyeHeightOffset,
        double focusDistance,
        double aperture,
        double nearClip,
        ProjectionState projection,
        double lookInputScale,
        LayerState layers
) {

    /** Vanilla's near plane ({@code Camera.PROJECTION_Z_NEAR}). */
    public static final double DEFAULT_NEAR_CLIP = 0.05;

    public CameraState {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(angles, "angles");
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(layers, "layers");
        if (!(fovDegrees > 0.0 && fovDegrees < 180.0)) {
            throw new IllegalArgumentException("fovDegrees must be in (0, 180)");
        }
        if (!Double.isFinite(eyeHeightOffset) || !Double.isFinite(lookInputScale) || lookInputScale < 0.0) {
            throw new IllegalArgumentException("eyeHeightOffset must be finite and lookInputScale non-negative");
        }
        if (!(focusDistance >= 0.0 && aperture >= 0.0 && nearClip > 0.0)) {
            throw new IllegalArgumentException("focusDistance and aperture must be non-negative, nearClip positive");
        }
    }

    /** A state at an anchor with every other field at identity. */
    public static CameraState anchored(Vec3 position, ViewAngles angles, double fovDegrees) {
        return new CameraState(position, angles.toQuaternion(), angles, fovDegrees, 0.0, 0.0, 0.0,
                DEFAULT_NEAR_CLIP, ProjectionState.IDENTITY, 1.0, LayerState.IDENTITY);
    }

    public CameraState withPosition(Vec3 newPosition) {
        return new CameraState(newPosition, orientation, angles, fovDegrees, eyeHeightOffset, focusDistance, aperture,
                nearClip, projection, lookInputScale, layers);
    }

    public CameraState withOrientation(Quaternion newOrientation, ViewAngles newAngles) {
        return new CameraState(position, newOrientation, newAngles, fovDegrees, eyeHeightOffset, focusDistance,
                aperture, nearClip, projection, lookInputScale, layers);
    }

    public CameraState withFov(double newFov) {
        return new CameraState(position, orientation, angles, newFov, eyeHeightOffset, focusDistance, aperture,
                nearClip, projection, lookInputScale, layers);
    }

    public CameraState withProjection(ProjectionState newProjection) {
        return new CameraState(position, orientation, angles, fovDegrees, eyeHeightOffset, focusDistance, aperture,
                nearClip, newProjection, lookInputScale, layers);
    }

    /** Records {@code newLayers} and its look scale without touching anything else (for identity layers). */
    public CameraState withLayerRecord(LayerState newLayers) {
        return new CameraState(position, orientation, angles, fovDegrees, eyeHeightOffset, focusDistance, aperture,
                nearClip, projection, newLayers.lookScale(), newLayers);
    }

    /** Applies the additive layers: local pitch then local roll after the current orientation, FOV, eye height. */
    public CameraState withLayers(LayerState newLayers, double newFov, double newEyeHeightOffset, Vec3 newPosition) {
        double pitchOffset = newLayers.pitchAdd() + newLayers.shakePitch();
        double rollOffset = newLayers.roll() + newLayers.shakeRoll();
        Quaternion rotated = orientation;
        if (pitchOffset != 0.0) {
            rotated = rotated.multiply(ViewAngles.pitchQuaternion(pitchOffset));
        }
        if (rollOffset != 0.0) {
            rotated = rotated.multiply(ViewAngles.rollQuaternion(rollOffset));
        }
        ViewAngles newAngles = new ViewAngles(angles.yaw(), angles.pitch() + pitchOffset, angles.roll() + rollOffset);
        return new CameraState(newPosition, rotated.normalize(), newAngles, newFov, newEyeHeightOffset, focusDistance,
                aperture, nearClip, projection, newLayers.lookScale(), newLayers);
    }
}
