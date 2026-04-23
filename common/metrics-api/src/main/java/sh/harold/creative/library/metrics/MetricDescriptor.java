package sh.harold.creative.library.metrics;

import java.util.List;

public sealed interface MetricDescriptor permits CounterMetric, GaugeMetric, HistogramMetric, TimerMetric {

    String name();

    String help();

    String unit();

    List<LabelKey> labelKeys();
}
