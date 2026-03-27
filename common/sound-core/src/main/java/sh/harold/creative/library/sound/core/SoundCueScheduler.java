package sh.harold.creative.library.sound.core;

import java.util.Objects;

@FunctionalInterface
public interface SoundCueScheduler {

    ScheduledCueTask schedule(long delayTicks, Runnable action);

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
