package sh.harold.library.menu.fabric;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricMenuDeathTest {

    @Test
    void repeatedAllowDeathCallbacksStartOnlyOneSettlementAttempt() {
        FabricMenuSession.DeathAttempt attempts = new FabricMenuSession.DeathAttempt();
        AtomicInteger settlements = new AtomicInteger();

        long first = attempts.begin();
        if (first != 0L) {
            settlements.incrementAndGet();
        }
        long duplicate = attempts.begin();
        if (duplicate != 0L) {
            settlements.incrementAndGet();
        }

        assertNotEquals(0L, first);
        assertEquals(0L, duplicate);
        assertEquals(1, settlements.get());
    }

    @Test
    void cancelledDeathReconcilesTheDirtyViewExactlyOnce() {
        FabricMenuSession.DeathAttempt attempts = new FabricMenuSession.DeathAttempt();
        FabricMenuSession.SettledCustodyView settledView =
                new FabricMenuSession.SettledCustodyView();
        settledView.markDirty();
        AtomicInteger renders = new AtomicInteger();
        long token = attempts.begin();

        FabricMenuTaskScheduler scheduler = new FabricMenuTaskScheduler();
        scheduler.scheduleNextTick(() -> {
            if (attempts.consume(token)) {
                settledView.restore(() -> {
                    renders.incrementAndGet();
                    return true;
                });
            }
        });
        assertEquals(0, renders.get());
        scheduler.tick();

        assertEquals(1, renders.get());
        assertFalse(attempts.consume(token));
        assertTrue(settledView.restore(() -> {
            renders.incrementAndGet();
            return true;
        }));
        assertEquals(1, renders.get());
    }

    @Test
    void confirmedDeathRetiresThePendingReconciliation() {
        FabricMenuSession.DeathAttempt attempts = new FabricMenuSession.DeathAttempt();
        long token = attempts.begin();
        AtomicInteger reconciliations = new AtomicInteger();
        FabricMenuTaskScheduler scheduler = new FabricMenuTaskScheduler();
        scheduler.scheduleNextTick(() -> {
            if (attempts.consume(token)) {
                reconciliations.incrementAndGet();
            }
        });

        attempts.retire();
        scheduler.tick();

        assertFalse(attempts.consume(token));
        assertEquals(0, reconciliations.get());
        assertNotEquals(0L, attempts.begin());
    }

    @Test
    void reducerFailureQuarantinesWithoutRetryingSettlement() {
        AtomicInteger settlements = new AtomicInteger();
        AtomicInteger quarantines = new AtomicInteger();

        assertFalse(FabricMenuRuntime.settleCustodyBeforeDeath(
                () -> {
                    settlements.incrementAndGet();
                    return false;
                },
                quarantines::incrementAndGet));

        assertEquals(1, settlements.get());
        assertEquals(1, quarantines.get());
    }

    @Test
    void successfulPreDeathSettlementDoesNotQuarantine() {
        AtomicInteger quarantines = new AtomicInteger();

        assertTrue(FabricMenuRuntime.settleCustodyBeforeDeath(
                () -> true,
                quarantines::incrementAndGet));

        assertEquals(0, quarantines.get());
    }
}
