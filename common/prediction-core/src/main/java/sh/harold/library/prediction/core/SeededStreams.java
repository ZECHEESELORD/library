package sh.harold.library.prediction.core;

import sh.harold.library.prediction.DeterministicRandom;
import sh.harold.library.prediction.StreamKey;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Splittable seeded streams keyed by {@code (seed, action index, purpose)} (architecture section 5,
 * "Deterministic random streams").
 *
 * <p>The algorithm is fixed so other tools (the game's Python sims) can reproduce it:
 * <ol>
 *   <li>{@code p = FNV-1a 64} of the purpose's UTF-8 bytes;</li>
 *   <li>{@code state = mix(mix(mix(seed) ^ actionIndex) ^ p)}, where {@code mix} is the SplitMix64 finaliser
 *       applied after adding the golden gamma;</li>
 *   <li>each draw is one SplitMix64 step: {@code state += 0x9E3779B97F4A7C15}, output the finaliser of
 *       {@code state}.</li>
 * </ol>
 * {@code nextInt(bound)} rejects to avoid modulo bias, and {@code nextDouble} uses the top 53 bits.
 */
public final class SeededStreams {

    static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long FNV_OFFSET = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;

    private SeededStreams() {
    }

    public static DeterministicRandom open(StreamKey key) {
        Objects.requireNonNull(key, "key");
        long state = mix(mix(mix(key.seed()) ^ key.actionIndex()) ^ fnv1a64(key.purpose()));
        return new SplitMixStream(key, state);
    }

    public static DeterministicRandom open(long seed, long actionIndex, String purpose) {
        return open(new StreamKey(seed, actionIndex, purpose));
    }

    static long fnv1a64(String text) {
        long hash = FNV_OFFSET;
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xFF);
            hash *= FNV_PRIME;
        }
        return hash;
    }

    static long mix(long z) {
        return finalise(z + GOLDEN_GAMMA);
    }

    static long finalise(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static final class SplitMixStream implements DeterministicRandom {

        private final StreamKey key;
        private long state;

        private SplitMixStream(StreamKey key, long state) {
            this.key = key;
            this.state = state;
        }

        @Override
        public StreamKey key() {
            return key;
        }

        @Override
        public long nextLong() {
            state += GOLDEN_GAMMA;
            return finalise(state);
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("bound must be positive");
            }
            long limit = Long.MAX_VALUE - (Long.MAX_VALUE % bound);
            while (true) {
                long candidate = nextLong() >>> 1;
                if (candidate < limit) {
                    return (int) (candidate % bound);
                }
            }
        }

        @Override
        public double nextDouble() {
            return (nextLong() >>> 11) * 0x1.0p-53;
        }

        @Override
        public boolean nextBoolean() {
            return nextLong() < 0L;
        }

        @Override
        public DeterministicRandom split(String purpose) {
            return open(key.child(purpose));
        }
    }
}
