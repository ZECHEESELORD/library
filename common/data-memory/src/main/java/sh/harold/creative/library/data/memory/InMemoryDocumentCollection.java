package sh.harold.creative.library.data.memory;

import sh.harold.creative.library.data.Document;
import sh.harold.creative.library.data.DocumentCollection;
import sh.harold.creative.library.data.DocumentKey;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class InMemoryDocumentCollection implements DocumentCollection {

    private final String name;
    private final InMemoryDocumentStore store;

    InMemoryDocumentCollection(String name, InMemoryDocumentStore store) {
        this.name = requireName(name);
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public CompletionStage<Document> load(String id) {
        return CompletableFuture.completedFuture(new InMemoryDocument(store, key(id)));
    }

    @Override
    public CompletionStage<Document> put(String id, Map<String, Object> data) {
        store.writeNow(key(id), data);
        return load(id);
    }

    @Override
    public CompletionStage<Boolean> exists(String id) {
        return CompletableFuture.completedFuture(store.readSnapshot(key(id)).exists());
    }

    @Override
    public CompletionStage<Boolean> delete(String id) {
        return CompletableFuture.completedFuture(store.deleteNow(key(id)));
    }

    @Override
    public CompletionStage<List<Document>> all() {
        List<Document> result = store.allSnapshots(name).stream()
                .map(snapshot -> new InMemoryDocument(store, snapshot.key()))
                .map(Document.class::cast)
                .toList();
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletionStage<Long> count() {
        return CompletableFuture.completedFuture(store.countNow(name));
    }

    private DocumentKey key(String id) {
        return new DocumentKey(name, Objects.requireNonNull(id, "id"));
    }

    private static String requireName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        return name;
    }
}
