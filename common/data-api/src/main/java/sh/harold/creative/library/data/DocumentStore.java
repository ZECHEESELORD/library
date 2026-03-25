package sh.harold.creative.library.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;

public interface DocumentStore extends AutoCloseable {

    CompletionStage<DocumentSnapshot> read(DocumentKey key);

    CompletionStage<Void> write(DocumentKey key, Map<String, Object> data);

    CompletionStage<DocumentSnapshot> update(DocumentKey key, UnaryOperator<Map<String, Object>> mutator);

    default CompletionStage<Void> patch(DocumentKey key, DocumentPatch patch) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(patch, "patch");
        return read(key).thenCompose(snapshot -> {
            if (!snapshot.exists() && patch.setValues().isEmpty()) {
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
            return update(key, current -> {
                Map<String, Object> working = deepCopy(current);
                for (Map.Entry<String, Object> entry : patch.setValues().entrySet()) {
                    writePath(working, entry.getKey(), entry.getValue());
                }
                for (String path : patch.removePaths()) {
                    removePath(working, path);
                }
                return working;
            }).thenApply(ignored -> null);
        });
    }

    CompletionStage<Boolean> delete(DocumentKey key);

    CompletionStage<List<DocumentSnapshot>> all(String collection);

    CompletionStage<Long> count(String collection);

    @Override
    void close();

    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new java.util.LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        source.forEach((entryKey, value) -> copy.put(String.valueOf(entryKey), deepCopyValue(value)));
        return copy;
    }

    private static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return deepCopy(typed);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(deepCopyValue(element));
            }
            return copy;
        }
        return value;
    }

    private static void writePath(Map<String, Object> root, String path, Object value) {
        if (path == null || path.isBlank()) {
            root.clear();
            if (value instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                root.putAll(deepCopy(typed));
            }
            return;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map<?, ?> map)) {
                Map<String, Object> created = new java.util.LinkedHashMap<>();
                current.put(parts[i], created);
                current = created;
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                current = typed;
            }
        }
        current.put(parts[parts.length - 1], deepCopyValue(value));
    }

    private static void removePath(Map<String, Object> root, String path) {
        if (path == null || path.isBlank()) {
            root.clear();
            return;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map<?, ?> map)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            current = typed;
        }
        current.remove(parts[parts.length - 1]);
    }
}
