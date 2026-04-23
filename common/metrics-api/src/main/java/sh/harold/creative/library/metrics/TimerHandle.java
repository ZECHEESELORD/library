package sh.harold.creative.library.metrics;

import java.time.Duration;
import java.util.function.Supplier;

public interface TimerHandle {

    void record(Duration duration, MetricLabels labels);

    void recordNanos(long nanos, MetricLabels labels);

    TimingScope startScope(MetricLabels labels);

    default void record(Duration duration) {
        record(duration, MetricLabels.empty());
    }

    default void recordNanos(long nanos) {
        recordNanos(nanos, MetricLabels.empty());
    }

    default TimingScope startScope() {
        return startScope(MetricLabels.empty());
    }

    default void observe(MetricLabels labels, Runnable action) {
        try (TimingScope ignored = startScope(labels)) {
            action.run();
        }
    }

    default <T> T observe(MetricLabels labels, Supplier<T> action) {
        try (TimingScope ignored = startScope(labels)) {
            return action.get();
        }
    }

    default void observe(Runnable action) {
        observe(MetricLabels.empty(), action);
    }

    default <T> T observe(Supplier<T> action) {
        return observe(MetricLabels.empty(), action);
    }
}
