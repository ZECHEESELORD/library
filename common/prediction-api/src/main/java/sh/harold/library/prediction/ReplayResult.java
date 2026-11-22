package sh.harold.library.prediction;

import java.util.Objects;

/**
 * What a reconciliation did.
 *
 * @param outcome         what happened
 * @param correctedTick   the tick the correction named
 * @param replayedTicks   how many ticks were re-simulated after it (0 unless {@link Outcome#REPLAYED})
 * @param predictedBefore the current predicted state before the correction
 * @param current         the current predicted state after it; feed {@code predictedBefore} and {@code current}
 *                        to an error smoother so the drawn state closes the gap instead of popping
 */
public record ReplayResult<S>(Outcome outcome, long correctedTick, long replayedTicks, S predictedBefore, S current) {

    public ReplayResult {
        Objects.requireNonNull(outcome, "outcome");
    }

    public boolean changed() {
        return outcome == Outcome.REPLAYED;
    }

    public enum Outcome {
        /** The stored prediction for that tick matched within tolerance; nothing changed. */
        CONFIRMED,
        /** The snapshot was restored, corrected, and the stored inputs replayed up to the current tick. */
        REPLAYED,
        /** The tick is older than the history ring; nothing changed. The caller decides whether to hard-reset. */
        TOO_OLD,
        /** The tick is ahead of the current predicted tick; nothing changed. */
        FUTURE
    }
}
