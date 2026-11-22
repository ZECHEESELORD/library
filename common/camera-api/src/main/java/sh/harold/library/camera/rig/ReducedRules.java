package sh.harold.library.camera.rig;

/**
 * What the {@link IntensityProfile#REDUCED} profile does to cue output that has no Reduced substitute.
 *
 * <p>{@link #OVERWORLD} is 02's definition: FOV kicks halved, roll tilt removed, rotational shake off (03 turns
 * rotational shake off in Reduced, the strictest reading of "caps shake"). {@link #FIGHT} adds 03's FOV clamp of 50
 * to 80. A substitute curve declared on a cue is taken as authored for Reduced and is not scaled again; the shake cap
 * and the safety limits still apply to it.
 *
 * @param fovScale        factor on FOV offsets
 * @param rollScale       factor on roll offsets; {@code 0} also turns the roll axis of shake off
 * @param shakeCapDegrees the largest rotational shake amplitude
 * @param fovMin          the smallest final FOV a cue may push to (the anchor's own FOV is never clamped)
 * @param fovMax          the largest final FOV a cue may push to
 */
public record ReducedRules(double fovScale, double rollScale, double shakeCapDegrees, double fovMin, double fovMax) {

    public static final ReducedRules OVERWORLD = new ReducedRules(0.5, 0.0, 0.0, 1.0, 179.0);
    public static final ReducedRules FIGHT = new ReducedRules(0.5, 0.0, 0.0, 50.0, 80.0);

    public ReducedRules {
        requireNonNegative(fovScale, "fovScale");
        requireNonNegative(rollScale, "rollScale");
        requireNonNegative(shakeCapDegrees, "shakeCapDegrees");
        if (!(fovMin > 0.0 && fovMax < 180.0 && fovMin <= fovMax)) {
            throw new IllegalArgumentException("need 0 < fovMin <= fovMax < 180");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
