package sh.harold.creative.library.statemachine;

import java.time.Duration;
import java.util.Objects;

public sealed interface TimerCommand<E> permits TimerCommand.Schedule, TimerCommand.Cancel {

    static <E> TimerCommand<E> schedule(TimerKey key, Duration delay, E event) {
        return new Schedule<>(key, delay, event);
    }

    static <E> TimerCommand<E> cancel(TimerKey key) {
        return new Cancel<>(key);
    }

    record Schedule<E>(TimerKey key, Duration delay, E event) implements TimerCommand<E> {

        public Schedule {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(delay, "delay");
            Objects.requireNonNull(event, "event");
            if (delay.isNegative()) {
                throw new IllegalArgumentException("delay cannot be negative");
            }
        }
    }

    record Cancel<E>(TimerKey key) implements TimerCommand<E> {

        public Cancel {
            Objects.requireNonNull(key, "key");
        }
    }
}
