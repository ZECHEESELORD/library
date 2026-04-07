package sh.harold.creative.library.data.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import sh.harold.creative.library.data.DocumentKey;
import sh.harold.creative.library.data.DocumentPatch;
import sh.harold.creative.library.data.DocumentSnapshot;
import sh.harold.creative.library.data.DocumentStore;
import sh.harold.creative.library.data.DocumentValues;
import sh.harold.creative.library.data.WriteCondition;
import sh.harold.creative.library.data.WriteResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MongoDocumentStore implements DocumentStore {

    private static final String REVISION_FIELD = "_rev";
    private static final String DATA_FIELD = "data";
    private static final String REVISION_COLLECTION = "__data_revisions";

    private final MongoClient client;
    private final MongoDatabase database;
    private final Executor executor;
    private final ExecutorService ownedExecutor;
    private final boolean closeClient;

    public MongoDocumentStore(String connectionString, String databaseName) {
        this(MongoClients.create(connectionString), databaseName, null, true);
    }

    public MongoDocumentStore(String connectionString, String databaseName, Executor executor) {
        this(MongoClients.create(connectionString), databaseName, executor, true);
    }

    public MongoDocumentStore(MongoClient client, String databaseName) {
        this(client, databaseName, null, false);
    }

    public MongoDocumentStore(MongoClient client, String databaseName, Executor executor) {
        this(client, databaseName, executor, false);
    }

    static MongoDocumentStore owned(MongoClient client, String databaseName, Executor executor) {
        return new MongoDocumentStore(client, databaseName, executor, true);
    }

    private MongoDocumentStore(MongoClient client, String databaseName, Executor executor, boolean closeClient) {
        this.client = Objects.requireNonNull(client, "client");
        this.database = client.getDatabase(requireName(databaseName, "databaseName"));
        ExecutorService defaultExecutor = executor == null ? createExecutor() : null;
        this.executor = executor == null ? defaultExecutor : executor;
        this.ownedExecutor = defaultExecutor;
        this.closeClient = closeClient;
        validateConnection();
    }

    @Override
    public CompletionStage<DocumentSnapshot> read(DocumentKey key) {
        return CompletableFuture.supplyAsync(() -> readSync(key).snapshot(), executor);
    }

    @Override
    public CompletionStage<WriteResult> write(DocumentKey key, Map<String, Object> data, WriteCondition condition) {
        return CompletableFuture.supplyAsync(() ->
                mutate(key, condition, current -> MutationAction.write(DocumentValues.normalizeRoot(data))), executor);
    }

    @Override
    public CompletionStage<WriteResult> patch(DocumentKey key, DocumentPatch patch, WriteCondition condition) {
        return CompletableFuture.supplyAsync(() ->
                mutate(key, condition, current -> {
                    Map<String, Object> working = current.exists()
                            ? DocumentValues.deepCopyMap(current.data())
                            : new LinkedHashMap<>();
                    for (Map.Entry<String, Object> entry : patch.setValues().entrySet()) {
                        DocumentValues.writePath(working, entry.getKey(), entry.getValue());
                    }
                    for (String path : patch.removePaths()) {
                        DocumentValues.removePath(working, path);
                    }
                    if (!current.exists() && working.isEmpty()) {
                        return MutationAction.noop();
                    }
                    return MutationAction.write(working);
                }), executor);
    }

    @Override
    public CompletionStage<WriteResult> delete(DocumentKey key, WriteCondition condition) {
        return CompletableFuture.supplyAsync(() ->
                mutate(key, condition, current -> current.exists() ? MutationAction.delete() : MutationAction.noop()), executor);
    }

    @Override
    public CompletionStage<Long> count(String namespace, String collection) {
        return CompletableFuture.supplyAsync(() -> collection(namespace, collection).countDocuments(), executor);
    }

    @Override
    public CompletionStage<List<String>> listIds(String namespace, String collection) {
        return CompletableFuture.supplyAsync(() -> collection(namespace, collection)
                .find()
                .projection(new Document("_id", 1))
                .sort(Sorts.ascending("_id"))
                .map(document -> document.getString("_id"))
                .into(new ArrayList<>()), executor);
    }

    @Override
    public void close() {
        if (ownedExecutor != null) {
            ownedExecutor.shutdownNow();
        }
        if (closeClient) {
            client.close();
        }
    }

    private WriteResult mutate(DocumentKey key,
                               WriteCondition condition,
                               java.util.function.Function<DocumentSnapshot, MutationAction> mutation) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(mutation, "mutation");
        while (true) {
            StoredState current = readSync(key);
            DocumentSnapshot currentSnapshot = current.snapshot();
            if (!condition.matches(currentSnapshot)) {
                return WriteResult.conditionFailed(currentSnapshot);
            }

            MutationAction action = mutation.apply(currentSnapshot);
            if (action.isNoop()) {
                return WriteResult.applied(currentSnapshot);
            }

            long nextRevision = current.revision() + 1L;
            if (action.isDelete()) {
                if (applyDelete(key, current.revision())) {
                    recordRevision(key, nextRevision);
                    return WriteResult.applied(new DocumentSnapshot(key, Map.of(), false, Long.toString(nextRevision)));
                }
            } else if (!current.exists()) {
                if (applyInsert(key, action.data(), nextRevision)) {
                    recordRevision(key, nextRevision);
                    return WriteResult.applied(new DocumentSnapshot(key, action.data(), true, Long.toString(nextRevision)));
                }
            } else if (applyReplace(key, current.revision(), action.data(), nextRevision)) {
                recordRevision(key, nextRevision);
                return WriteResult.applied(new DocumentSnapshot(key, action.data(), true, Long.toString(nextRevision)));
            }

            StoredState latest = readSync(key);
            if (condition.expectedRevision().isPresent() || !condition.matches(latest.snapshot())) {
                return WriteResult.conditionFailed(latest.snapshot());
            }
        }
    }

    private StoredState readSync(DocumentKey key) {
        Objects.requireNonNull(key, "key");
        Document stored = collection(key.namespace(), key.collection())
                .find(Filters.eq("_id", key.id()))
                .first();
        if (stored == null) {
            return new StoredState(key, false, revision(key), Map.of());
        }
        long revision = stored.getLong(REVISION_FIELD);
        Object rawData = stored.get(DATA_FIELD);
        if (!(rawData instanceof Document document)) {
            throw new IllegalStateException("Mongo document is missing nested data map for " + key);
        }
        return new StoredState(key, true, revision, fromBsonMap(document));
    }

    private boolean applyInsert(DocumentKey key, Map<String, Object> data, long revision) {
        try {
            collection(key.namespace(), key.collection()).insertOne(storedDocument(key, data, revision));
            return true;
        } catch (com.mongodb.MongoWriteException exception) {
            return false;
        }
    }

    private boolean applyReplace(DocumentKey key, long currentRevision, Map<String, Object> data, long nextRevision) {
        UpdateResult result = collection(key.namespace(), key.collection()).replaceOne(
                Filters.and(Filters.eq("_id", key.id()), Filters.eq(REVISION_FIELD, currentRevision)),
                storedDocument(key, data, nextRevision)
        );
        return result.getModifiedCount() == 1L;
    }

    private boolean applyDelete(DocumentKey key, long currentRevision) {
        DeleteResult result = collection(key.namespace(), key.collection()).deleteOne(
                Filters.and(Filters.eq("_id", key.id()), Filters.eq(REVISION_FIELD, currentRevision))
        );
        return result.getDeletedCount() == 1L;
    }

    private void recordRevision(DocumentKey key, long revision) {
        revisionCollection().updateOne(
                Filters.eq("_id", revisionKey(key)),
                Updates.set("revision", revision),
                new UpdateOptions().upsert(true)
        );
    }

    private long revision(DocumentKey key) {
        Document stored = revisionCollection().find(Filters.eq("_id", revisionKey(key))).first();
        if (stored == null) {
            return 0L;
        }
        return ((Number) stored.getOrDefault("revision", 0L)).longValue();
    }

    private MongoCollection<Document> collection(String namespace, String collection) {
        return database.getCollection(collectionName(namespace, collection));
    }

    private MongoCollection<Document> revisionCollection() {
        return database.getCollection(REVISION_COLLECTION);
    }

    private Document storedDocument(DocumentKey key, Map<String, Object> data, long revision) {
        return new Document("_id", key.id())
                .append(REVISION_FIELD, revision)
                .append(DATA_FIELD, toBsonMap(data));
    }

    private Document toBsonMap(Map<String, Object> values) {
        Document document = new Document();
        for (Map.Entry<String, Object> entry : DocumentValues.normalizeRoot(values).entrySet()) {
            document.put(entry.getKey(), toBsonValue(entry.getValue()));
        }
        return document;
    }

    private Object toBsonValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return toBsonMap((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(toBsonValue(element));
            }
            return copy;
        }
        return value;
    }

    private Map<String, Object> fromBsonMap(Document document) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            map.put(entry.getKey(), fromBsonValue(entry.getValue()));
        }
        return map;
    }

    private Object fromBsonValue(Object value) {
        if (value instanceof Document document) {
            return fromBsonMap(document);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(fromBsonValue(element));
            }
            return copy;
        }
        return value;
    }

    private String revisionKey(DocumentKey key) {
        return collectionName(key.namespace(), key.collection()) + "::" + encodeSegment(key.id());
    }

    private String collectionName(String namespace, String collection) {
        return encodeSegment(namespace) + "__" + encodeSegment(collection);
    }

    private void validateConnection() {
        database.runCommand(new Document("ping", 1));
        revisionCollection().createIndex(new Document("_id", 1));
    }

    private static ExecutorService createExecutor() {
        return Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "data-mongodb");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static String encodeSegment(String value) {
        requireName(value, "segment");
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isLetterOrDigit(character) || character == '-' || character == '_' || character == '.') {
                builder.append(character);
                continue;
            }
            builder.append('%');
            builder.append(String.format("%04x", (int) character));
        }
        return builder.toString();
    }

    private static String requireName(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return value;
    }

    private record StoredState(DocumentKey key, boolean exists, long revision, Map<String, Object> data) {

        private DocumentSnapshot snapshot() {
            return new DocumentSnapshot(key, data, exists, Long.toString(revision));
        }
    }

    private record MutationAction(boolean noopAction, boolean deleteAction, Map<String, Object> data) {

        private static MutationAction noop() {
            return new MutationAction(true, false, Map.of());
        }

        private static MutationAction delete() {
            return new MutationAction(false, true, Map.of());
        }

        private static MutationAction write(Map<String, Object> data) {
            return new MutationAction(false, false, DocumentValues.normalizeRoot(data));
        }

        private boolean isNoop() {
            return noopAction;
        }

        private boolean isDelete() {
            return deleteAction;
        }
    }
}
