package sh.harold.creative.library.metrics.core;

import sh.harold.creative.library.metrics.CounterHandle;
import sh.harold.creative.library.metrics.CounterMetric;
import sh.harold.creative.library.metrics.GaugeMetric;
import sh.harold.creative.library.metrics.HistogramHandle;
import sh.harold.creative.library.metrics.HistogramMetric;
import sh.harold.creative.library.metrics.LabelKey;
import sh.harold.creative.library.metrics.MetricDescriptor;
import sh.harold.creative.library.metrics.MetricLabels;
import sh.harold.creative.library.metrics.MetricRegistration;
import sh.harold.creative.library.metrics.SettableGauge;
import sh.harold.creative.library.metrics.Telemetry;
import sh.harold.creative.library.metrics.TimerHandle;
import sh.harold.creative.library.metrics.TimerMetric;
import sh.harold.creative.library.metrics.TimingScope;
import sh.harold.creative.library.metrics.UnsafeMetricLabels;
import sh.harold.creative.library.metrics.UnsafeTelemetry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.DoubleSupplier;

public final class StandardTelemetry implements Telemetry, MetricSnapshotSource {

    private final ConcurrentMap<CounterMetric, CounterState> counters = new ConcurrentHashMap<>();
    private final ConcurrentMap<GaugeMetric, GaugeState> gauges = new ConcurrentHashMap<>();
    private final ConcurrentMap<HistogramMetric, HistogramState> histograms = new ConcurrentHashMap<>();
    private final ConcurrentMap<TimerMetric, TimerState> timers = new ConcurrentHashMap<>();
    private final UnsafeTelemetry unsafeTelemetry = new StandardUnsafeTelemetry();
    private final AtomicBoolean closed = new AtomicBoolean();

    @Override
    public CounterHandle counter(CounterMetric metric) {
        Objects.requireNonNull(metric, "metric");
        ensureOpen();
        return counters.computeIfAbsent(metric, CounterState::new).handle();
    }

    @Override
    public SettableGauge gauge(GaugeMetric metric) {
        Objects.requireNonNull(metric, "metric");
        ensureOpen();
        return gauges.computeIfAbsent(metric, GaugeState::new).handle();
    }

    @Override
    public MetricRegistration registerGauge(GaugeMetric metric, MetricLabels labels, DoubleSupplier supplier) {
        Objects.requireNonNull(metric, "metric");
        ensureOpen();
        return gauges.computeIfAbsent(metric, GaugeState::new).registerObserved(labels, supplier);
    }

    @Override
    public MetricRegistration registerCounter(CounterMetric metric, MetricLabels labels, DoubleSupplier supplier) {
        Objects.requireNonNull(metric, "metric");
        ensureOpen();
        return counters.computeIfAbsent(metric, CounterState::new).registerObserved(labels, supplier);
    }

    @Override
    public HistogramHandle histogram(HistogramMetric metric) {
        Objects.requireNonNull(metric, "metric");
        ensureOpen();
        return histograms.computeIfAbsent(metric, HistogramState::new).handle();
    }

    @Override
    public TimerHandle timer(TimerMetric metric) {
        Objects.requireNonNull(metric, "metric");
        ensureOpen();
        return timers.computeIfAbsent(metric, TimerState::new).handle();
    }

    @Override
    public UnsafeTelemetry unsafe() {
        return unsafeTelemetry;
    }

    @Override
    public Collection<MetricSnapshotSource.MetricSnapshot> snapshot() {
        List<MetricSnapshotSource.MetricSnapshot> snapshots = new ArrayList<>();
        counters.values().forEach(state -> snapshots.add(state.snapshot()));
        gauges.values().forEach(state -> snapshots.add(state.snapshot()));
        histograms.values().forEach(state -> snapshots.add(state.snapshot()));
        timers.values().forEach(state -> snapshots.add(state.snapshot()));
        return List.copyOf(snapshots);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            counters.clear();
            gauges.clear();
            histograms.clear();
            timers.clear();
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Telemetry is closed");
        }
    }

    private interface SnapshotProducer {

        MetricSnapshotSource.MetricSnapshot snapshot();
    }

    private abstract static class BaseState<D extends MetricDescriptor> implements SnapshotProducer {

        private final D metric;
        private final List<LabelKey> labelKeys;
        private final List<String> labelNames;
        private final Set<String> labelNameSet;

        private BaseState(D metric) {
            this.metric = metric;
            this.labelKeys = metric.labelKeys();
            this.labelNames = labelKeys.stream().map(LabelKey::name).toList();
            this.labelNameSet = Set.copyOf(labelNames);
        }

        protected D metric() {
            return metric;
        }

        protected List<String> labelNames() {
            return labelNames;
        }

        protected SeriesKey safeKey(MetricLabels labels) {
            return key(labels.asMap(), true);
        }

        protected SeriesKey unsafeKey(UnsafeMetricLabels labels) {
            return key(labels.asMap(), false);
        }

        private SeriesKey key(Map<String, String> labels, boolean enforceAllowedValues) {
            Objects.requireNonNull(labels, "labels");
            if (!labels.keySet().equals(labelNameSet)) {
                throw new IllegalArgumentException(
                        "Metric '" + metric.name() + "' expects label keys " + labelNames + " but received " + labels.keySet()
                );
            }
            List<String> values = new ArrayList<>(labelKeys.size());
            for (LabelKey labelKey : labelKeys) {
                String value = Objects.requireNonNull(labels.get(labelKey.name()), "Missing value for label '" + labelKey.name() + "'");
                if (enforceAllowedValues && labelKey.isBounded() && !labelKey.allowedValues().contains(value)) {
                    throw new IllegalArgumentException(
                            "Label '" + labelKey.name() + "' does not allow value '" + value + "'. Allowed values: " + labelKey.allowedValues()
                    );
                }
                values.add(value);
            }
            return new SeriesKey(values);
        }
    }

    private static final class CounterState extends BaseState<CounterMetric> {

        private final ConcurrentMap<SeriesKey, DoubleAdder> manual = new ConcurrentHashMap<>();
        private final ConcurrentMap<SeriesKey, DoubleSupplier> observed = new ConcurrentHashMap<>();

        private CounterState(CounterMetric metric) {
            super(metric);
        }

        private CounterHandle handle() {
            return new CounterHandle() {
                @Override
                public void increment(MetricLabels labels) {
                    add(1.0, labels);
                }

                @Override
                public void add(double amount, MetricLabels labels) {
                    CounterState.this.add(labels, amount);
                }
            };
        }

        private void add(MetricLabels labels, double amount) {
            addSafe(safeKey(labels), amount);
        }

        private void addUnsafe(UnsafeMetricLabels labels, double amount) {
            addSafe(unsafeKey(labels), amount);
        }

        private void addSafe(SeriesKey key, double amount) {
            if (!Double.isFinite(amount) || amount < 0.0) {
                throw new IllegalArgumentException("Counter increments must be finite and non-negative");
            }
            if (observed.containsKey(key)) {
                throw duplicateSeries(metric().name(), key);
            }
            manual.computeIfAbsent(key, ignored -> new DoubleAdder()).add(amount);
        }

        private MetricRegistration registerObserved(MetricLabels labels, DoubleSupplier supplier) {
            return registerObserved(safeKey(labels), supplier);
        }

        private MetricRegistration registerObservedUnsafe(UnsafeMetricLabels labels, DoubleSupplier supplier) {
            return registerObserved(unsafeKey(labels), supplier);
        }

        private MetricRegistration registerObserved(SeriesKey key, DoubleSupplier supplier) {
            Objects.requireNonNull(supplier, "supplier");
            if (manual.containsKey(key)) {
                throw duplicateSeries(metric().name(), key);
            }
            DoubleSupplier previous = observed.putIfAbsent(key, supplier);
            if (previous != null) {
                throw duplicateSeries(metric().name(), key);
            }
            return () -> observed.remove(key, supplier);
        }

        @Override
        public MetricSnapshotSource.MetricSnapshot snapshot() {
            List<MetricSnapshotSource.ScalarSample> samples = new ArrayList<>();
            manual.forEach((key, adder) -> samples.add(new MetricSnapshotSource.ScalarSample(key.values(), adder.sum())));
            observed.forEach((key, supplier) -> {
                double value = supplier.getAsDouble();
                if (value < 0.0) {
                    throw new IllegalStateException("Observed counter '" + metric().name() + "' produced a negative value");
                }
                samples.add(new MetricSnapshotSource.ScalarSample(key.values(), value));
            });
            return new MetricSnapshotSource.ScalarMetricSnapshot(
                    metric().name(),
                    metric().help(),
                    metric().unit(),
                    MetricSnapshotSource.MetricType.COUNTER,
                    labelNames(),
                    samples
            );
        }
    }

    private static final class GaugeState extends BaseState<GaugeMetric> {

        private final ConcurrentMap<SeriesKey, AtomicLong> settable = new ConcurrentHashMap<>();
        private final ConcurrentMap<SeriesKey, DoubleSupplier> observed = new ConcurrentHashMap<>();

        private GaugeState(GaugeMetric metric) {
            super(metric);
        }

        private SettableGauge handle() {
            return (value, labels) -> set(labels, value);
        }

        private void set(MetricLabels labels, double value) {
            set(safeKey(labels), value);
        }

        private void setUnsafe(UnsafeMetricLabels labels, double value) {
            set(unsafeKey(labels), value);
        }

        private void set(SeriesKey key, double value) {
            if (observed.containsKey(key)) {
                throw duplicateSeries(metric().name(), key);
            }
            settable.computeIfAbsent(key, ignored -> new AtomicLong()).set(Double.doubleToRawLongBits(value));
        }

        private MetricRegistration registerObserved(MetricLabels labels, DoubleSupplier supplier) {
            return registerObserved(safeKey(labels), supplier);
        }

        private MetricRegistration registerObservedUnsafe(UnsafeMetricLabels labels, DoubleSupplier supplier) {
            return registerObserved(unsafeKey(labels), supplier);
        }

        private MetricRegistration registerObserved(SeriesKey key, DoubleSupplier supplier) {
            Objects.requireNonNull(supplier, "supplier");
            if (settable.containsKey(key)) {
                throw duplicateSeries(metric().name(), key);
            }
            DoubleSupplier previous = observed.putIfAbsent(key, supplier);
            if (previous != null) {
                throw duplicateSeries(metric().name(), key);
            }
            return () -> observed.remove(key, supplier);
        }

        @Override
        public MetricSnapshotSource.MetricSnapshot snapshot() {
            List<MetricSnapshotSource.ScalarSample> samples = new ArrayList<>();
            settable.forEach((key, bits) -> samples.add(new MetricSnapshotSource.ScalarSample(key.values(), Double.longBitsToDouble(bits.get()))));
            observed.forEach((key, supplier) -> samples.add(new MetricSnapshotSource.ScalarSample(key.values(), supplier.getAsDouble())));
            return new MetricSnapshotSource.ScalarMetricSnapshot(
                    metric().name(),
                    metric().help(),
                    metric().unit(),
                    MetricSnapshotSource.MetricType.GAUGE,
                    labelNames(),
                    samples
            );
        }
    }

    private abstract static class BucketedState<D extends MetricDescriptor> extends BaseState<D> {

        private final List<Double> buckets;
        private final ConcurrentMap<SeriesKey, HistogramSeries> series = new ConcurrentHashMap<>();

        private BucketedState(D metric, List<Double> buckets) {
            super(metric);
            this.buckets = buckets;
        }

        protected void record(MetricLabels labels, double value) {
            record(safeKey(labels), value);
        }

        protected void recordUnsafe(UnsafeMetricLabels labels, double value) {
            record(unsafeKey(labels), value);
        }

        private void record(SeriesKey key, double value) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Histogram values must be finite");
            }
            series.computeIfAbsent(key, ignored -> new HistogramSeries(buckets)).record(value);
        }

        @Override
        public MetricSnapshotSource.MetricSnapshot snapshot() {
            List<MetricSnapshotSource.HistogramSample> samples = new ArrayList<>();
            series.forEach((key, histogramSeries) -> samples.add(histogramSeries.snapshot(key)));
            return new MetricSnapshotSource.HistogramMetricSnapshot(
                    metric().name(),
                    metric().help(),
                    metric().unit(),
                    labelNames(),
                    buckets,
                    samples
            );
        }
    }

    private static final class HistogramState extends BucketedState<HistogramMetric> {

        private HistogramState(HistogramMetric metric) {
            super(metric, metric.buckets());
        }

        private HistogramHandle handle() {
            return (value, labels) -> record(labels, value);
        }
    }

    private static final class TimerState extends BucketedState<TimerMetric> {

        private TimerState(TimerMetric metric) {
            super(metric, metric.buckets());
        }

        private TimerHandle handle() {
            return new TimerHandle() {
                @Override
                public void record(Duration duration, MetricLabels labels) {
                    Objects.requireNonNull(duration, "duration");
                    TimerState.this.record(labels, duration.toNanos() / 1_000_000_000.0);
                }

                @Override
                public void recordNanos(long nanos, MetricLabels labels) {
                    TimerState.this.record(labels, nanos / 1_000_000_000.0);
                }

                @Override
                public TimingScope startScope(MetricLabels labels) {
                    return new TimerScope(TimerState.this, labels);
                }
            };
        }

        private void recordUnsafe(UnsafeMetricLabels labels, Duration duration) {
            Objects.requireNonNull(duration, "duration");
            recordUnsafe(labels, duration.toNanos() / 1_000_000_000.0);
        }

        private void recordUnsafeNanos(UnsafeMetricLabels labels, long nanos) {
            recordUnsafe(labels, nanos / 1_000_000_000.0);
        }
    }

    private static final class HistogramSeries {

        private final LongAdder count = new LongAdder();
        private final DoubleAdder sum = new DoubleAdder();
        private final LongAdder[] bucketCounts;
        private final List<Double> buckets;

        private HistogramSeries(List<Double> buckets) {
            this.buckets = buckets;
            this.bucketCounts = new LongAdder[buckets.size()];
            for (int i = 0; i < bucketCounts.length; i++) {
                bucketCounts[i] = new LongAdder();
            }
        }

        private void record(double value) {
            count.increment();
            sum.add(value);
            for (int i = 0; i < buckets.size(); i++) {
                if (value <= buckets.get(i)) {
                    bucketCounts[i].increment();
                    return;
                }
            }
        }

        private MetricSnapshotSource.HistogramSample snapshot(SeriesKey key) {
            List<Long> counts = new ArrayList<>(bucketCounts.length);
            for (LongAdder bucketCount : bucketCounts) {
                counts.add(bucketCount.sum());
            }
            return new MetricSnapshotSource.HistogramSample(key.values(), count.sum(), sum.sum(), counts);
        }
    }

    private static final class SeriesKey {

        private final List<String> values;

        private SeriesKey(List<String> values) {
            this.values = List.copyOf(values);
        }

        private List<String> values() {
            return values;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SeriesKey that)) {
                return false;
            }
            return values.equals(that.values);
        }

        @Override
        public int hashCode() {
            return values.hashCode();
        }
    }

    private static IllegalArgumentException duplicateSeries(String metricName, SeriesKey key) {
        return new IllegalArgumentException("Metric '" + metricName + "' already has a registration for labels " + key.values());
    }

    private final class StandardUnsafeTelemetry implements UnsafeTelemetry {

        @Override
        public void increment(CounterMetric metric, UnsafeMetricLabels labels) {
            add(metric, labels, 1.0);
        }

        @Override
        public void add(CounterMetric metric, UnsafeMetricLabels labels, double amount) {
            Objects.requireNonNull(metric, "metric");
            ensureOpen();
            counters.computeIfAbsent(metric, CounterState::new).addUnsafe(labels, amount);
        }

        @Override
        public void set(GaugeMetric metric, UnsafeMetricLabels labels, double value) {
            Objects.requireNonNull(metric, "metric");
            ensureOpen();
            gauges.computeIfAbsent(metric, GaugeState::new).setUnsafe(labels, value);
        }

        @Override
        public MetricRegistration registerGauge(GaugeMetric metric, UnsafeMetricLabels labels, DoubleSupplier supplier) {
            Objects.requireNonNull(metric, "metric");
            ensureOpen();
            return gauges.computeIfAbsent(metric, GaugeState::new).registerObservedUnsafe(labels, supplier);
        }

        @Override
        public MetricRegistration registerCounter(CounterMetric metric, UnsafeMetricLabels labels, DoubleSupplier supplier) {
            Objects.requireNonNull(metric, "metric");
            ensureOpen();
            return counters.computeIfAbsent(metric, CounterState::new).registerObservedUnsafe(labels, supplier);
        }

        @Override
        public void record(HistogramMetric metric, UnsafeMetricLabels labels, double value) {
            Objects.requireNonNull(metric, "metric");
            ensureOpen();
            histograms.computeIfAbsent(metric, HistogramState::new).recordUnsafe(labels, value);
        }

        @Override
        public void record(TimerMetric metric, UnsafeMetricLabels labels, Duration duration) {
            Objects.requireNonNull(metric, "metric");
            ensureOpen();
            timers.computeIfAbsent(metric, TimerState::new).recordUnsafe(labels, duration);
        }

        @Override
        public void recordNanos(TimerMetric metric, UnsafeMetricLabels labels, long nanos) {
            Objects.requireNonNull(metric, "metric");
            ensureOpen();
            timers.computeIfAbsent(metric, TimerState::new).recordUnsafeNanos(labels, nanos);
        }

        @Override
        public TimingScope startScope(TimerMetric metric, UnsafeMetricLabels labels) {
            Objects.requireNonNull(metric, "metric");
            ensureOpen();
            return new UnsafeTimerScope(timers.computeIfAbsent(metric, TimerState::new), labels);
        }
    }

    private static final class TimerScope implements TimingScope {

        private final TimerState state;
        private final MetricLabels labels;
        private final long startedAt = System.nanoTime();
        private final AtomicBoolean stopped = new AtomicBoolean();
        private volatile Duration elapsed = Duration.ZERO;

        private TimerScope(TimerState state, MetricLabels labels) {
            this.state = state;
            this.labels = labels;
        }

        @Override
        public Duration elapsed() {
            return elapsed;
        }

        @Override
        public boolean stopped() {
            return stopped.get();
        }

        @Override
        public void stop() {
            if (stopped.compareAndSet(false, true)) {
                long nanos = System.nanoTime() - startedAt;
                elapsed = Duration.ofNanos(nanos);
                state.record(labels, nanos / 1_000_000_000.0);
            }
        }
    }

    private static final class UnsafeTimerScope implements TimingScope {

        private final TimerState state;
        private final UnsafeMetricLabels labels;
        private final long startedAt = System.nanoTime();
        private final AtomicBoolean stopped = new AtomicBoolean();
        private volatile Duration elapsed = Duration.ZERO;

        private UnsafeTimerScope(TimerState state, UnsafeMetricLabels labels) {
            this.state = state;
            this.labels = labels;
        }

        @Override
        public Duration elapsed() {
            return elapsed;
        }

        @Override
        public boolean stopped() {
            return stopped.get();
        }

        @Override
        public void stop() {
            if (stopped.compareAndSet(false, true)) {
                long nanos = System.nanoTime() - startedAt;
                elapsed = Duration.ofNanos(nanos);
                state.recordUnsafeNanos(labels, nanos);
            }
        }
    }
}
