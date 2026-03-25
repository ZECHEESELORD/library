package sh.harold.creative.library.data;

import java.util.Objects;

public record DocumentKey(String collection, String id) {

    public DocumentKey {
        Objects.requireNonNull(collection, "collection");
        Objects.requireNonNull(id, "id");
        if (collection.isBlank()) {
            throw new IllegalArgumentException("collection cannot be blank");
        }
        if (id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
    }
}
