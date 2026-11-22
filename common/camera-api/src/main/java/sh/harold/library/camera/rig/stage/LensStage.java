package sh.harold.library.camera.rig.stage;

import sh.harold.library.camera.rig.CameraState;

/**
 * Stage 4: the lens when a shot owns it (absolute FOV, crash zoom, dolly zoom, focus). Wave 1 passes through; its
 * additive FOV lives in stage 5.
 */
@FunctionalInterface
public interface LensStage {

    CameraState apply(CameraState state, RigFrame frame);

    LensStage PASS_THROUGH = (state, frame) -> state;
}
