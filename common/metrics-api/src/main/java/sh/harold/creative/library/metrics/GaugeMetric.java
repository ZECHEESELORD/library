package sh.harold.creative.library.metrics;

import java.util.List;

public record GaugeMetric(String name, String help, String unit, List<LabelKey> labelKeys) implements MetricDescriptor {

    public GaugeMetric {
        labelKeys = Metrics.copyLabelKeys(labelKeys);
        Metrics.validateDescriptor(name, help, unit, labelKeys);
    }
}
