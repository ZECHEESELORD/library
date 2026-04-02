package sh.harold.creative.library.data.core;

import sh.harold.creative.library.data.Document;
import sh.harold.creative.library.data.DocumentKey;
import sh.harold.creative.library.data.DocumentPatch;
import sh.harold.creative.library.data.DocumentSnapshot;
import sh.harold.creative.library.data.DocumentStore;
import sh.harold.creative.library.data.DocumentValues;
import sh.harold.creative.library.data.WriteCondition;
import sh.harold.creative.library.data.WriteResult;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

final class StoreBackedDocument implements Document {

    private final DocumentStore store;
    private final DocumentKey key;

    StoreBackedDocument(DocumentStore store, DocumentKey key) {
        this.store = Objects.requireNonNull(store, "store");
        this.key = Objects.requireNonNull(key, "key");
    }

    @Override
    public DocumentKey key() {
        return key;
    }

    @Override
    public CompletionStage<DocumentSnapshot> read() {
        return store.read(key);
    }

    @Override
    public CompletionStage<WriteResult> write(Map<String, Object> data, WriteCondition condition) {
        return store.write(key, DocumentValues.normalizeRoot(data), Objects.requireNonNull(condition, "condition"));
    }

    @Override
    public CompletionStage<WriteResult> patch(DocumentPatch patch, WriteCondition condition) {
        return store.patch(key, Objects.requireNonNull(patch, "patch"), Objects.requireNonNull(condition, "condition"));
    }

    @Override
    public CompletionStage<WriteResult> delete(WriteCondition condition) {
        return store.delete(key, Objects.requireNonNull(condition, "condition"));
    }
}
