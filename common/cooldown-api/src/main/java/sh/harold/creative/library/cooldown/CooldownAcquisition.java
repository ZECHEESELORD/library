package sh.harold.creative.library.cooldown;

import java.time.Duration;
import java.util.Objects;

/**
 * Outcome of attempting to acquire a cooldown.
 */
public sealed interface CooldownAcquisition permits CooldownAcquisition.Accepted, CooldownAcquisition.Rejected {

    record Accepted(CooldownTicket ticket) implements CooldownAcquisition {
        public Accepted {
            Objects.requireNonNull(ticket, "ticket");
        }
    }

    record Rejected(Duration remaining) implements CooldownAcquisition {
        public Rejected {
            Objects.requireNonNull(remaining, "remaining");
            if (remaining.isNegative()) {
                throw new IllegalArgumentException("remaining must not be negative");
            }
        }
    }
}
