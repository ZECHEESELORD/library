package sh.harold.creative.library.metrics;

public interface HistogramHandle {

    void record(double value, MetricLabels labels);

    default void record(double value) {
        record(value, MetricLabels.empty());
    }
}
