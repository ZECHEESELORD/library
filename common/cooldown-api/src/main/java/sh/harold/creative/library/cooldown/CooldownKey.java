package sh.harold.creative.library.cooldown;

import java.util.Objects;

/**
 * Identifies one logical cooldown slot.
 *
 * @param scope     intended sharing scope
 * @param namespace logical subsystem grouping
 * @param name      action name within the namespace
 * @param subject   primary actor being throttled
 * @param context   optional secondary reference
 */
public record CooldownKey(
        CooldownScope scope,
        String namespace,
        String name,
        CooldownRef subject,
        CooldownRef context
) {

    public CooldownKey {
        scope = Objects.requireNonNull(scope, "scope");
        namespace = sanitize(namespace, "namespace");
        name = sanitize(name, "name");
        subject = Objects.requireNonNull(subject, "subject");
    }

    private static String sanitize(String value, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return trimmed;
    }
}
