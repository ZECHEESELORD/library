package sh.harold.creative.library.metrics;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record LabelKey(String name, List<String> allowedValues) {

    public LabelKey {
        Metrics.requireLabelName(name);
        Objects.requireNonNull(allowedValues, "allowedValues");
        LinkedHashSet<String> orderedValues = new LinkedHashSet<>();
        for (String allowedValue : allowedValues) {
            String normalized = Objects.requireNonNull(allowedValue, "allowedValues cannot contain null values");
            if (!orderedValues.add(normalized)) {
                throw new IllegalArgumentException("Duplicate allowed label value '" + normalized + "' for label '" + name + "'");
            }
        }
        allowedValues = List.copyOf(orderedValues);
    }

    public static LabelKey of(String name, String... allowedValues) {
        return new LabelKey(name, List.of(Arrays.copyOf(allowedValues, allowedValues.length)));
    }

    public static LabelKey enumValues(String name, Class<? extends Enum<?>> type) {
        Objects.requireNonNull(type, "type");
        Object[] constants = type.getEnumConstants();
        if (constants == null || constants.length == 0) {
            throw new IllegalArgumentException("Enum type '" + type.getName() + "' does not define any constants");
        }
        return new LabelKey(
                name,
                Arrays.stream(constants)
                        .map(constant -> ((Enum<?>) constant).name().toLowerCase())
                        .toList()
        );
    }

    public boolean isBounded() {
        return !allowedValues.isEmpty();
    }

    String validateValue(String value, boolean enforceAllowedValues) {
        String normalized = Objects.requireNonNull(value, "label value");
        if (enforceAllowedValues && isBounded() && !allowedValues.contains(normalized)) {
            throw new IllegalArgumentException("Label '" + name + "' does not allow value '" + normalized + "'. Allowed values: " + allowedValues);
        }
        return normalized;
    }

    Set<String> allowedValueSet() {
        return Set.copyOf(allowedValues);
    }
}
