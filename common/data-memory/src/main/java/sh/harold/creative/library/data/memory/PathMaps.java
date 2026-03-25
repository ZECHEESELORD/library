package sh.harold.creative.library.data.memory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PathMaps {

    private PathMaps() {
    }

    static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), deepCopyValue(entry.getValue()));
        }
        return copy;
    }

    private static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return deepCopy(typed);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(PathMaps::deepCopyValue).toList();
        }
        return value;
    }

    static Object read(Map<String, Object> source, String path) {
        if (path == null || path.isBlank()) {
            return source;
        }
        Object current = source;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    static void write(Map<String, Object> target, String path, Object value) {
        if (path == null || path.isBlank()) {
            target.clear();
            if (value instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                target.putAll(deepCopy(typed));
            }
            return;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> current = target;
        for (int i = 0; i < parts.length - 1; i++) {
            Object nested = current.get(parts[i]);
            if (!(nested instanceof Map<?, ?>)) {
                Map<String, Object> created = new LinkedHashMap<>();
                current.put(parts[i], created);
                current = created;
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) nested;
                current = typed;
            }
        }
        current.put(parts[parts.length - 1], deepCopyValue(value));
    }

    static void remove(Map<String, Object> target, String path) {
        if (path == null || path.isBlank()) {
            target.clear();
            return;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> current = target;
        for (int i = 0; i < parts.length - 1; i++) {
            Object nested = current.get(parts[i]);
            if (!(nested instanceof Map<?, ?>)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) nested;
            current = typed;
        }
        current.remove(parts[parts.length - 1]);
    }
}
