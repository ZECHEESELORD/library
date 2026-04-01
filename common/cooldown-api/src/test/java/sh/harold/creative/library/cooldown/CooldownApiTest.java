package sh.harold.creative.library.cooldown;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CooldownApiTest {

    @Test
    void refsAndKeysRejectBlankValues() {
        assertThrows(NullPointerException.class, () -> new CooldownRef(null, "alpha"));
        assertThrows(IllegalArgumentException.class, () -> new CooldownRef(" ", "alpha"));
        assertThrows(IllegalArgumentException.class, () -> new CooldownRef("player", " "));
        assertThrows(NullPointerException.class, () -> new CooldownKey(null, "shops", "buy", CooldownRefs.literal("alpha"), null));
        assertThrows(IllegalArgumentException.class, () -> new CooldownKey(CooldownScope.LOCAL, " ", "buy", CooldownRefs.literal("alpha"), null));
        assertThrows(IllegalArgumentException.class, () -> new CooldownKey(CooldownScope.LOCAL, "shops", " ", CooldownRefs.literal("alpha"), null));
        assertThrows(NullPointerException.class, () -> new CooldownKey(CooldownScope.LOCAL, "shops", "buy", null, null));
    }

    @Test
    void helperFactoriesComposeExpectedScopesAndRefs() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000034");
        CooldownRef context = CooldownRefs.literal("confirm");

        CooldownKey local = CooldownKeys.localPlayer("plugin-one", "34", playerId);
        CooldownKey shared = CooldownKeys.sharedServerPlayer("network", "34", playerId, context);

        assertEquals(CooldownScope.LOCAL, local.scope());
        assertEquals("player", local.subject().kind());
        assertEquals(playerId.toString(), local.subject().id());
        assertNull(local.context());

        assertEquals(CooldownScope.SHARED_SERVER, shared.scope());
        assertEquals("network", shared.namespace());
        assertEquals("34", shared.name());
        assertEquals(context, shared.context());
    }

    @Test
    void specDefaultsPolicyAndRejectedResultRejectsNegativeRemaining() {
        CooldownSpec spec = new CooldownSpec(Duration.ofSeconds(5), null);

        assertEquals(CooldownPolicy.REJECT_WHILE_ACTIVE, spec.policy());
        assertThrows(IllegalArgumentException.class, () -> new CooldownAcquisition.Rejected(Duration.ofSeconds(-1)));
        assertEquals(Instant.EPOCH, new CooldownTicket(CooldownKeys.of(
                CooldownScope.LOCAL,
                "test",
                "demo",
                CooldownRefs.literal("subject")
        ), Instant.EPOCH).expiresAt());
    }
}
