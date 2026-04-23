package sh.harold.creative.library.metrics;

import java.util.List;

public record TimerMetric(String name, String help, List<LabelKey> labelKeys, List<Double> buckets) implements MetricDescriptor {

    public TimerMetric {
        labelKeys = Metrics.copyLabelKeys(labelKeys);
        buckets = Metrics.copyBuckets(buckets);
        Metrics.validateDescriptor(name, help, "seconds", labelKeys);
    }

    @Override
    public String unit() {
        return "seconds";
    }
}
