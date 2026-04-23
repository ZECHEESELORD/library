package sh.harold.creative.library.metrics;

import java.time.Duration;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public interface UnsafeTelemetry {

    void increment(CounterMetric metric, UnsafeMetricLabels labels);

    void add(CounterMetric metric, UnsafeMetricLabels labels, double amount);

    void set(GaugeMetric metric, UnsafeMetricLabels labels, double value);

    MetricRegistration registerGauge(GaugeMetric metric, UnsafeMetricLabels labels, DoubleSupplier supplier);

    MetricRegistration registerCounter(CounterMetric metric, UnsafeMetricLabels labels, DoubleSupplier supplier);

    void record(HistogramMetric metric, UnsafeMetricLabels labels, double value);

    void record(TimerMetric metric, UnsafeMetricLabels labels, Duration duration);

    void recordNanos(TimerMetric metric, UnsafeMetricLabels labels, long nanos);

    TimingScope startScope(TimerMetric metric, UnsafeMetricLabels labels);

    default void observe(TimerMetric metric, UnsafeMetricLabels labels, Runnable action) {
        try (TimingScope ignored = startScope(metric, labels)) {
            action.run();
        }
    }

    default <T> T observe(TimerMetric metric, UnsafeMetricLabels labels, Supplier<T> action) {
        try (TimingScope ignored = startScope(metric, labels)) {
            return action.get();
        }
    }
}
