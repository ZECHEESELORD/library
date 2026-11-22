package sh.harold.library.prediction;

/**
 * A seeded random stream. Its sequence depends only on its {@link StreamKey}, never on wall time, thread or
 * JVM, so client prediction and server authority roll the same dice. Not thread-safe.
 */
public interface DeterministicRandom {

    StreamKey key();

    long nextLong();

    /** Uniform in {@code [0, bound)}, without modulo bias. */
    int nextInt(int bound);

    /** Uniform in {@code [0, 1)} with 53 bits. */
    double nextDouble();

    boolean nextBoolean();

    /** A die roll in {@code [1, sides]}. */
    default int roll(int sides) {
        if (sides < 1) {
            throw new IllegalArgumentException("sides must be positive");
        }
        return nextInt(sides) + 1;
    }

    /**
     * An independent child stream keyed by {@code key().child(purpose)}. It depends only on the key, not on how
     * many numbers this stream has drawn, so splitting order never changes results.
     */
    DeterministicRandom split(String purpose);
}
