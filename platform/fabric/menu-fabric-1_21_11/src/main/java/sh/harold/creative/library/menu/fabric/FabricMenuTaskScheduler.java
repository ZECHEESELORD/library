package sh.harold.creative.library.menu.fabric;

import sh.harold.creative.library.menu.core.MenuTickHandle;
import sh.harold.creative.library.menu.core.MenuTickScheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class FabricMenuTaskScheduler {

    private final AtomicLong nextTaskId = new AtomicLong();
    private final Map<Long, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private volatile long currentTick;

    MenuTickHandle scheduleNextTick(Runnable action) {
        return schedule(currentTick + 1L, 0L, action);
    }

    MenuTickScheduler intervalScheduler() {
        return (intervalTicks, action) -> {
            if (intervalTicks <= 0L) {
                throw new IllegalArgumentException("intervalTicks must be greater than zero");
            }
            return schedule(currentTick + intervalTicks, intervalTicks, action);
        };
    }

    void tick() {
        currentTick++;
        List<ScheduledTask> due = new ArrayList<>();
        for (ScheduledTask task : tasks.values()) {
            if (!task.cancelled && task.nextRunTick <= currentTick) {
                due.add(task);
            }
        }
        due.sort(Comparator.comparingLong((ScheduledTask task) -> task.nextRunTick).thenComparingLong(task -> task.id));
        for (ScheduledTask task : due) {
            if (task.cancelled || tasks.get(task.id) != task || task.nextRunTick > currentTick) {
                continue;
            }
            task.action.run();
            if (task.cancelled || tasks.get(task.id) != task) {
                tasks.remove(task.id, task);
                continue;
            }
            if (task.intervalTicks > 0L) {
                task.nextRunTick = currentTick + task.intervalTicks;
            } else {
                tasks.remove(task.id, task);
            }
        }
    }

    private MenuTickHandle schedule(long nextRunTick, long intervalTicks, Runnable action) {
        ScheduledTask task = new ScheduledTask(nextTaskId.incrementAndGet(), nextRunTick, intervalTicks,
                Objects.requireNonNull(action, "action"));
        tasks.put(task.id, task);
        return () -> {
            task.cancelled = true;
            tasks.remove(task.id, task);
        };
    }

    private static final class ScheduledTask {

        private final long id;
        private final long intervalTicks;
        private final Runnable action;
        private volatile long nextRunTick;
        private volatile boolean cancelled;

        private ScheduledTask(long id, long nextRunTick, long intervalTicks, Runnable action) {
            this.id = id;
            this.nextRunTick = nextRunTick;
            this.intervalTicks = intervalTicks;
            this.action = action;
        }
    }
}
