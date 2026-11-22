package sh.harold.library.camera.rig.stage;

import sh.harold.library.camera.rig.CameraState;

/**
 * Stage 3: orients the camera with full yaw, pitch and roll (authored rotation, look-at, the composition solver).
 * Wave 1 passes through.
 */
@FunctionalInterface
public interface AimStage {

    CameraState apply(CameraState state, RigFrame frame);

    AimStage PASS_THROUGH = (state, frame) -> state;
}
