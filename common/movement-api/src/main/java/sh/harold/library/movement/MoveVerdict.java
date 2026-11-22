package sh.harold.library.movement;

import sh.harold.library.spatial.Vec3;

import java.util.Objects;
import java.util.Optional;

/**
 * The server's answer to a claim (the {@code movement/verdict} payload).
 *
 * @param seq      the claim's sequence number
 * @param accepted whether the server accepted it
 * @param reason   a short machine-readable reason ({@code ok}, {@code no_wall}, {@code too_slow}, {@code desync}, ...)
 * @param position on a rejection, the server's feet position at the claim, when it knows a better one than the
 *                 client's own pre-move snapshot
 * @param velocity on a rejection, the server's velocity at the claim, with {@code position}
 */
public record MoveVerdict(int seq, boolean accepted, String reason, Optional<Vec3> position, Optional<Vec3> velocity) {

    public MoveVerdict {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(velocity, "velocity");
    }

    public static MoveVerdict accept(int seq) {
        return new MoveVerdict(seq, true, "ok", Optional.empty(), Optional.empty());
    }

    public static MoveVerdict reject(int seq, String reason) {
        return new MoveVerdict(seq, false, reason, Optional.empty(), Optional.empty());
    }

    public MoveVerdict withCorrection(Vec3 feet, Vec3 motion) {
        return new MoveVerdict(seq, accepted, reason, Optional.of(feet), Optional.of(motion));
    }
}
