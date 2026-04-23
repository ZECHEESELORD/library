package sh.harold.creative.library.metrics.core;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public interface MetricSnapshotSource {

    Collection<MetricSnapshot> snapshot();

    sealed interface MetricSnapshot permits ScalarMetricSnapshot, HistogramMetricSnapshot {

        String name();

        String help();

        String unit();
    }

    enum MetricType {
        COUNTER,
        GAUGE
    }

    record ScalarMetricSnapshot(
            String name,
            String help,
            String unit,
            MetricType type,
            List<String> labelNames,
            List<ScalarSample> samples
    ) implements MetricSnapshot {

        public ScalarMetricSnapshot {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(help, "help");
            Objects.requireNonNull(unit, "unit");
            Objects.requireNonNull(type, "type");
            labelNames = List.copyOf(labelNames);
            samples = List.copyOf(samples);
        }
    }

    record ScalarSample(List<String> labelValues, double value) {

        public ScalarSample {
            labelValues = List.copyOf(labelValues);
        }
    }

    record HistogramMetricSnapshot(
            String name,
            String help,
            String unit,
            List<String> labelNames,
            List<Double> buckets,
            List<HistogramSample> samples
    ) implements MetricSnapshot {

        public HistogramMetricSnapshot {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(help, "help");
            Objects.requireNonNull(unit, "unit");
            labelNames = List.copyOf(labelNames);
            buckets = List.copyOf(buckets);
            samples = List.copyOf(samples);
        }
    }

    record HistogramSample(List<String> labelValues, long count, double sum, List<Long> bucketCounts) {

        public HistogramSample {
            labelValues = List.copyOf(labelValues);
            bucketCounts = List.copyOf(bucketCounts);
        }
    }
}
