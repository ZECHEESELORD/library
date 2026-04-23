package sh.harold.creative.library.metrics;

import java.util.List;

public record HistogramMetric(String name, String help, String unit, List<LabelKey> labelKeys, List<Double> buckets) implements MetricDescriptor {

    public HistogramMetric {
        labelKeys = Metrics.copyLabelKeys(labelKeys);
        buckets = Metrics.copyBuckets(buckets);
        Metrics.validateDescriptor(name, help, unit, labelKeys);
    }
}
