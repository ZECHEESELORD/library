package sh.harold.creative.library.metrics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class MetricLabels {

    private static final MetricLabels EMPTY = new MetricLabels(Map.of());

    private final Map<String, String> values;

    private MetricLabels(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    public static MetricLabels empty() {
        return EMPTY;
    }

    public static MetricLabels of(LabelKey key, String value) {
        return builder().put(key, value).build();
    }

    public static MetricLabels of(LabelKey firstKey, String firstValue, LabelKey secondKey, String secondValue) {
        return builder()
                .put(firstKey, firstValue)
                .put(secondKey, secondValue)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Map<String, String> asMap() {
        return values;
    }

    String value(String key) {
        return values.get(key);
    }

    public static final class Builder {

        private final Map<String, String> values = new LinkedHashMap<>();

        public Builder put(LabelKey key, String value) {
            Objects.requireNonNull(key, "key");
            String previous = values.putIfAbsent(key.name(), Objects.requireNonNull(value, "value"));
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate label key '" + key.name() + "'");
            }
            return this;
        }

        public MetricLabels build() {
            if (values.isEmpty()) {
                return EMPTY;
            }
            return new MetricLabels(values);
        }
    }
}
