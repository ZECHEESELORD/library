package sh.harold.creative.library.data.core;

import sh.harold.creative.library.data.DataNamespace;
import sh.harold.creative.library.data.Document;
import sh.harold.creative.library.data.DocumentCollection;
import sh.harold.creative.library.data.DocumentKey;
import sh.harold.creative.library.data.DocumentStore;

import java.util.Objects;
import java.util.List;
import java.util.concurrent.CompletionStage;

final class StoreBackedDocumentCollection implements DocumentCollection {

    private final DataNamespace namespace;
    private final String name;
    private final DocumentStore store;

    StoreBackedDocumentCollection(DataNamespace namespace, String name, DocumentStore store) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.name = requireName(name);
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public DataNamespace namespace() {
        return namespace;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public DocumentKey key(String id) {
        return new DocumentKey(namespace.name(), name, Objects.requireNonNull(id, "id"));
    }

    @Override
    public Document document(String id) {
        return new StoreBackedDocument(store, key(id));
    }

    @Override
    public CompletionStage<Long> count() {
        return store.count(namespace.name(), name);
    }

    @Override
    public CompletionStage<List<String>> listIds() {
        return store.listIds(namespace.name(), name);
    }

    private static String requireName(String value) {
        Objects.requireNonNull(value, "name");
        if (value.isBlank()) {
            throw new IllegalArgumentException("collection cannot be blank");
        }
        return value;
    }
}
