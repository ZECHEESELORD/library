package sh.harold.creative.library.data.mongodb;

import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.data.DocumentKey;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MongoDocumentStoreTest {

    @Test
    void constructorIsLazyAndInitializesOnFirstOperation() throws Exception {
        MongoHarness harness = new MongoHarness();

        try (MongoDocumentStore store = new MongoDocumentStore(harness.client, "test_db", Runnable::run)) {
            assertEquals(0, harness.runCommandCalls.get());
            assertEquals(0, harness.createIndexCalls.get());

            long count = store.count("plugin-a", "players").toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertEquals(7L, count);
            assertEquals(1, harness.runCommandCalls.get());
            assertEquals(1, harness.createIndexCalls.get());
            assertEquals(1, harness.countCalls.get());
        }
    }

    @Test
    void duplicateKeyInsertFailuresReturnFalse() throws Exception {
        MongoHarness harness = new MongoHarness();
        harness.targetCollection.insertException.set(duplicateKeyException());

        try (MongoDocumentStore store = new MongoDocumentStore(harness.client, "test_db", Runnable::run)) {
            boolean result = invokeApplyInsert(store);
            assertFalse(result);
        }
    }

    @Test
    void nonDuplicateInsertFailuresPropagate() throws Exception {
        MongoHarness harness = new MongoHarness();
        harness.targetCollection.insertException.set(validationException());

        try (MongoDocumentStore store = new MongoDocumentStore(harness.client, "test_db", Runnable::run)) {
            assertThrows(MongoWriteException.class, () -> invokeApplyInsert(store));
        }
    }

    @Test
    void ownedExecutorClosesGracefully() throws Exception {
        MongoDocumentStore store = new MongoDocumentStore("mongodb://localhost:27017", "test_" + UUID.randomUUID().toString().replace("-", ""));
        ExecutorService executor = ownedExecutor(store);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();

        Future<?> blocker = executor.submit(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });

        assertTrue(started.await(5, TimeUnit.SECONDS));

        CompletableFuture<Void> closeFuture = CompletableFuture.runAsync(store::close);
        Thread.sleep(200L);

        assertFalse(closeFuture.isDone());
        assertFalse(interrupted.get());

        release.countDown();
        closeFuture.get(5, TimeUnit.SECONDS);
        assertTrue(blocker.isDone());
    }

    private static boolean invokeApplyInsert(MongoDocumentStore store) throws Exception {
        Method method = MongoDocumentStore.class.getDeclaredMethod(
                "applyInsert",
                DocumentKey.class,
                Map.class,
                long.class
        );
        method.setAccessible(true);
        try {
            return (boolean) method.invoke(
                    store,
                    new DocumentKey("plugin-a", "players", "alpha"),
                    Map.of("name", "Alice"),
                    1L
            );
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        }
    }

    private static ExecutorService ownedExecutor(MongoDocumentStore store) throws Exception {
        Field field = MongoDocumentStore.class.getDeclaredField("ownedExecutor");
        field.setAccessible(true);
        return (ExecutorService) field.get(store);
    }

    private static MongoWriteException duplicateKeyException() {
        return new MongoWriteException(new WriteError(11000, "duplicate key", new BsonDocument()), new ServerAddress());
    }

    private static MongoWriteException validationException() {
        return new MongoWriteException(new WriteError(121, "document validation failed", new BsonDocument()), new ServerAddress());
    }

    private static final class MongoHarness {

        private final AtomicInteger runCommandCalls = new AtomicInteger();
        private final AtomicInteger createIndexCalls = new AtomicInteger();
        private final AtomicInteger countCalls = new AtomicInteger();
        private final CollectionHandler targetCollection = new CollectionHandler(this, false);
        private final CollectionHandler revisionCollection = new CollectionHandler(this, true);
        private final MongoDatabase database;
        private final MongoClient client;

        private MongoHarness() {
            this.database = (MongoDatabase) Proxy.newProxyInstance(
                    MongoDatabase.class.getClassLoader(),
                    new Class<?>[]{MongoDatabase.class},
                    new DatabaseHandler()
            );
            this.client = (MongoClient) Proxy.newProxyInstance(
                    MongoClient.class.getClassLoader(),
                    new Class<?>[]{MongoClient.class},
                    new ClientHandler()
            );
        }

        private final class ClientHandler implements InvocationHandler {

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                return switch (method.getName()) {
                    case "getDatabase" -> database;
                    case "close" -> null;
                    case "toString" -> "MongoHarnessClient";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            }
        }

        private final class DatabaseHandler implements InvocationHandler {

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                return switch (method.getName()) {
                    case "runCommand" -> {
                        runCommandCalls.incrementAndGet();
                        yield new org.bson.Document("ok", 1);
                    }
                    case "getCollection" -> collection(String.valueOf(args[0]));
                    case "toString" -> "MongoHarnessDatabase";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            }
        }

        private MongoCollection<org.bson.Document> collection(String name) {
            if ("__data_revisions".equals(name)) {
                return revisionCollection.proxy;
            }
            return targetCollection.proxy;
        }
    }

    private static final class CollectionHandler implements InvocationHandler {

        private final MongoHarness owner;
        private final boolean revisionCollection;
        private final AtomicReference<RuntimeException> insertException = new AtomicReference<>();
        private final MongoCollection<org.bson.Document> proxy;

        private CollectionHandler(MongoHarness owner, boolean revisionCollection) {
            this.owner = owner;
            this.revisionCollection = revisionCollection;
            this.proxy = (MongoCollection<org.bson.Document>) Proxy.newProxyInstance(
                    MongoCollection.class.getClassLoader(),
                    new Class<?>[]{MongoCollection.class},
                    this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "countDocuments" -> {
                    if (revisionCollection) {
                        throw new UnsupportedOperationException("countDocuments");
                    }
                    owner.countCalls.incrementAndGet();
                    yield 7L;
                }
                case "createIndex" -> {
                    if (!revisionCollection) {
                        throw new UnsupportedOperationException("createIndex");
                    }
                    owner.createIndexCalls.incrementAndGet();
                    yield "revision-index";
                }
                case "insertOne" -> {
                    RuntimeException exception = insertException.get();
                    if (exception != null) {
                        throw exception;
                    }
                    yield null;
                }
                case "replaceOne", "deleteOne", "updateOne" -> throw new UnsupportedOperationException(method.getName());
                case "find" -> throw new UnsupportedOperationException("find");
                case "toString" -> revisionCollection ? "MongoRevisionCollection" : "MongoTargetCollection";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }
}
