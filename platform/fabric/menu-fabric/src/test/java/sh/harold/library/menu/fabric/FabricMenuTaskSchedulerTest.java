package sh.harold.library.menu.fabric;

import org.junit.jupiter.api.Test;
import sh.harold.library.menu.core.MenuTickHandle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FabricMenuTaskSchedulerTest {

    @Test
    void runsOnlyDueBucketInInsertionOrder() {
        FabricMenuTaskScheduler scheduler = new FabricMenuTaskScheduler();
        AtomicInteger futureRuns = new AtomicInteger();
        for (int task = 0; task < 10_000; task++) {
            scheduler.intervalScheduler().schedule(10_000L, futureRuns::incrementAndGet);
        }
        List<Integer> order = new ArrayList<>();
        scheduler.scheduleNextTick(() -> order.add(1));
        scheduler.scheduleNextTick(() -> order.add(2));

        scheduler.tick();

        assertEquals(List.of(1, 2), order);
        assertEquals(0, futureRuns.get());
    }

    @Test
    void repeatsAtItsIntervalUntilCancelled() {
        FabricMenuTaskScheduler scheduler = new FabricMenuTaskScheduler();
        AtomicInteger runs = new AtomicInteger();
        MenuTickHandle repeating = scheduler.intervalScheduler().schedule(2L, runs::incrementAndGet);

        scheduler.tick();
        scheduler.tick();
        scheduler.tick();
        scheduler.tick();
        repeating.cancel();
        scheduler.tick();
        scheduler.tick();

        assertEquals(2, runs.get());
    }

    @Test
    void failingTaskDoesNotStrandTheRemainingBucket() {
        FabricMenuTaskScheduler scheduler = new FabricMenuTaskScheduler();
        AtomicInteger runs = new AtomicInteger();
        scheduler.scheduleNextTick(() -> {
            throw new IllegalStateException();
        });
        scheduler.scheduleNextTick(runs::incrementAndGet);

        scheduler.tick();

        assertEquals(1, runs.get());
    }

    @Test
    void failingRepeatingTaskIsRetiredAfterItsFirstFailure() {
        FabricMenuTaskScheduler scheduler = new FabricMenuTaskScheduler();
        AtomicInteger failingRuns = new AtomicInteger();
        AtomicInteger healthyRuns = new AtomicInteger();
        scheduler.intervalScheduler().schedule(1L, () -> {
            failingRuns.incrementAndGet();
            throw new IllegalStateException();
        });
        scheduler.scheduleNextTick(healthyRuns::incrementAndGet);

        scheduler.tick();
        scheduler.tick();

        assertEquals(1, failingRuns.get());
        assertEquals(1, healthyRuns.get());
    }
}
