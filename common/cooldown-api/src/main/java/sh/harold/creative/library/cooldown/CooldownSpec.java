package sh.harold.creative.library.cooldown;

import java.time.Duration;
import java.util.Objects;

/**
 * Describes the duration and policy for a cooldown acquisition.
 *
 * @param window cooldown duration
 * @param policy handling for active cooldowns
 */
public record CooldownSpec(Duration window, CooldownPolicy policy) {

    public CooldownSpec {
        Objects.requireNonNull(window, "window");
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Cooldown window must be positive");
        }
        policy = Objects.requireNonNullElse(policy, CooldownPolicy.REJECT_WHILE_ACTIVE);
    }

    public static CooldownSpec rejecting(Duration window) {
        return new CooldownSpec(window, CooldownPolicy.REJECT_WHILE_ACTIVE);
    }

    public static CooldownSpec extending(Duration window) {
        return new CooldownSpec(window, CooldownPolicy.EXTEND_ON_ACQUIRE);
    }
}
