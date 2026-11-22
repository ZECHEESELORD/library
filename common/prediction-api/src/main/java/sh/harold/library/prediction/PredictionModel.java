package sh.harold.library.prediction;

/**
 * One deterministic simulation step, shared by client and server (architecture principle 3). The client runs it
 * to predict, the server to decide, and a rewind runs it again to replay. It must be a pure function of its
 * arguments: no clocks, no host state, and randomness only from a {@link DeterministicRandom} carried in the
 * state or input.
 *
 * @param <I> the per-tick input (keys and press edges, an intent)
 * @param <S> the simulated state snapshot; treat it as immutable
 */
@FunctionalInterface
public interface PredictionModel<I, S> {

    /** The state at the end of {@code tick}, given the state at the end of the previous tick and this tick's input. */
    S step(S state, I input, long tick);
}
