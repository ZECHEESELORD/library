package sh.harold.creative.library.sound.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import sh.harold.creative.library.sound.core.ScheduledCueTask;
import sh.harold.creative.library.sound.core.SoundCueScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

final class FabricServerSoundCueScheduler implements SoundCueScheduler, AutoCloseable {

    private final Object monitor = new Object();
    private final NavigableMap<Long, List<ScheduledTask>> tasksByTick = new TreeMap<>();

    private long currentTick;
    private boolean closed;

    FabricServerSoundCueScheduler() {
        ServerTickEvents.START_SERVER_TICK.register(server -> onTick());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> close());
    }

    @Override
    public ScheduledCueTask schedule(long delayTicks, Runnable action) {
        if (delayTicks < 0L) {
            throw new IllegalArgumentException("delayTicks cannot be negative");
        }
        ScheduledTask task = new ScheduledTask(Objects.requireNonNull(action, "action"));
        synchronized (monitor) {
            ensureOpenLocked();
            long dueTick = currentTick + delayTicks;
            tasksByTick.computeIfAbsent(dueTick, ignored -> new ArrayList<>()).add(task);
        }
        return task;
    }

    @Override
    public void close() {
        synchronized (monitor) {
            if (closed) {
                return;
            }
            closed = true;
            for (List<ScheduledTask> tasks : tasksByTick.values()) {
                for (ScheduledTask task : tasks) {
                    task.cancel();
                }
            }
            tasksByTick.clear();
        }
    }

    private void onTick() {
        List<ScheduledTask> due = new ArrayList<>();
        synchronized (monitor) {
            if (closed) {
                return;
            }
            currentTick++;
            var iterator = tasksByTick.headMap(currentTick, true).entrySet().iterator();
            while (iterator.hasNext()) {
                due.addAll(iterator.next().getValue());
                iterator.remove();
            }
        }
        for (ScheduledTask task : due) {
            task.fire();
        }
    }

    private void ensureOpenLocked() {
        if (closed) {
            throw new IllegalStateException("Sound cue scheduler is closed");
        }
    }

    private static final class ScheduledTask implements ScheduledCueTask {

        private final Runnable action;

        private boolean cancelled;
        private boolean fired;

        private ScheduledTask(Runnable action) {
            this.action = action;
        }

        @Override
        public synchronized void cancel() {
            cancelled = true;
        }

        private synchronized void fire() {
            if (cancelled || fired) {
                return;
            }
            fired = true;
            action.run();
        }
    }
}
