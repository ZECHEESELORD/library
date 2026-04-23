package sh.harold.creative.library.metrics;

import java.util.List;

public record CounterMetric(String name, String help, String unit, List<LabelKey> labelKeys) implements MetricDescriptor {

    public CounterMetric {
        labelKeys = Metrics.copyLabelKeys(labelKeys);
        Metrics.validateDescriptor(name, help, unit, labelKeys);
    }
}
