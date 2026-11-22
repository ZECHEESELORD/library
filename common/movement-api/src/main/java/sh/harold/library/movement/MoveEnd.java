package sh.harold.library.movement;

import java.util.Objects;

/**
 * The client's notice that a claimed move ended (the {@code movement/end} payload).
 *
 * @param seq        the claim's sequence number
 * @param clientTick the client tick it ended on
 * @param reason     why
 */
public record MoveEnd(int seq, long clientTick, EndReason reason) {

    public MoveEnd {
        Objects.requireNonNull(reason, "reason");
    }

    public enum EndReason {
        /** The move ran its timeline. */
        COMPLETED,
        /** A rule of the move ended it early (slide too slow, sneak released, wall lost). */
        CONDITION,
        /** The player cancelled it (a climb let go). */
        CANCELLED,
        /** Another move took over (a slide-jump, a mantle kicked into a wall jump). */
        INTERRUPTED,
        /** Something blocked it (a collision). */
        BLOCKED,
        /** The body entered a state no move handles, or the service closed. */
        EXEMPT,
        /** The server rejected the claim. */
        REJECTED
    }
}
