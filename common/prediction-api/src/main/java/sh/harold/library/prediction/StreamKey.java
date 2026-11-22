package sh.harold.library.prediction;

import java.util.Objects;

/**
 * Names one deterministic random stream: a fight or session seed, the index of the action that draws from it,
 * and a purpose ({@code "d20"}, {@code "damage"}, {@code "crit_shake"}). The server and every client that know
 * the key draw the same numbers.
 */
public record StreamKey(long seed, long actionIndex, String purpose) {

    public StreamKey {
        Objects.requireNonNull(purpose, "purpose");
        if (purpose.isEmpty()) {
            throw new IllegalArgumentException("purpose cannot be empty");
        }
    }

    /** The key of a child stream: same seed and action, purpose {@code parent/child}. */
    public StreamKey child(String childPurpose) {
        Objects.requireNonNull(childPurpose, "childPurpose");
        if (childPurpose.isEmpty()) {
            throw new IllegalArgumentException("childPurpose cannot be empty");
        }
        return new StreamKey(seed, actionIndex, purpose + "/" + childPurpose);
    }
}
