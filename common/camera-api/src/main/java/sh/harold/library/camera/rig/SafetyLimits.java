package sh.harold.library.camera.rig;

/**
 * Hard limits stage 7 applies in every profile. They bound what cues add; the anchor's own values (the player's FOV
 * setting, their look) are never clamped, so an idle rig is exactly vanilla.
 *
 * @param fovMin       smallest final FOV a cue may push to (architecture: 5)
 * @param fovMax       largest final FOV a cue may push to (architecture: 110)
 * @param maxFovAdd    largest absolute FOV offset
 * @param maxRoll      largest absolute roll offset in degrees
 * @param eyeHeightMin smallest eye height offset in blocks
 * @param eyeHeightMax largest eye height offset in blocks
 * @param maxShake     largest rotational shake amplitude in degrees
 * @param lookScaleMin smallest look-input scale
 * @param lookScaleMax largest look-input scale
 * @param maxPitch     largest absolute total pitch (the player's look plus what cues add) for a pitch curve that is
 *                     not unwound, in degrees; the player's own pitch is never moved
 * @param maxArmOffset largest absolute arm-pose translation per axis, in blocks
 * @param maxArmRotation largest absolute arm-pose rotation per axis, in degrees
 */
public record SafetyLimits(
        double fovMin,
        double fovMax,
        double maxFovAdd,
        double maxRoll,
        double eyeHeightMin,
        double eyeHeightMax,
        double maxShake,
        double lookScaleMin,
        double lookScaleMax,
        double maxPitch,
        double maxArmOffset,
        double maxArmRotation
) {

    public static final SafetyLimits DEFAULT = new SafetyLimits(5.0, 110.0, 40.0, 90.0, -1.5, 1.0, 6.0, 0.0, 1.0, 85.0,
            1.0, 180.0);

    /** Limits with total pitch at 90 and the default arm limits (the v13 shape). */
    public SafetyLimits(double fovMin, double fovMax, double maxFovAdd, double maxRoll, double eyeHeightMin,
                        double eyeHeightMax, double maxShake, double lookScaleMin, double lookScaleMax) {
        this(fovMin, fovMax, maxFovAdd, maxRoll, eyeHeightMin, eyeHeightMax, maxShake, lookScaleMin, lookScaleMax, 90.0,
                1.0, 180.0);
    }

    public SafetyLimits {
        if (!(fovMin > 0.0 && fovMin <= fovMax && fovMax < 180.0)) {
            throw new IllegalArgumentException("need 0 < fovMin <= fovMax < 180");
        }
        if (!(maxFovAdd >= 0.0 && maxRoll >= 0.0 && maxShake >= 0.0)) {
            throw new IllegalArgumentException("maxFovAdd, maxRoll and maxShake must be non-negative");
        }
        if (!(eyeHeightMin <= 0.0 && eyeHeightMax >= 0.0)) {
            throw new IllegalArgumentException("the eye height range must contain 0");
        }
        if (!(maxPitch > 0.0 && maxPitch <= 90.0 && maxArmOffset >= 0.0 && maxArmRotation >= 0.0)) {
            throw new IllegalArgumentException("maxPitch must be in (0, 90] and the arm limits non-negative");
        }
        if (!(lookScaleMin >= 0.0 && lookScaleMin <= 1.0 && lookScaleMax >= 1.0)) {
            throw new IllegalArgumentException("the look scale range must contain 1 and be non-negative");
        }
    }
}
