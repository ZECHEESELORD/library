package sh.harold.creative.library.cooldown;

/**
 * Determines how the registry handles acquisition while a cooldown is active.
 */
public enum CooldownPolicy {
    REJECT_WHILE_ACTIVE,
    EXTEND_ON_ACQUIRE
}
