package sh.harold.creative.library.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DocumentValues {

    private DocumentValues() {
    }

    public static Map<String, Object> normalizeRoot(Map<String, Object> data) {
        return deepCopyMap(Objects.requireNonNullElse(data, Map.of()));
    }

    public static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("document keys must be strings");
            }
            copy.put(key, normalizeValue(entry.getValue()));
        }
        return copy;
    }

    public static Map<String, Object> deepImmutableCopyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source != null) {
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("document keys must be strings");
                }
                copy.put(key, deepImmutableCopyValue(entry.getValue()));
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    public static Object normalizeValue(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Boolean
                || value instanceof Number) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            return deepCopyMap((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(normalizeValue(element));
            }
            return copy;
        }
        throw new IllegalArgumentException("unsupported document value type: " + value.getClass().getName());
    }

    public static Object readPath(Map<String, Object> root, String path) {
        Objects.requireNonNull(root, "root");
        if (path == null || path.isBlank()) {
            return root;
        }
        String[] parts = splitPath(path);
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    public static boolean containsPath(Map<String, Object> root, String path) {
        Objects.requireNonNull(root, "root");
        if (path == null || path.isBlank()) {
            return true;
        }
        String[] parts = splitPath(path);
        Object current = root;
        for (int index = 0; index < parts.length; index++) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(parts[index])) {
                return false;
            }
            current = map.get(parts[index]);
            if (index < parts.length - 1 && !(current instanceof Map<?, ?>)) {
                return false;
            }
        }
        return true;
    }

    public static void writePath(Map<String, Object> root, String path, Object value) {
        Objects.requireNonNull(root, "root");
        String[] parts = splitPath(requirePath(path));
        Map<String, Object> current = root;
        for (int index = 0; index < parts.length - 1; index++) {
            Object next = current.get(parts[index]);
            if (!(next instanceof Map<?, ?> map)) {
                Map<String, Object> created = new LinkedHashMap<>();
                current.put(parts[index], created);
                current = created;
                continue;
            }
            current = (Map<String, Object>) map;
        }
        current.put(parts[parts.length - 1], normalizeValue(value));
    }

    public static void removePath(Map<String, Object> root, String path) {
        Objects.requireNonNull(root, "root");
        String[] parts = splitPath(requirePath(path));
        Map<String, Object> current = root;
        for (int index = 0; index < parts.length - 1; index++) {
            Object next = current.get(parts[index]);
            if (!(next instanceof Map<?, ?> map)) {
                return;
            }
            current = (Map<String, Object>) map;
        }
        current.remove(parts[parts.length - 1]);
    }

    public static String requirePath(String path) {
        Objects.requireNonNull(path, "path");
        if (path.isBlank()) {
            throw new IllegalArgumentException("path cannot be blank");
        }
        splitPath(path);
        return path;
    }

    private static Object deepImmutableCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return deepImmutableCopyMap((Map<String, Object>) map);
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

    private static String[] splitPath(String path) {
        String[] parts = path.split("\\.");
        for (String part : parts) {
            if (part.isBlank()) {
                throw new IllegalArgumentException("path segments cannot be blank: " + path);
            }
        }
        return parts;
    }
}
