package sh.harold.creative.library.data.core;

import sh.harold.creative.library.data.DataNamespace;
import sh.harold.creative.library.data.DocumentCollection;
import sh.harold.creative.library.data.DocumentStore;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

final class StoreBackedDataNamespace implements DataNamespace {

    private final String name;
    private final DocumentStore store;
    private final Map<String, DocumentCollection> collections = new ConcurrentHashMap<>();

    StoreBackedDataNamespace(String name, DocumentStore store) {
        this.name = requireName(name);
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public DocumentCollection collection(String name) {
        return collections.computeIfAbsent(name, ignored -> new StoreBackedDocumentCollection(this, ignored, store));
    }

    private static String requireName(String value) {
        Objects.requireNonNull(value, "name");
        if (value.isBlank()) {
            throw new IllegalArgumentException("namespace cannot be blank");
        }
        return value;
    }
}
