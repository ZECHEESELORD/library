package sh.harold.creative.library.cooldown.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.cooldown.CooldownAcquisition;
import sh.harold.creative.library.cooldown.CooldownKey;
import sh.harold.creative.library.cooldown.CooldownKeys;
import sh.harold.creative.library.cooldown.CooldownRef;
import sh.harold.creative.library.cooldown.CooldownRefs;
import sh.harold.creative.library.cooldown.CooldownSpec;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class InMemoryCooldownRegistryTest {

    private static final Duration SHORT = Duration.ofMillis(30);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-03-31T00:00:00Z"));
    private final InMemoryCooldownRegistry registry = new InMemoryCooldownRegistry(clock);

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @Test
    void acquireGrantsWhenIdle() {
        CooldownKey key = CooldownKeys.localPlayer("test", "demo", UUID.randomUUID());

        CooldownAcquisition result = registry.acquire(key, CooldownSpec.rejecting(SHORT));

        assertInstanceOf(CooldownAcquisition.Accepted.class, result);
        assertEquals(1, registry.trackedCount());
    }

    @Test
    void acquireRejectsWhileActive() {
        CooldownKey key = CooldownKeys.localPlayer("test", "demo", UUID.randomUUID());
        registry.acquire(key, CooldownSpec.rejecting(SHORT));

        CooldownAcquisition second = registry.acquire(key, CooldownSpec.rejecting(SHORT));

        CooldownAcquisition.Rejected rejected = assertInstanceOf(CooldownAcquisition.Rejected.class, second);
        assertFalse(rejected.remaining().isNegative());
    }

    @Test
    void clearAllowsReacquire() {
        CooldownKey key = CooldownKeys.localPlayer("test", "demo", UUID.randomUUID());
        registry.acquire(key, CooldownSpec.rejecting(SHORT));
        registry.clear(key);

        CooldownAcquisition result = registry.acquire(key, CooldownSpec.rejecting(SHORT));

        assertInstanceOf(CooldownAcquisition.Accepted.class, result);
    }

    @Test
    void extendPolicyResetsExpiry() {
        CooldownKey key = CooldownKeys.sharedServerPlayer("test", "demo", UUID.randomUUID());
        registry.acquire(key, CooldownSpec.extending(Duration.ofSeconds(10)));

        clock.advance(Duration.ofSeconds(6));
        assertEquals(Duration.ofSeconds(4), registry.remaining(key).orElseThrow());

        CooldownAcquisition result = registry.acquire(key, CooldownSpec.extending(Duration.ofSeconds(10)));

        assertInstanceOf(CooldownAcquisition.Accepted.class, result);
        assertEquals(Duration.ofSeconds(10), registry.remaining(key).orElseThrow());
    }

    @Test
    void expiryCleanupRemovesExpiredEntries() {
        CooldownKey key = CooldownKeys.sharedNetworkPlayer("test", "demo", UUID.randomUUID());
        registry.acquire(key, CooldownSpec.rejecting(Duration.ofSeconds(10)));

        clock.advance(Duration.ofSeconds(11));
        int removed = registry.drainExpired(Integer.MAX_VALUE);

        assertEquals(1, removed);
        assertEquals(0, registry.trackedCount());
        assertFalse(registry.remaining(key).isPresent());
    }

    @Test
    void namespacesKeepLocalKeysIndependent() {
        UUID playerId = UUID.randomUUID();
        CooldownKey pluginOne = CooldownKeys.localPlayer("plugin-one", "34", playerId);
        CooldownKey pluginTwo = CooldownKeys.localPlayer("plugin-two", "34", playerId);

        CooldownAcquisition first = registry.acquire(pluginOne, CooldownSpec.rejecting(SHORT));
        CooldownAcquisition second = registry.acquire(pluginTwo, CooldownSpec.rejecting(SHORT));

        assertInstanceOf(CooldownAcquisition.Accepted.class, first);
        assertInstanceOf(CooldownAcquisition.Accepted.class, second);
    }

    @Test
    void scopeSeparatesLocalAndSharedKeysWhileSharedKeysCollide() {
        UUID playerId = UUID.randomUUID();
        CooldownRef literalContext = CooldownRefs.literal("menu:shop");
        CooldownKey local = CooldownKeys.localPlayer("test", "demo", playerId, literalContext);
        CooldownKey shared = CooldownKeys.sharedServerPlayer("test", "demo", playerId, literalContext);
        CooldownKey sharedAgain = CooldownKeys.sharedServerPlayer("test", "demo", playerId, literalContext);

        CooldownAcquisition localFirst = registry.acquire(local, CooldownSpec.rejecting(SHORT));
        assertInstanceOf(CooldownAcquisition.Accepted.class, localFirst);

        CooldownAcquisition sharedFirst = registry.acquire(shared, CooldownSpec.rejecting(SHORT));
        assertInstanceOf(CooldownAcquisition.Accepted.class, sharedFirst);

        CooldownAcquisition sharedSecond = registry.acquire(sharedAgain, CooldownSpec.rejecting(SHORT));
        assertInstanceOf(CooldownAcquisition.Rejected.class, sharedSecond);
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
