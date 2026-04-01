package sh.harold.creative.library.cooldown;

import java.util.Objects;
import java.util.UUID;

/**
 * Convenience factories for composing cooldown keys.
 */
public final class CooldownKeys {

    private static final String PLAYER_KIND = "player";

    private CooldownKeys() {
    }

    public static CooldownKey of(CooldownScope scope, String namespace, String name, CooldownRef subject, CooldownRef context) {
        return new CooldownKey(scope, namespace, name, subject, context);
    }

    public static CooldownKey of(CooldownScope scope, String namespace, String name, CooldownRef subject) {
        return of(scope, namespace, name, subject, null);
    }

    public static CooldownKey localPlayer(String namespace, String name, UUID playerId) {
        return playerScoped(CooldownScope.LOCAL, namespace, name, playerId);
    }

    public static CooldownKey localPlayer(String namespace, String name, UUID playerId, CooldownRef context) {
        return playerScoped(CooldownScope.LOCAL, namespace, name, playerId, context);
    }

    public static CooldownKey sharedServerPlayer(String namespace, String name, UUID playerId) {
        return playerScoped(CooldownScope.SHARED_SERVER, namespace, name, playerId);
    }

    public static CooldownKey sharedServerPlayer(String namespace, String name, UUID playerId, CooldownRef context) {
        return playerScoped(CooldownScope.SHARED_SERVER, namespace, name, playerId, context);
    }

    public static CooldownKey sharedNetworkPlayer(String namespace, String name, UUID playerId) {
        return playerScoped(CooldownScope.SHARED_NETWORK, namespace, name, playerId);
    }

    public static CooldownKey sharedNetworkPlayer(String namespace, String name, UUID playerId, CooldownRef context) {
        return playerScoped(CooldownScope.SHARED_NETWORK, namespace, name, playerId, context);
    }

    public static CooldownKey playerScoped(CooldownScope scope, String namespace, String name, UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return of(scope, namespace, name, CooldownRefs.of(PLAYER_KIND, playerId.toString()));
    }

    public static CooldownKey playerScoped(CooldownScope scope,
                                           String namespace,
                                           String name,
                                           UUID playerId,
                                           CooldownRef context) {
        Objects.requireNonNull(playerId, "playerId");
        return of(scope, namespace, name, CooldownRefs.of(PLAYER_KIND, playerId.toString()), context);
    }

    public static CooldownKey literalScoped(CooldownScope scope, String namespace, String name, String literalId) {
        return of(scope, namespace, name, CooldownRefs.literal(literalId));
    }

    public static CooldownKey entityScoped(CooldownScope scope, String namespace, String name, UUID entityId) {
        Objects.requireNonNull(entityId, "entityId");
        return of(scope, namespace, name, CooldownRefs.entity(entityId));
    }
}
