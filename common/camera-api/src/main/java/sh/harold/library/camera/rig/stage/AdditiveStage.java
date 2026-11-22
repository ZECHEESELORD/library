package sh.harold.library.camera.rig.stage;

/**
 * Stage 5: the additive layers (move cues, signal-driven channels, the shake bus, dutch). It only samples; stage 7
 * applies the intensity profile and the limits, and the pipeline composes the result onto the shot.
 */
@FunctionalInterface
public interface AdditiveStage {

    LayerSample sample(RigFrame frame);

    AdditiveStage NONE = frame -> LayerSample.EMPTY;
}
