package sh.harold.creative.library.sound.minestom;

import net.minestom.server.timer.Scheduler;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import sh.harold.creative.library.sound.core.ScheduledCueTask;
import sh.harold.creative.library.sound.core.SoundCueScheduler;

import java.util.Objects;

final class MinestomSoundCueScheduler implements SoundCueScheduler {

    private final Scheduler scheduler;

    MinestomSoundCueScheduler(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public ScheduledCueTask schedule(long delayTicks, Runnable action) {
        if (delayTicks < 0) {
            throw new IllegalArgumentException("delayTicks cannot be negative");
        }
        Task task = scheduler.scheduleTask(Objects.requireNonNull(action, "action"), toDelay(delayTicks), TaskSchedule.stop());
        return task::cancel;
    }

    private static TaskSchedule toDelay(long delayTicks) {
        if (delayTicks == 0L) {
            return TaskSchedule.immediate();
        }
        return TaskSchedule.tick(Math.toIntExact(delayTicks));
    }
}
