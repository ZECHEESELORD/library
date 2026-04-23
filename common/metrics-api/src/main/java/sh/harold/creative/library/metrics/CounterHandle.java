package sh.harold.creative.library.metrics;

public interface CounterHandle {

    void increment(MetricLabels labels);

    void add(double amount, MetricLabels labels);

    default void increment() {
        increment(MetricLabels.empty());
    }

    default void add(double amount) {
        add(amount, MetricLabels.empty());
    }
}
