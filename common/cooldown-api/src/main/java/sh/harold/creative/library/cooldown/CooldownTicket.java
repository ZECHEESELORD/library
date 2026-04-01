package sh.harold.creative.library.cooldown;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents an accepted cooldown reservation.
 *
 * @param key canonical key that was reserved
 * @param expiresAt instant when the cooldown elapses
 */
public record CooldownTicket(CooldownKey key, Instant expiresAt) {

    public CooldownTicket {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
