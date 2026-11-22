package sh.harold.library.camera.rig.stage;

import sh.harold.library.camera.rig.CameraState;

/**
 * Stage 2: places the camera in the anchor's frame (fixed offset, orbit, damped follow; then dolly, crane, rail).
 * Wave 1 passes through.
 */
@FunctionalInterface
public interface BodyStage {

    CameraState apply(CameraState state, RigFrame frame);

    BodyStage PASS_THROUGH = (state, frame) -> state;
}
