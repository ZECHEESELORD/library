package sh.harold.library.tween.noise;

import java.util.List;
import java.util.Objects;

/**
 * Seeded multi-octave smooth noise (catalog 1.3, "Noise"). The same seed always gives the same signal.
 */
public record NoiseSpec(long seed, List<NoiseOctave> octaves) {

    public NoiseSpec {
        octaves = List.copyOf(Objects.requireNonNull(octaves, "octaves"));
        if (octaves.isEmpty()) {
            throw new IllegalArgumentException("noise needs at least one octave");
        }
    }

    public static NoiseSpec of(long seed, NoiseOctave... octaves) {
        return new NoiseSpec(seed, List.of(octaves));
    }

    /** The largest absolute value the noise can reach: the sum of octave amplitudes. */
    public double peakAmplitude() {
        double sum = 0.0;
        for (NoiseOctave octave : octaves) {
            sum += octave.amplitude();
        }
        return sum;
    }
}
