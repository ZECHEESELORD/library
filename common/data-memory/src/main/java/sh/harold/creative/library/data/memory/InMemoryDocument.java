package sh.harold.creative.library.data.memory;

import sh.harold.creative.library.data.Document;
import sh.harold.creative.library.data.DocumentKey;
import sh.harold.creative.library.data.DocumentPatch;
import sh.harold.creative.library.data.DocumentSnapshot;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;

final class InMemoryDocument implements Document {

    private final InMemoryDocumentStore store;
    private final DocumentKey key;

    InMemoryDocument(InMemoryDocumentStore store, DocumentKey key) {
        this.store = Objects.requireNonNull(store, "store");
        this.key = Objects.requireNonNull(key, "key");
    }

    @Override
    public DocumentKey key() {
        return key;
    }

    @Override
    public boolean exists() {
        return store.readSnapshot(key).exists();
    }

    @Override
    public <T> Optional<T> get(String path, Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value = PathMaps.read(store.readSnapshot(key).data(), path);
        if (type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    @Override
    public DocumentSnapshot snapshot() {
        return store.readSnapshot(key);
    }

    @Override
    public CompletionStage<DocumentSnapshot> snapshotAsync() {
        return CompletableFuture.completedFuture(snapshot());
    }

    @Override
    public CompletionStage<Void> set(String path, Object value) {
        store.updateSnapshot(key, current -> {
            PathMaps.write(current, path, value);
            return current;
        });
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> remove(String path) {
        if (!exists()) {
            return CompletableFuture.completedFuture(null);
        }
        store.updateSnapshot(key, current -> {
            PathMaps.remove(current, path);
            return current;
        });
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> overwrite(Map<String, Object> data) {
        store.writeNow(key, data);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> update(UnaryOperator<Map<String, Object>> mutator) {
        store.updateSnapshot(key, Objects.requireNonNull(mutator, "mutator"));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> patch(DocumentPatch patch) {
        Objects.requireNonNull(patch, "patch");
        store.patchNow(key, patch);
        return CompletableFuture.completedFuture(null);
    }
}
