package sh.harold.library.movement;

import net.kyori.adventure.key.Key;

import java.util.Objects;

/** One running move. Instances are copied for prediction snapshots, so {@link #copy()} must copy every field. */
public interface MoveInstance {

    Key key();

    /** The tick the move counts as {@code T0}. */
    long startTick();

    /** Runs one tick: writes this tick's modifiers into {@code out} and says whether the move goes on. */
    Step tick(MoveContext context, MoveOutput.Builder out);

    /** A short phase name for telemetry ({@code tuck}, {@code pull}, {@code decay}). */
    String phase();

    /** Whether, while this move is live, the exclusive move {@code other} may start and replace it. */
    default boolean allowsStart(Key other) {
        return false;
    }

    /** Adds this move's own evidence (ledge top, wall normal, fall) to the claim sent when it starts. */
    default MoveClaim claim(MoveClaim base) {
        return base;
    }

    MoveInstance copy();

    /** What a move tick decided. */
    sealed interface Step {

        Step CONTINUE = new Continue();

        static Step end(MoveEnd.EndReason reason) {
            return new End(reason);
        }

        /** Ends this move and starts {@code next} on the same tick (a climb cancelled into a wall kick). */
        static Step handoff(Key next) {
            return new Handoff(next);
        }

        record Continue() implements Step {
        }

        record End(MoveEnd.EndReason reason) implements Step {
            public End {
                Objects.requireNonNull(reason, "reason");
            }
        }

        record Handoff(Key next) implements Step {
            public Handoff {
                Objects.requireNonNull(next, "next");
            }
        }
    }
}
