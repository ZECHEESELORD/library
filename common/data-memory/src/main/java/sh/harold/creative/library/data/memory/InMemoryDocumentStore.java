package sh.harold.creative.library.data.memory;

import sh.harold.creative.library.data.DocumentKey;
import sh.harold.creative.library.data.DocumentPatch;
import sh.harold.creative.library.data.DocumentSnapshot;
import sh.harold.creative.library.data.DocumentStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

public final class InMemoryDocumentStore implements DocumentStore {

    private final Map<String, Map<String, Map<String, Object>>> collections = new ConcurrentHashMap<>();

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
        collections.clear();
    }

    DocumentSnapshot readSnapshot(DocumentKey key) {
        Objects.requireNonNull(key, "key");
        Map<String, Object> current = collection(key.collection()).get(key.id());
        return new DocumentSnapshot(key, current == null ? Map.of() : PathMaps.deepCopy(current), current != null);
    }

    void writeNow(DocumentKey key, Map<String, Object> data) {
        Objects.requireNonNull(key, "key");
        collection(key.collection()).put(key.id(), PathMaps.deepCopy(Objects.requireNonNullElse(data, Map.of())));
    }

    DocumentSnapshot updateSnapshot(DocumentKey key, UnaryOperator<Map<String, Object>> mutator) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(mutator, "mutator");
        Map<String, Map<String, Object>> collection = collection(key.collection());
        collection.compute(key.id(), (ignored, current) -> {
            Map<String, Object> editable = current == null ? new LinkedHashMap<>() : PathMaps.deepCopy(current);
            Map<String, Object> updated = Objects.requireNonNull(mutator.apply(editable), "mutator result");
            return PathMaps.deepCopy(updated);
        });
        return readSnapshot(key);
    }

    void patchNow(DocumentKey key, DocumentPatch patch) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(patch, "patch");
        if (!readSnapshot(key).exists() && patch.setValues().isEmpty()) {
            return;
        }
        updateSnapshot(key, current -> {
            for (Map.Entry<String, Object> entry : patch.setValues().entrySet()) {
                PathMaps.write(current, entry.getKey(), entry.getValue());
            }
            for (String path : patch.removePaths()) {
                PathMaps.remove(current, path);
            }
            return current;
        });
    }

    boolean deleteNow(DocumentKey key) {
        Objects.requireNonNull(key, "key");
        return collection(key.collection()).remove(key.id()) != null;
    }

    List<DocumentSnapshot> allSnapshots(String collection) {
        Map<String, Map<String, Object>> documents = collection(collection);
        List<DocumentSnapshot> snapshots = new ArrayList<>(documents.size());
        for (Map.Entry<String, Map<String, Object>> entry : documents.entrySet()) {
            snapshots.add(new DocumentSnapshot(
                    new DocumentKey(collection, entry.getKey()),
                    PathMaps.deepCopy(entry.getValue()),
                    true
            ));
        }
        return snapshots;
    }

    long countNow(String collection) {
        return collection(collection).size();
    }

    private Map<String, Map<String, Object>> collection(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("collection cannot be blank");
        }
        return collections.computeIfAbsent(name, ignored -> new ConcurrentHashMap<>());
    }
}
