package sh.harold.creative.library.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record DocumentSnapshot(DocumentKey key, Map<String, Object> data, boolean exists) {

    public DocumentSnapshot {
        Objects.requireNonNull(key, "key");
        data = deepImmutableCopyMap(Objects.requireNonNullElse(data, Map.of()));
    }

    private static Map<String, Object> deepImmutableCopyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), deepImmutableCopyValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Object deepImmutableCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return deepImmutableCopyMap(typed);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(deepImmutableCopyValue(element));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
