package sh.harold.library.movement;

import net.kyori.adventure.key.Key;

import java.util.Objects;

/** Something a move did this tick that the host must act on or report. */
public sealed interface MoveEvent {

    /** A claimable move started on this tick. The host assigns {@link MoveClaim#seq()} and sends the claim. */
    record Started(MoveClaim claim) implements MoveEvent {
        public Started {
            Objects.requireNonNull(claim, "claim");
        }

        public Key move() {
            return claim.move();
        }
    }

    /** A move ended on this tick. The host sends {@code movement/end} for the claim it sent at the start. */
    record Ended(Key move, long tick, MoveEnd.EndReason reason) implements MoveEvent {
        public Ended {
            Objects.requireNonNull(move, "move");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * Fall damage for the coming landing is worked out as if the fall were {@code blocks} shorter. On the client this
     * is a prediction; only the server, after accepting the move's claim, applies it.
     */
    record FallDamageModifier(double blocks) implements MoveEvent {
    }

    /** The fall measured for damage restarts from here (the end of a wall slide, a wall kick). */
    record FallDistanceReset() implements MoveEvent {
    }

    /** A cosmetic particle burst the host draws (wall-slide dust at the feet). */
    record Particles(String kind, sh.harold.library.spatial.Vec3 at) implements MoveEvent {
        public Particles {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(at, "at");
        }
    }
}
