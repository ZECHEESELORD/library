package sh.harold.creative.library.metrics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class UnsafeMetricLabels {

    private static final UnsafeMetricLabels EMPTY = new UnsafeMetricLabels(Map.of());

    private final Map<String, String> values;

    private UnsafeMetricLabels(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    public static UnsafeMetricLabels empty() {
        return EMPTY;
    }

    public static UnsafeMetricLabels of(String key, String value) {
        return builder().put(key, value).build();
    }

    public static UnsafeMetricLabels of(String firstKey, String firstValue, String secondKey, String secondValue) {
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

    public static final class Builder {

        private final Map<String, String> values = new LinkedHashMap<>();

        public Builder put(String key, String value) {
            Metrics.requireLabelName(key);
            String previous = values.putIfAbsent(key, Objects.requireNonNull(value, "value"));
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate label key '" + key + "'");
            }
            return this;
        }

        public UnsafeMetricLabels build() {
            if (values.isEmpty()) {
                return EMPTY;
            }
            return new UnsafeMetricLabels(values);
        }
    }
}
