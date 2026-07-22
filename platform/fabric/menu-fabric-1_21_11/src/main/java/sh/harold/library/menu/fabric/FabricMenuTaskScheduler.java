package sh.harold.library.menu.fabric;

import sh.harold.library.menu.core.MenuTickHandle;
import sh.harold.library.menu.core.MenuTickScheduler;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

final class FabricMenuTaskScheduler {

    private long nextTaskId;
    private final Object stateLock = new Object();
    private final Map<Long, ScheduledTask> tasks = new HashMap<>();
    private final NavigableMap<Long, LinkedHashMap<Long, ScheduledTask>> tasksByTick = new TreeMap<>();
    private long currentTick;

    MenuTickHandle scheduleNextTick(Runnable action) {
        synchronized (stateLock) {
            return schedule(currentTick + 1L, 0L, action);
        }
    }

    MenuTickScheduler intervalScheduler() {
        return (intervalTicks, action) -> {
            if (intervalTicks <= 0L) {
                throw new IllegalArgumentException("intervalTicks must be greater than zero");
            }
            synchronized (stateLock) {
                return schedule(currentTick + intervalTicks, intervalTicks, action);
            }
        };
    }

    void tick() {
        synchronized (stateLock) {
            currentTick++;
        }

        ScheduledTask task;
        while ((task = pollDueTask()) != null) {
            boolean completed = false;
            try {
                task.action.run();
                completed = true;
            } catch (RuntimeException ignored) {
                // One player's reducer must not starve the rest of the due bucket.
            } finally {
                completeRun(task, completed);
            }
        }
    }

    private ScheduledTask pollDueTask() {
        synchronized (stateLock) {
            while (true) {
                Map.Entry<Long, LinkedHashMap<Long, ScheduledTask>> bucketEntry = tasksByTick.firstEntry();
                if (bucketEntry == null || bucketEntry.getKey() > currentTick) {
                    return null;
                }
                LinkedHashMap<Long, ScheduledTask> bucket = bucketEntry.getValue();
                Map.Entry<Long, ScheduledTask> taskEntry = bucket.entrySet().iterator().next();
                bucket.remove(taskEntry.getKey());
                if (bucket.isEmpty()) {
                    tasksByTick.pollFirstEntry();
                }
                ScheduledTask task = taskEntry.getValue();
                task.queued = false;
                if (!task.cancelled && tasks.get(task.id) == task) {
                    return task;
                }
            }
        }
    }

    private void completeRun(ScheduledTask task, boolean completed) {
        synchronized (stateLock) {
            if (!completed) {
                task.cancelled = true;
                tasks.remove(task.id, task);
            } else if (task.cancelled || tasks.get(task.id) != task) {
                tasks.remove(task.id, task);
            } else if (task.intervalTicks > 0L) {
                task.nextRunTick = currentTick + task.intervalTicks;
                enqueue(task);
            } else {
                tasks.remove(task.id);
            }
        }
    }

    private MenuTickHandle schedule(long nextRunTick, long intervalTicks, Runnable action) {
        synchronized (stateLock) {
            ScheduledTask task = new ScheduledTask(++nextTaskId, nextRunTick, intervalTicks,
                    Objects.requireNonNull(action, "action"));
            tasks.put(task.id, task);
            enqueue(task);
            return () -> cancel(task);
        }
    }

    private void enqueue(ScheduledTask task) {
        tasksByTick.computeIfAbsent(task.nextRunTick, ignored -> new LinkedHashMap<>()).put(task.id, task);
        task.queued = true;
    }

    private void cancel(ScheduledTask task) {
        synchronized (stateLock) {
            task.cancelled = true;
            tasks.remove(task.id, task);
            if (!task.queued) {
                return;
            }
            LinkedHashMap<Long, ScheduledTask> bucket = tasksByTick.get(task.nextRunTick);
            if (bucket != null) {
                bucket.remove(task.id);
                if (bucket.isEmpty()) {
                    tasksByTick.remove(task.nextRunTick);
                }
            }
            task.queued = false;
        }
    }

    private static final class ScheduledTask {

        private final long id;
        private final long intervalTicks;
        private final Runnable action;
        private long nextRunTick;
        private boolean queued;
        private boolean cancelled;

        private ScheduledTask(long id, long nextRunTick, long intervalTicks, Runnable action) {
            this.id = id;
            this.nextRunTick = nextRunTick;
            this.intervalTicks = intervalTicks;
            this.action = action;
        }
    }
}
