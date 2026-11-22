package sh.harold.library.camera.rig.stage;

import sh.harold.library.camera.rig.CameraState;
import sh.harold.library.camera.rig.LayerState;

/**
 * Stage 7: the intensity profile (Reduced), the safety limits, later the flash cap and collision pull-in. It runs in
 * two places: on stage 5's sample before the layers are composed (so Reduced scales the offsets, never the anchor),
 * and on the final state.
 */
public interface ConstraintStage {

    /**
     * Turns stage 5's sample into final layer offsets for a shot, the shake's pitch and roll offsets for this frame
     * included (they depend on the profile's caps).
     */
    LayerState constrainLayers(LayerSample sample, CameraState shot, RigFrame frame);

    /** A last pass over the composed state. */
    CameraState constrainState(CameraState state, RigFrame frame);
}
