package sh.harold.creative.library.metrics;

import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public interface Telemetry extends AutoCloseable {

    CounterHandle counter(CounterMetric metric);

    SettableGauge gauge(GaugeMetric metric);

    MetricRegistration registerGauge(GaugeMetric metric, MetricLabels labels, DoubleSupplier supplier);

    MetricRegistration registerCounter(CounterMetric metric, MetricLabels labels, DoubleSupplier supplier);

    HistogramHandle histogram(HistogramMetric metric);

    TimerHandle timer(TimerMetric metric);

    UnsafeTelemetry unsafe();

    @Override
    default void close() {
    }

    default void observe(TimerMetric metric, MetricLabels labels, Runnable action) {
        timer(metric).observe(labels, action);
    }

    default <T> T observe(TimerMetric metric, MetricLabels labels, Supplier<T> action) {
        return timer(metric).observe(labels, action);
    }

    default void observe(TimerMetric metric, Runnable action) {
        timer(metric).observe(action);
    }

    default <T> T observe(TimerMetric metric, Supplier<T> action) {
        return timer(metric).observe(action);
    }

    static Telemetry noop() {
        return NoOpTelemetry.INSTANCE;
    }

    final class NoOpTelemetry implements Telemetry {

        private static final NoOpTelemetry INSTANCE = new NoOpTelemetry();
        private static final CounterHandle NO_OP_COUNTER = new CounterHandle() {
            @Override
            public void increment(MetricLabels labels) {
            }

            @Override
            public void add(double amount, MetricLabels labels) {
            }
        };
        private static final SettableGauge NO_OP_GAUGE = new SettableGauge() {
            @Override
            public void set(double value, MetricLabels labels) {
            }
        };
        private static final HistogramHandle NO_OP_HISTOGRAM = new HistogramHandle() {
            @Override
            public void record(double value, MetricLabels labels) {
            }
        };
        private static final TimerHandle NO_OP_TIMER = new TimerHandle() {
            @Override
            public void record(java.time.Duration duration, MetricLabels labels) {
            }

            @Override
            public void recordNanos(long nanos, MetricLabels labels) {
            }

            @Override
            public TimingScope startScope(MetricLabels labels) {
                return new TimingScope() {
                    @Override
                    public java.time.Duration elapsed() {
                        return java.time.Duration.ZERO;
                    }

                    @Override
                    public boolean stopped() {
                        return true;
                    }

                    @Override
                    public void stop() {
                    }
                };
            }
        };
        private static final UnsafeTelemetry NO_OP_UNSAFE = new UnsafeTelemetry() {
            @Override
            public void increment(CounterMetric metric, UnsafeMetricLabels labels) {
            }

            @Override
            public void add(CounterMetric metric, UnsafeMetricLabels labels, double amount) {
            }

            @Override
            public void set(GaugeMetric metric, UnsafeMetricLabels labels, double value) {
            }

            @Override
            public MetricRegistration registerGauge(GaugeMetric metric, UnsafeMetricLabels labels, DoubleSupplier supplier) {
                return MetricRegistration.noop();
            }

            @Override
            public MetricRegistration registerCounter(CounterMetric metric, UnsafeMetricLabels labels, DoubleSupplier supplier) {
                return MetricRegistration.noop();
            }

            @Override
            public void record(HistogramMetric metric, UnsafeMetricLabels labels, double value) {
            }

            @Override
            public void record(TimerMetric metric, UnsafeMetricLabels labels, java.time.Duration duration) {
            }

            @Override
            public void recordNanos(TimerMetric metric, UnsafeMetricLabels labels, long nanos) {
            }

            @Override
            public TimingScope startScope(TimerMetric metric, UnsafeMetricLabels labels) {
                return NO_OP_TIMER.startScope();
            }
        };

        private NoOpTelemetry() {
        }

        @Override
        public CounterHandle counter(CounterMetric metric) {
            return NO_OP_COUNTER;
        }

        @Override
        public SettableGauge gauge(GaugeMetric metric) {
            return NO_OP_GAUGE;
        }

        @Override
        public MetricRegistration registerGauge(GaugeMetric metric, MetricLabels labels, DoubleSupplier supplier) {
            return MetricRegistration.noop();
        }

        @Override
        public MetricRegistration registerCounter(CounterMetric metric, MetricLabels labels, DoubleSupplier supplier) {
            return MetricRegistration.noop();
        }

        @Override
        public HistogramHandle histogram(HistogramMetric metric) {
            return NO_OP_HISTOGRAM;
        }

        @Override
        public TimerHandle timer(TimerMetric metric) {
            return NO_OP_TIMER;
        }

        @Override
        public UnsafeTelemetry unsafe() {
            return NO_OP_UNSAFE;
        }
    }
}
