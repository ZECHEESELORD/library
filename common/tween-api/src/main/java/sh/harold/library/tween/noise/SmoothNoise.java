package sh.harold.library.tween.noise;

import sh.harold.library.tween.spring.SpringSpec;

/**
 * Seeded one-dimensional gradient noise as a pure function of clock time (catalog 1.3, R2). Each octave is
 * Perlin-style gradient noise with a quintic fade, so value and slope are continuous; it is zero on every lattice
 * point and stays within {@code [-amplitude, amplitude]}. A per-axis offset gives independent channels (x, y,
 * roll) from one spec.
 *
 * <p>The hash is SplitMix64 over {@code (seed, octave, axis, lattice index)}, so the signal is identical on every
 * JVM and can be reproduced outside Java.
 */
public final class SmoothNoise {

    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    private SmoothNoise() {
    }

    /** The noise at {@code seconds} of clock time on axis 0. */
    public static double sample(NoiseSpec spec, double seconds) {
        return sample(spec, 0, seconds);
    }

    /** The noise at {@code seconds} of clock time on {@code axis}; each axis is an independent signal. */
    public static double sample(NoiseSpec spec, int axis, double seconds) {
        if (!Double.isFinite(seconds)) {
            throw new IllegalArgumentException("seconds must be finite");
        }
        double sum = 0.0;
        for (int i = 0; i < spec.octaves().size(); i++) {
            NoiseOctave octave = spec.octaves().get(i);
            long octaveSeed = mix(spec.seed() + (GOLDEN * (i + 1)) + (0xD1B54A32D192ED03L * axis));
            sum += octave.amplitude() * gradientNoise(octaveSeed, seconds * octave.frequencyHz());
        }
        return sum;
    }

    /** The noise at {@code ticks} of clock time (fractional allowed) on {@code axis}. */
    public static double sampleTicks(NoiseSpec spec, int axis, double ticks) {
        return sample(spec, axis, ticks / SpringSpec.TICKS_PER_SECOND);
    }

    /** Unit gradient noise in {@code [-1, 1]} at lattice coordinate {@code x}. */
    public static double gradientNoise(long seed, double x) {
        double floor = Math.floor(x);
        long i0 = (long) floor;
        double f = x - floor;
        double g0 = gradient(seed, i0);
        double g1 = gradient(seed, i0 + 1L);
        double fade = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
        double value = (g0 * f) + (fade * ((g1 * (f - 1.0)) - (g0 * f)));
        // 1D gradient noise with gradients in [-1, 1] peaks at 0.5; scale to [-1, 1].
        return Math.max(-1.0, Math.min(1.0, value * 2.0));
    }

    private static double gradient(long seed, long lattice) {
        long h = mix(seed ^ mix(lattice * GOLDEN));
        return ((h >>> 11) * 0x1.0p-53 * 2.0) - 1.0;
    }

    static long mix(long z) {
        z += GOLDEN;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
