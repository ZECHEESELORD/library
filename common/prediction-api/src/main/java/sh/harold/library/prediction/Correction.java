package sh.harold.library.prediction;

import java.util.Objects;

/**
 * An authoritative verdict: the state at the end of {@code tick} was {@code state}.
 */
public record Correction<S>(long tick, S state) {

    public Correction {
        Objects.requireNonNull(state, "state");
    }
}
