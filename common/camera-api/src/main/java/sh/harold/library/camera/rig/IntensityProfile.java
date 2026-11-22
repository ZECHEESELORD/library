package sh.harold.library.camera.rig;

/**
 * The camera intensity setting (02, "Camera intensity"; 03, "Reduced camera intensity"). Stage 7 applies it.
 */
public enum IntensityProfile {
    /** Cues play as authored. The default. */
    FULL,
    /** {@link ReducedRules} apply: FOV kicks scaled, roll removed, rotational shake capped, Reduced substitutes used. */
    REDUCED
}
