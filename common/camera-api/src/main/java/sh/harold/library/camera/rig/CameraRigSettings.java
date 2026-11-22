package sh.harold.library.camera.rig;

import sh.harold.library.tween.noise.NoiseOctave;
import sh.harold.library.tween.noise.NoiseSpec;

import java.util.Objects;

/**
 * Rig-wide tuning.
 *
 * @param shakeNoise         the seeded noise the shake channel drives; axis 0 is pitch, axis 1 is roll. It is
 *                           normalised by its peak amplitude, so the shake channel's value is the amplitude in degrees
 * @param traumaMaxDegrees   shake amplitude at trauma 1; amplitude is {@code traumaMaxDegrees * trauma^2}
 * @param traumaDecayPerTick trauma lost per tick, linearly
 * @param reduced            what {@link IntensityProfile#REDUCED} does
 * @param limits             the hard limits for every profile
 */
public record CameraRigSettings(
        NoiseSpec shakeNoise,
        double traumaMaxDegrees,
        double traumaDecayPerTick,
        ReducedRules reduced,
        SafetyLimits limits
) {

    /** 10 Hz plus a quieter 23 Hz octave: 02's slide shake is "0.15 degrees at 10 Hz". */
    public static final NoiseSpec DEFAULT_SHAKE_NOISE = NoiseSpec.of(0x5EEDCA3EL,
            new NoiseOctave(10.0, 1.0), new NoiseOctave(23.0, 0.35));

    public static final CameraRigSettings DEFAULT = new CameraRigSettings(
            DEFAULT_SHAKE_NOISE, 3.0, 0.05, ReducedRules.OVERWORLD, SafetyLimits.DEFAULT);

    public CameraRigSettings {
        Objects.requireNonNull(shakeNoise, "shakeNoise");
        Objects.requireNonNull(reduced, "reduced");
        Objects.requireNonNull(limits, "limits");
        if (!Double.isFinite(traumaMaxDegrees) || traumaMaxDegrees < 0.0) {
            throw new IllegalArgumentException("traumaMaxDegrees must be finite and non-negative");
        }
        if (!Double.isFinite(traumaDecayPerTick) || traumaDecayPerTick <= 0.0) {
            throw new IllegalArgumentException("traumaDecayPerTick must be finite and positive");
        }
    }

    public CameraRigSettings withReduced(ReducedRules rules) {
        return new CameraRigSettings(shakeNoise, traumaMaxDegrees, traumaDecayPerTick, rules, limits);
    }

    public CameraRigSettings withLimits(SafetyLimits newLimits) {
        return new CameraRigSettings(shakeNoise, traumaMaxDegrees, traumaDecayPerTick, reduced, newLimits);
    }
}
