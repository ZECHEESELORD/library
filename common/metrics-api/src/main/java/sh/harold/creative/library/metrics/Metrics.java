package sh.harold.creative.library.metrics;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class Metrics {

    private static final Pattern METRIC_NAME = Pattern.compile("[a-zA-Z_:][a-zA-Z0-9_:]*");
    private static final Pattern LABEL_NAME = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
    private static final Pattern UNIT = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*");
    private static final List<Double> DEFAULT_LATENCY_BUCKETS_SECONDS = List.of(
            0.001,
            0.005,
            0.01,
            0.025,
            0.05,
            0.1,
            0.25,
            0.5,
            1.0,
            2.5,
            5.0,
            10.0
    );

    private Metrics() {
    }

    public static LabelKey label(String name, String... allowedValues) {
        return LabelKey.of(name, allowedValues);
    }

    public static CounterMetric counter(String name, String help, LabelKey... labelKeys) {
        return new CounterMetric(name, help, "", List.of(Arrays.copyOf(labelKeys, labelKeys.length)));
    }

    public static CounterMetric counter(String name, String help, String unit, LabelKey... labelKeys) {
        return new CounterMetric(name, help, unit, List.of(Arrays.copyOf(labelKeys, labelKeys.length)));
    }

    public static GaugeMetric gauge(String name, String help, String unit, LabelKey... labelKeys) {
        return new GaugeMetric(name, help, unit, List.of(Arrays.copyOf(labelKeys, labelKeys.length)));
    }

    public static HistogramMetric histogram(String name, String help, String unit, List<Double> buckets, LabelKey... labelKeys) {
        return new HistogramMetric(name, help, unit, List.of(Arrays.copyOf(labelKeys, labelKeys.length)), buckets);
    }

    public static TimerMetric timer(String name, String help, LabelKey... labelKeys) {
        return new TimerMetric(name, help, List.of(Arrays.copyOf(labelKeys, labelKeys.length)), DEFAULT_LATENCY_BUCKETS_SECONDS);
    }

    public static TimerMetric timer(String name, String help, List<Double> buckets, LabelKey... labelKeys) {
        return new TimerMetric(name, help, List.of(Arrays.copyOf(labelKeys, labelKeys.length)), buckets);
    }

    public static List<Double> latencyBucketsSeconds() {
        return DEFAULT_LATENCY_BUCKETS_SECONDS;
    }

    static void validateDescriptor(String name, String help, String unit, List<LabelKey> labelKeys) {
        requireMetricName(name);
        String normalizedHelp = Objects.requireNonNull(help, "help").trim();
        if (normalizedHelp.isEmpty()) {
            throw new IllegalArgumentException("Metric help must not be blank");
        }
        if (!unit.isBlank() && !UNIT.matcher(unit).matches()) {
            throw new IllegalArgumentException("Metric unit '" + unit + "' must match " + UNIT.pattern());
        }
        LinkedHashSet<String> labelNames = new LinkedHashSet<>();
        for (LabelKey labelKey : labelKeys) {
            Objects.requireNonNull(labelKey, "labelKeys cannot contain null");
            if (!labelNames.add(labelKey.name())) {
                throw new IllegalArgumentException("Duplicate label key '" + labelKey.name() + "' for metric '" + name + "'");
            }
        }
    }

    static void requireMetricName(String name) {
        String normalized = Objects.requireNonNull(name, "name");
        if (!METRIC_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Metric name '" + normalized + "' must match " + METRIC_NAME.pattern());
        }
    }

    static void requireLabelName(String name) {
        String normalized = Objects.requireNonNull(name, "name");
        if (!LABEL_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Label name '" + normalized + "' must match " + LABEL_NAME.pattern());
        }
    }

    static List<LabelKey> copyLabelKeys(List<LabelKey> labelKeys) {
        return List.copyOf(Objects.requireNonNull(labelKeys, "labelKeys"));
    }

    static List<Double> copyBuckets(List<Double> buckets) {
        Objects.requireNonNull(buckets, "buckets");
        if (buckets.isEmpty()) {
            throw new IllegalArgumentException("Histogram buckets must not be empty");
        }
        double previous = Double.NEGATIVE_INFINITY;
        for (Double bucket : buckets) {
            double value = Objects.requireNonNull(bucket, "buckets cannot contain null");
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Histogram bucket values must be finite");
            }
            if (value <= previous) {
                throw new IllegalArgumentException("Histogram buckets must be strictly increasing");
            }
            previous = value;
        }
        return List.copyOf(buckets);
    }
}
