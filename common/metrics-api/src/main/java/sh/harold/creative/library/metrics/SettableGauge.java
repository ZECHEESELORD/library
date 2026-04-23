package sh.harold.creative.library.metrics;

public interface SettableGauge {

    void set(double value, MetricLabels labels);

    default void set(double value) {
        set(value, MetricLabels.empty());
    }
}
