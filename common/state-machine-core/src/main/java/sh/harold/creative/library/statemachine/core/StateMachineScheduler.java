package sh.harold.creative.library.statemachine.core;

import java.time.Duration;
import java.util.Objects;

@FunctionalInterface
public interface StateMachineScheduler {

    ScheduledTask schedule(Duration delay, Runnable action);

    static StateMachineScheduler unsupported() {
        return (delay, action) -> {
            Objects.requireNonNull(delay, "delay");
            Objects.requireNonNull(action, "action");
            throw new UnsupportedOperationException("Timers are not enabled for this runtime");
        };
    }
}
