package sh.harold.library.sound.core;

import sh.harold.library.sound.SoundTarget;

import java.util.Objects;

@FunctionalInterface
public interface SoundCueScheduler {

    ScheduledCueTask schedule(long delayTicks, Runnable action);

    default ScheduledCueTask schedule(SoundTarget target, long delayTicks, Runnable action) {
        Objects.requireNonNull(target, "target");
        return schedule(delayTicks, action);
    }

    /**
     * Schedules a target-bound action that may become impossible when the target retires.
     * The discard callback may run before this method returns and is not a cancellation callback.
     * Schedulers without target retirement semantics may keep the default behavior.
     */
    default ScheduledCueTask schedule(
            SoundTarget target,
            long delayTicks,
            Runnable action,
            Runnable onDiscard
    ) {
        Objects.requireNonNull(onDiscard, "onDiscard");
        return schedule(target, delayTicks, action);
    }

    static SoundCueScheduler unsupported() {
        return (delayTicks, action) -> {
            if (delayTicks < 0) {
                throw new IllegalArgumentException("delayTicks cannot be negative");
            }
            Objects.requireNonNull(action, "action");
            throw new UnsupportedOperationException("Tick scheduling is not enabled for this sound cue service");
        };
    }
}
