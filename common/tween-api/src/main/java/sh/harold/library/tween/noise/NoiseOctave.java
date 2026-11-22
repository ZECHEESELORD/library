package sh.harold.library.tween.noise;

/** One octave of {@link SmoothNoise}: a frequency in hertz of clock time and a peak amplitude. */
public record NoiseOctave(double frequencyHz, double amplitude) {

    public NoiseOctave {
        if (!Double.isFinite(frequencyHz) || frequencyHz <= 0.0) {
            throw new IllegalArgumentException("frequencyHz must be finite and positive");
        }
        if (!Double.isFinite(amplitude) || amplitude < 0.0) {
            throw new IllegalArgumentException("amplitude must be finite and non-negative");
        }
    }
}
