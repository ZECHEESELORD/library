package sh.harold.creative.library.data;

import java.util.Objects;

public record DocumentKey(String namespace, String collection, String id) {

    public DocumentKey {
        namespace = requireSegment(namespace, "namespace");
        collection = requireSegment(collection, "collection");
        id = requireSegment(id, "id");
    }

    private static String requireSegment(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return value;
    }
}
