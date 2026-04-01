package sh.harold.creative.library.cooldown;

import java.util.Objects;
import java.util.UUID;

/**
 * Convenience factories for common cooldown references.
 */
public final class CooldownRefs {

    private static final String LITERAL_KIND = "literal";
    private static final String PLAYER_KIND = "player";
    private static final String ENTITY_KIND = "entity";

    private CooldownRefs() {
    }

    public static CooldownRef of(String kind, String id) {
        return new CooldownRef(kind, id);
    }

    public static CooldownRef literal(String id) {
        return of(LITERAL_KIND, id);
    }

    public static CooldownRef player(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return of(PLAYER_KIND, playerId.toString());
    }

    public static CooldownRef entity(UUID entityId) {
        Objects.requireNonNull(entityId, "entityId");
        return of(ENTITY_KIND, entityId.toString());
    }
}
