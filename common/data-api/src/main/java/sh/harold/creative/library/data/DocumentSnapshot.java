package sh.harold.creative.library.data;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record DocumentSnapshot(DocumentKey key, Map<String, Object> data, boolean exists, String revision) {

    public DocumentSnapshot {
        Objects.requireNonNull(key, "key");
        data = DocumentValues.deepImmutableCopyMap(Objects.requireNonNullElse(data, Map.of()));
        revision = requireRevision(revision);
    }

    public Optional<Object> get(String path) {
        return Optional.ofNullable(DocumentValues.readPath(data, path));
    }

    public boolean contains(String path) {
        return DocumentValues.containsPath(data, path);
    }

    public <T> Optional<T> get(String path, Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value = DocumentValues.readPath(data, path);
        if (type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    private static String requireRevision(String value) {
        Objects.requireNonNull(value, "revision");
        if (value.isBlank()) {
            throw new IllegalArgumentException("revision cannot be blank");
        }
        return value;
    }
}
