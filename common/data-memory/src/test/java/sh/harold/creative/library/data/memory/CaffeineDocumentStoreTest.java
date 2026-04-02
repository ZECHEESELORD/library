package sh.harold.creative.library.data.memory;

import org.junit.jupiter.api.Test;
import sh.harold.creative.library.data.DocumentKey;
import sh.harold.creative.library.data.DocumentPatch;
import sh.harold.creative.library.data.DocumentSnapshot;
import sh.harold.creative.library.data.DocumentStore;
import sh.harold.creative.library.data.WriteCondition;
import sh.harold.creative.library.data.WriteResult;
import sh.harold.creative.library.data.core.CaffeineDocumentStore;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaffeineDocumentStoreTest {

    @Test
    void cachedReadAvoidsDelegateReload() throws Exception {
        CountingStore delegate = new CountingStore();
        DocumentStore store = new CaffeineDocumentStore(delegate, 100L, Duration.ofMinutes(1L));
        DocumentKey key = new DocumentKey("plugin-a", "players", "alpha");

        await(delegate.write(key, Map.of("name", "Alice"), WriteCondition.none()));

        DocumentSnapshot first = await(store.read(key));
        DocumentSnapshot second = await(store.read(key));

        assertTrue(first.exists());
        assertEquals("Alice", first.data().get("name"));
        assertEquals(first, second);
        assertEquals(1, delegate.readCount());
    }

    @Test
    void writesRefreshCacheWithoutExtraReload() throws Exception {
        CountingStore delegate = new CountingStore();
        DocumentStore store = new CaffeineDocumentStore(delegate, 100L, Duration.ofMinutes(1L));
        DocumentKey key = new DocumentKey("plugin-a", "players", "alpha");

        await(delegate.write(key, Map.of("name", "Alice"), WriteCondition.none()));
        await(store.read(key));

        await(store.write(key, Map.of("name", "Bob"), WriteCondition.none()));
        DocumentSnapshot snapshot = await(store.read(key));

        assertTrue(snapshot.exists());
        assertEquals("Bob", snapshot.data().get("name"));
        assertEquals(1, delegate.readCount());
    }

    @Test
    void patchRefreshesCacheToLatestSnapshot() throws Exception {
        CountingStore delegate = new CountingStore();
        DocumentStore store = new CaffeineDocumentStore(delegate, 100L, Duration.ofMinutes(1L));
        DocumentKey key = new DocumentKey("plugin-a", "players", "alpha");

        await(delegate.write(key, Map.of("profile", Map.of("rank", "MEMBER")), WriteCondition.none()));
        await(store.read(key));

        await(store.patch(key, new DocumentPatch().set("profile.rank", "ADMIN"), WriteCondition.none()));
        DocumentSnapshot snapshot = await(store.read(key));

        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) snapshot.data().get("profile");
        assertEquals("ADMIN", profile.get("rank"));
        assertEquals(1, delegate.readCount());
    }

    @Test
    void deleteRefreshesCachedEntry() throws Exception {
        CountingStore delegate = new CountingStore();
        DocumentStore store = new CaffeineDocumentStore(delegate, 100L, Duration.ofMinutes(1L));
        DocumentKey key = new DocumentKey("plugin-a", "players", "alpha");

        await(delegate.write(key, Map.of("name", "Alice"), WriteCondition.none()));
        await(store.read(key));
        assertEquals(1, delegate.readCount());

        await(store.delete(key, WriteCondition.none()));

        DocumentSnapshot afterDelete = await(store.read(key));
        assertFalse(afterDelete.exists());
        assertEquals(1, delegate.readCount());
    }

    private static final class CountingStore implements DocumentStore {

        private final InMemoryDocumentStore delegate = new InMemoryDocumentStore();
        private final AtomicInteger reads = new AtomicInteger();

        @Override
        public CompletionStage<DocumentSnapshot> read(DocumentKey key) {
            reads.incrementAndGet();
            return delegate.read(key);
        }

        @Override
        public CompletionStage<WriteResult> write(DocumentKey key, Map<String, Object> data, WriteCondition condition) {
            return delegate.write(key, data, condition);
        }

        @Override
        public CompletionStage<WriteResult> patch(DocumentKey key, DocumentPatch patch, WriteCondition condition) {
            return delegate.patch(key, patch, condition);
        }

        @Override
        public CompletionStage<WriteResult> delete(DocumentKey key, WriteCondition condition) {
            return delegate.delete(key, condition);
        }

        @Override
        public CompletionStage<Long> count(String namespace, String collection) {
            return delegate.count(namespace, collection);
        }

        @Override
        public void close() {
            delegate.close();
        }

        int readCount() {
            return reads.get();
        }
    }

    private static <T> T await(CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
}
