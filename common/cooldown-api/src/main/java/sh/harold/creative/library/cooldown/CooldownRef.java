package sh.harold.creative.library.cooldown;

import java.util.Objects;

/**
 * Generic reference used to identify the subject or context of a cooldown.
 *
 * @param kind logical kind of reference, such as {@code player} or {@code entity}
 * @param id stable identifier for that reference
 */
public record CooldownRef(String kind, String id) {

    public CooldownRef {
        kind = sanitize(kind, "kind");
        id = sanitize(id, "id");
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
