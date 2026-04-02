package sh.harold.creative.library.data.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import sh.harold.creative.library.data.DocumentKey;
import sh.harold.creative.library.data.DocumentPatch;
import sh.harold.creative.library.data.DocumentSnapshot;
import sh.harold.creative.library.data.DocumentStore;
import sh.harold.creative.library.data.WriteCondition;
import sh.harold.creative.library.data.WriteResult;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class CaffeineDocumentStore implements DocumentStore {

    private static final long DEFAULT_MAXIMUM_SIZE = 10_000L;
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(5L);

    private final DocumentStore delegate;
    private final Cache<DocumentKey, CompletableFuture<DocumentSnapshot>> cache;

    public CaffeineDocumentStore(DocumentStore delegate) {
        this(delegate, DEFAULT_MAXIMUM_SIZE, DEFAULT_TTL);
    }

    public CaffeineDocumentStore(DocumentStore delegate, long maximumSize, Duration ttl) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (maximumSize <= 0L) {
            throw new IllegalArgumentException("maximumSize must be positive");
        }
        Objects.requireNonNull(ttl, "ttl");
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(ttl)
                .build();
    }

    @Override
    public CompletionStage<DocumentSnapshot> read(DocumentKey key) {
        Objects.requireNonNull(key, "key");
        CompletableFuture<DocumentSnapshot> future = cache.get(key, ignored ->
                delegate.read(key).toCompletableFuture().whenComplete((snapshot, error) -> {
                    if (error != null) {
                        cache.invalidate(key);
                    }
                })
        );
        return future;
    }

    @Override
    public CompletionStage<WriteResult> write(DocumentKey key, Map<String, Object> data, WriteCondition condition) {
        return delegate.write(key, data, condition).thenApply(result -> updateCache(key, result));
    }

    @Override
    public CompletionStage<WriteResult> patch(DocumentKey key, DocumentPatch patch, WriteCondition condition) {
        return delegate.patch(key, patch, condition).thenApply(result -> updateCache(key, result));
    }

    @Override
    public CompletionStage<WriteResult> delete(DocumentKey key, WriteCondition condition) {
        return delegate.delete(key, condition).thenApply(result -> updateCache(key, result));
    }

    @Override
    public CompletionStage<Long> count(String namespace, String collection) {
        return delegate.count(namespace, collection);
    }

    @Override
    public void close() {
        cache.invalidateAll();
        delegate.close();
    }

    private WriteResult updateCache(DocumentKey key, WriteResult result) {
        result.snapshot().ifPresentOrElse(
                snapshot -> cache.put(key, CompletableFuture.completedFuture(snapshot)),
                () -> cache.invalidate(key)
        );
        return result;
    }
}
