package sh.harold.creative.library.data.memory;

import org.junit.jupiter.api.Test;
import sh.harold.creative.library.data.DocumentKey;
import sh.harold.creative.library.data.DocumentPatch;
import sh.harold.creative.library.data.DocumentSnapshot;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaffeineDocumentStoreTest {

    @Test
    void cachedReadAvoidsDelegateReload() {
        CountingStore delegate = new CountingStore();
        LocalDocumentStore store = new CaffeineDocumentStore(delegate, 100L);
        DocumentKey key = new DocumentKey("players", "alpha");

        delegate.writeNow(key, Map.of("name", "Alice"));

        DocumentSnapshot first = store.readSnapshot(key);
        DocumentSnapshot second = store.readSnapshot(key);

        assertTrue(first.exists());
        assertEquals("Alice", first.data().get("name"));
        assertEquals(first, second);
        assertEquals(1, delegate.readCount());
    }

    @Test
    void writeRefreshesCacheWithoutExtraReload() {
        CountingStore delegate = new CountingStore();
        LocalDocumentStore store = new CaffeineDocumentStore(delegate, 100L);
        DocumentKey key = new DocumentKey("players", "alpha");

        delegate.writeNow(key, Map.of("name", "Alice"));
        store.readSnapshot(key);

        store.writeNow(key, Map.of("name", "Bob"));
        DocumentSnapshot snapshot = store.readSnapshot(key);

        assertTrue(snapshot.exists());
        assertEquals("Bob", snapshot.data().get("name"));
        assertEquals(1, delegate.readCount());
    }

    @Test
    void updateRefreshesCacheWithoutExtraReload() {
        CountingStore delegate = new CountingStore();
        LocalDocumentStore store = new CaffeineDocumentStore(delegate, 100L);
        DocumentKey key = new DocumentKey("players", "alpha");

        delegate.writeNow(key, Map.of("stats", Map.of("kills", 1)));
        store.readSnapshot(key);

        store.updateSnapshot(key, map -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> stats = (Map<String, Object>) map.get("stats");
            stats.put("kills", 2);
            return map;
        });

        DocumentSnapshot snapshot = store.readSnapshot(key);

        assertEquals(Map.of("kills", 2), snapshot.data().get("stats"));
        assertEquals(1, delegate.readCount());
    }

    @Test
    void patchRefreshesCacheToLatestSnapshot() {
        CountingStore delegate = new CountingStore();
        LocalDocumentStore store = new CaffeineDocumentStore(delegate, 100L);
        DocumentKey key = new DocumentKey("players", "alpha");

        delegate.writeNow(key, Map.of("profile", Map.of("rank", "MEMBER")));
        store.readSnapshot(key);

        store.patchNow(key, new DocumentPatch().set("profile.rank", "ADMIN"));
        DocumentSnapshot snapshot = store.readSnapshot(key);

        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) snapshot.data().get("profile");
        assertEquals("ADMIN", profile.get("rank"));
        assertEquals(2, delegate.readCount());
    }

    @Test
    void deleteInvalidatesCachedEntry() {
        CountingStore delegate = new CountingStore();
        LocalDocumentStore store = new CaffeineDocumentStore(delegate, 100L);
        DocumentKey key = new DocumentKey("players", "alpha");

        delegate.writeNow(key, Map.of("name", "Alice"));
        store.readSnapshot(key);
        assertEquals(1, delegate.readCount());

        assertTrue(store.deleteNow(key));

        DocumentSnapshot afterDelete = store.readSnapshot(key);
        assertFalse(afterDelete.exists());
        assertEquals(2, delegate.readCount());
    }

    private static final class CountingStore implements LocalDocumentStore {

        private final InMemoryDocumentStore delegate = new InMemoryDocumentStore();
        private final AtomicInteger reads = new AtomicInteger();

        @Override
        public DocumentSnapshot readSnapshot(DocumentKey key) {
            reads.incrementAndGet();
            return delegate.readSnapshot(key);
        }

        @Override
        public void writeNow(DocumentKey key, Map<String, Object> data) {
            delegate.writeNow(key, data);
        }

        @Override
        public DocumentSnapshot updateSnapshot(DocumentKey key, UnaryOperator<Map<String, Object>> mutator) {
            return delegate.updateSnapshot(key, mutator);
        }

        @Override
        public void patchNow(DocumentKey key, DocumentPatch patch) {
            delegate.patchNow(key, patch);
        }

        @Override
        public boolean deleteNow(DocumentKey key) {
            return delegate.deleteNow(key);
        }

        @Override
        public List<DocumentSnapshot> allSnapshots(String collection) {
            return delegate.allSnapshots(collection);
        }

        @Override
        public long countNow(String collection) {
            return delegate.countNow(collection);
        }

        @Override
        public CompletionStage<DocumentSnapshot> read(DocumentKey key) {
            return awaitable(readSnapshot(key));
        }

        @Override
        public CompletionStage<Void> write(DocumentKey key, Map<String, Object> data) {
            writeNow(key, data);
            return awaitable(null);
        }

        @Override
        public CompletionStage<DocumentSnapshot> update(DocumentKey key, UnaryOperator<Map<String, Object>> mutator) {
            return awaitable(updateSnapshot(key, mutator));
        }

        @Override
        public CompletionStage<Boolean> delete(DocumentKey key) {
            return awaitable(deleteNow(key));
        }

        @Override
        public CompletionStage<List<DocumentSnapshot>> all(String collection) {
            return awaitable(allSnapshots(collection));
        }

        @Override
        public CompletionStage<Long> count(String collection) {
            return awaitable(countNow(collection));
        }

        @Override
        public void close() {
            delegate.close();
        }

        int readCount() {
            return reads.get();
        }

        private static <T> CompletionStage<T> awaitable(T value) {
            return java.util.concurrent.CompletableFuture.completedFuture(value);
        }
    }
}
