package sh.harold.creative.library.data.memory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import sh.harold.creative.library.data.DocumentKey;
import sh.harold.creative.library.data.DocumentPatch;
import sh.harold.creative.library.data.DocumentSnapshot;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;

final class CaffeineDocumentStore implements LocalDocumentStore {

    private static final long DEFAULT_MAXIMUM_SIZE = 10_000L;

    private final LocalDocumentStore delegate;
    private final Cache<DocumentKey, DocumentSnapshot> cache;

    CaffeineDocumentStore(LocalDocumentStore delegate) {
        this(delegate, DEFAULT_MAXIMUM_SIZE);
    }

    CaffeineDocumentStore(LocalDocumentStore delegate, long maximumSize) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (maximumSize <= 0L) {
            throw new IllegalArgumentException("maximumSize must be positive");
        }
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .build();
    }

    @Override
    public CompletionStage<DocumentSnapshot> read(DocumentKey key) {
        return CompletableFuture.completedFuture(readSnapshot(key));
    }

    @Override
    public CompletionStage<Void> write(DocumentKey key, Map<String, Object> data) {
        writeNow(key, data);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<DocumentSnapshot> update(DocumentKey key, UnaryOperator<Map<String, Object>> mutator) {
        return CompletableFuture.completedFuture(updateSnapshot(key, mutator));
    }

    @Override
    public CompletionStage<Boolean> delete(DocumentKey key) {
        return CompletableFuture.completedFuture(deleteNow(key));
    }

    @Override
    public CompletionStage<List<DocumentSnapshot>> all(String collection) {
        return CompletableFuture.completedFuture(allSnapshots(collection));
    }

    @Override
    public CompletionStage<Long> count(String collection) {
        return CompletableFuture.completedFuture(countNow(collection));
    }

    @Override
    public void close() {
        cache.invalidateAll();
        delegate.close();
    }

    @Override
    public DocumentSnapshot readSnapshot(DocumentKey key) {
        Objects.requireNonNull(key, "key");
        return cache.get(key, delegate::readSnapshot);
    }

    @Override
    public void writeNow(DocumentKey key, Map<String, Object> data) {
        Objects.requireNonNull(key, "key");
        delegate.writeNow(key, data);
        cache.put(key, new DocumentSnapshot(key, PathMaps.deepCopy(Objects.requireNonNullElse(data, Map.of())), true));
    }

    @Override
    public DocumentSnapshot updateSnapshot(DocumentKey key, UnaryOperator<Map<String, Object>> mutator) {
        Objects.requireNonNull(key, "key");
        DocumentSnapshot snapshot = delegate.updateSnapshot(key, mutator);
        cache.put(key, snapshot);
        return snapshot;
    }

    @Override
    public void patchNow(DocumentKey key, DocumentPatch patch) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(patch, "patch");
        delegate.patchNow(key, patch);
        cache.put(key, delegate.readSnapshot(key));
    }

    @Override
    public boolean deleteNow(DocumentKey key) {
        Objects.requireNonNull(key, "key");
        boolean deleted = delegate.deleteNow(key);
        cache.invalidate(key);
        return deleted;
    }

    @Override
    public List<DocumentSnapshot> allSnapshots(String collection) {
        return delegate.allSnapshots(collection);
    }

    @Override
    public long countNow(String collection) {
        return delegate.countNow(collection);
    }
}
