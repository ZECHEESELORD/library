package sh.harold.creative.library.data.memory;

import sh.harold.creative.library.data.DocumentKey;
import sh.harold.creative.library.data.DocumentPatch;
import sh.harold.creative.library.data.DocumentSnapshot;
import sh.harold.creative.library.data.DocumentStore;
import sh.harold.creative.library.data.DocumentValues;
import sh.harold.creative.library.data.WriteCondition;
import sh.harold.creative.library.data.WriteResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryDocumentStore implements DocumentStore {

    private final Map<String, Map<String, Map<String, StoredDocument>>> namespaces = new ConcurrentHashMap<>();
    private final Map<DocumentKey, Long> revisions = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<DocumentSnapshot> read(DocumentKey key) {
        return CompletableFuture.completedFuture(readSnapshot(key));
    }

    @Override
    public CompletionStage<WriteResult> write(DocumentKey key, Map<String, Object> data, WriteCondition condition) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(condition, "condition");
        Map<String, StoredDocument> documents = documents(key.namespace(), key.collection());
        synchronized (documents) {
            StoredDocument current = documents.get(key.id());
            DocumentSnapshot currentSnapshot = snapshot(key, current);
            if (!condition.matches(currentSnapshot)) {
                return CompletableFuture.completedFuture(WriteResult.conditionFailed(currentSnapshot));
            }

            long nextRevision = nextRevision(key);
            StoredDocument updated = new StoredDocument(DocumentValues.normalizeRoot(data), nextRevision);
            documents.put(key.id(), updated);
            return CompletableFuture.completedFuture(WriteResult.applied(snapshot(key, updated)));
        }
    }

    @Override
    public CompletionStage<WriteResult> patch(DocumentKey key, DocumentPatch patch, WriteCondition condition) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(patch, "patch");
        Objects.requireNonNull(condition, "condition");
        Map<String, StoredDocument> documents = documents(key.namespace(), key.collection());
        synchronized (documents) {
            StoredDocument current = documents.get(key.id());
            DocumentSnapshot currentSnapshot = snapshot(key, current);
            if (!condition.matches(currentSnapshot)) {
                return CompletableFuture.completedFuture(WriteResult.conditionFailed(currentSnapshot));
            }

            Map<String, Object> working = current == null
                    ? new LinkedHashMap<>()
                    : DocumentValues.deepCopyMap(current.data());
            for (Map.Entry<String, Object> entry : patch.setValues().entrySet()) {
                DocumentValues.writePath(working, entry.getKey(), entry.getValue());
            }
            for (String path : patch.removePaths()) {
                DocumentValues.removePath(working, path);
            }

            if (current == null && working.isEmpty()) {
                return CompletableFuture.completedFuture(WriteResult.applied(currentSnapshot));
            }

            long nextRevision = nextRevision(key);
            StoredDocument updated = new StoredDocument(working, nextRevision);
            documents.put(key.id(), updated);
            return CompletableFuture.completedFuture(WriteResult.applied(snapshot(key, updated)));
        }
    }

    @Override
    public CompletionStage<WriteResult> delete(DocumentKey key, WriteCondition condition) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(condition, "condition");
        Map<String, StoredDocument> documents = documents(key.namespace(), key.collection());
        synchronized (documents) {
            StoredDocument current = documents.get(key.id());
            DocumentSnapshot currentSnapshot = snapshot(key, current);
            if (!condition.matches(currentSnapshot)) {
                return CompletableFuture.completedFuture(WriteResult.conditionFailed(currentSnapshot));
            }

            if (current == null) {
                return CompletableFuture.completedFuture(WriteResult.applied(currentSnapshot));
            }

            long nextRevision = nextRevision(key);
            documents.remove(key.id());
            return CompletableFuture.completedFuture(WriteResult.applied(new DocumentSnapshot(
                    key,
                    Map.of(),
                    false,
                    revisionString(nextRevision)
            )));
        }
    }

    @Override
    public CompletionStage<Long> count(String namespace, String collection) {
        return CompletableFuture.completedFuture((long) documents(namespace, collection).size());
    }

    @Override
    public void close() {
        namespaces.clear();
        revisions.clear();
    }

    private DocumentSnapshot readSnapshot(DocumentKey key) {
        Objects.requireNonNull(key, "key");
        StoredDocument current = documents(key.namespace(), key.collection()).get(key.id());
        return snapshot(key, current);
    }

    private DocumentSnapshot snapshot(DocumentKey key, StoredDocument current) {
        if (current == null) {
            long revision = revisions.getOrDefault(key, 0L);
            return new DocumentSnapshot(key, Map.of(), false, revisionString(revision));
        }
        return new DocumentSnapshot(key, current.data(), true, revisionString(current.revision()));
    }

    private long nextRevision(DocumentKey key) {
        return revisions.merge(key, 1L, Long::sum);
    }

    private String revisionString(long revision) {
        return Long.toString(revision);
    }

    private Map<String, StoredDocument> documents(String namespace, String collection) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(collection, "collection");
        if (namespace.isBlank()) {
            throw new IllegalArgumentException("namespace cannot be blank");
        }
        if (collection.isBlank()) {
            throw new IllegalArgumentException("collection cannot be blank");
        }
        return namespaces
                .computeIfAbsent(namespace, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(collection, ignored -> new ConcurrentHashMap<>());
    }

    private record StoredDocument(Map<String, Object> data, long revision) {

        private StoredDocument {
            data = DocumentValues.normalizeRoot(data);
        }
    }
}
