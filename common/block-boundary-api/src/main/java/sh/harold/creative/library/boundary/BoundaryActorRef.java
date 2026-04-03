package sh.harold.creative.library.boundary;

import java.util.Objects;
import java.util.UUID;

public record BoundaryActorRef(String kind, String id) {

    public BoundaryActorRef {
        kind = sanitize(kind, "kind");
        id = sanitize(id, "id");
    }

    public static BoundaryActorRef player(UUID playerId) {
        return new BoundaryActorRef("player", Objects.requireNonNull(playerId, "playerId").toString());
    }

    public static BoundaryActorRef literal(String kind, String id) {
        return new BoundaryActorRef(kind, id);
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
