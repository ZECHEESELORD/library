package sh.harold.library.movement;

import net.kyori.adventure.key.Key;
import sh.harold.library.spatial.Vec3;

import java.util.Objects;

/**
 * The client's claim that it started a move at {@code clientTick} (the {@code movement/claim} payload). The server
 * re-checks the move's preconditions against its own state with a tolerance and answers with a {@link MoveVerdict}.
 *
 * @param seq             per-connection sequence number, assigned by the client host
 * @param move            the move's key
 * @param clientTick      the client tick the move started on ({@code T0})
 * @param position        feet position at {@code T0}, before the move acted
 * @param velocity        velocity at {@code T0}, before the move acted
 * @param normal          for a wall jump or wall slide, the wall's outward normal; otherwise {@link Vec3#ZERO}. For a
 *                        mantle, the horizontal direction of the ledge
 * @param ledgeTop        for a mantle, the ledge top's height; otherwise {@code NaN}
 * @param fallDistance    for a roll, the fall the client measured; otherwise 0
 * @param pressTickOffset when the triggering press happened, in ticks relative to {@code clientTick} (negative is
 *                        earlier): the roll's sneak press, the slide's chord partner
 */
public record MoveClaim(
        int seq,
        Key move,
        long clientTick,
        Vec3 position,
        Vec3 velocity,
        Vec3 normal,
        double ledgeTop,
        double fallDistance,
        int pressTickOffset
) {

    public MoveClaim {
        Objects.requireNonNull(move, "move");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(velocity, "velocity");
        Objects.requireNonNull(normal, "normal");
    }

    /** A claim with no move-specific parameters. */
    public static MoveClaim of(Key move, long clientTick, Vec3 position, Vec3 velocity) {
        return new MoveClaim(0, move, clientTick, position, velocity, Vec3.ZERO, Double.NaN, 0.0, 0);
    }

    public MoveClaim withSeq(int value) {
        return new MoveClaim(value, move, clientTick, position, velocity, normal, ledgeTop, fallDistance,
                pressTickOffset);
    }

    public MoveClaim withNormal(Vec3 value) {
        return new MoveClaim(seq, move, clientTick, position, velocity, value, ledgeTop, fallDistance,
                pressTickOffset);
    }

    public MoveClaim withLedgeTop(double value) {
        return new MoveClaim(seq, move, clientTick, position, velocity, normal, value, fallDistance,
                pressTickOffset);
    }

    public MoveClaim withFallDistance(double value) {
        return new MoveClaim(seq, move, clientTick, position, velocity, normal, ledgeTop, value, pressTickOffset);
    }

    public MoveClaim withPressTickOffset(int value) {
        return new MoveClaim(seq, move, clientTick, position, velocity, normal, ledgeTop, fallDistance, value);
    }
}
