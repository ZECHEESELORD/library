package sh.harold.library.camera.rig.stage;

import sh.harold.library.camera.rig.CameraState;

/**
 * Stage 6: camera squash, digital punch-in and pan, lens shift, letterbox ({@link
 * sh.harold.library.camera.rig.ProjectionState}). Wave 1 passes through.
 */
@FunctionalInterface
public interface ProjectionStage {

    CameraState apply(CameraState state, RigFrame frame);

    ProjectionStage PASS_THROUGH = (state, frame) -> state;
}
