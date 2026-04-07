package sh.harold.creative.library.data.core;

import sh.harold.creative.library.data.DataNamespace;
import sh.harold.creative.library.data.Document;
import sh.harold.creative.library.data.DocumentCollection;
import sh.harold.creative.library.data.DocumentKey;
import sh.harold.creative.library.data.DocumentPatch;
import sh.harold.creative.library.data.DocumentSnapshot;
import sh.harold.creative.library.data.SharedDataAccess;
import sh.harold.creative.library.data.WriteCondition;
import sh.harold.creative.library.data.WriteResult;
import sh.harold.creative.library.data.WriteStatus;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class ReflectiveSharedDataClient {

    private ReflectiveSharedDataClient() {
    }

    public static SharedDataAccess connect(Object endpoint, String callerId) {
        return new RemoteAccess(new RemoteEndpoint(endpoint), requireName(callerId));
    }

    private static String requireName(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
        return value;
    }

    private static final class RemoteAccess implements SharedDataAccess {

        private final RemoteEndpoint endpoint;
        private final String callerId;
        private volatile DataNamespace defaultNamespace;

        private RemoteAccess(RemoteEndpoint endpoint, String callerId) {
            this.endpoint = endpoint;
            this.callerId = callerId;
        }

        @Override
        public DataNamespace defaultNamespace() {
            DataNamespace current = defaultNamespace;
            if (current != null) {
                return current;
            }
            DataNamespace created = new RemoteNamespace(endpoint, callerId, endpoint.defaultNamespace(callerId));
            defaultNamespace = created;
            return created;
        }

        @Override
        public Optional<DataNamespace> namespace(String name) {
            String normalized = requireName(name);
            if (defaultNamespace().name().equals(normalized)) {
                return Optional.of(defaultNamespace());
            }
            if (!endpoint.canAccessNamespace(callerId, normalized)) {
                return Optional.empty();
            }
            return Optional.of(new RemoteNamespace(endpoint, callerId, normalized));
        }
    }

    private static final class RemoteNamespace implements DataNamespace {

        private final RemoteEndpoint endpoint;
        private final String callerId;
        private final String name;

        private RemoteNamespace(RemoteEndpoint endpoint, String callerId, String name) {
            this.endpoint = endpoint;
            this.callerId = callerId;
            this.name = requireName(name);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public DocumentCollection collection(String name) {
            return new RemoteCollection(endpoint, callerId, this.name, requireName(name));
        }
    }

    private static final class RemoteCollection implements DocumentCollection {

        private final RemoteEndpoint endpoint;
        private final String callerId;
        private final RemoteNamespace namespace;
        private final String name;

        private RemoteCollection(RemoteEndpoint endpoint, String callerId, String namespace, String name) {
            this.endpoint = endpoint;
            this.callerId = callerId;
            this.namespace = new RemoteNamespace(endpoint, callerId, namespace);
            this.name = name;
        }

        @Override
        public DataNamespace namespace() {
            return namespace;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public DocumentKey key(String id) {
            return new DocumentKey(namespace.name(), name, Objects.requireNonNull(id, "id"));
        }

        @Override
        public Document document(String id) {
            return new RemoteDocument(endpoint, callerId, key(id));
        }

        @Override
        public CompletionStage<Long> count() {
            return endpoint.count(callerId, namespace.name(), name);
        }

        @Override
        public CompletionStage<List<String>> listIds() {
            return endpoint.listIds(callerId, namespace.name(), name);
        }
    }

    private static final class RemoteDocument implements Document {

        private final RemoteEndpoint endpoint;
        private final String callerId;
        private final DocumentKey key;

        private RemoteDocument(RemoteEndpoint endpoint, String callerId, DocumentKey key) {
            this.endpoint = endpoint;
            this.callerId = callerId;
            this.key = key;
        }

        @Override
        public DocumentKey key() {
            return key;
        }

        @Override
        public CompletionStage<DocumentSnapshot> read() {
            return endpoint.read(callerId, key);
        }

        @Override
        public CompletionStage<WriteResult> write(Map<String, Object> data, WriteCondition condition) {
            return endpoint.write(callerId, key, data, condition);
        }

        @Override
        public CompletionStage<WriteResult> patch(DocumentPatch patch, WriteCondition condition) {
            return endpoint.patch(callerId, key, patch, condition);
        }

        @Override
        public CompletionStage<WriteResult> delete(WriteCondition condition) {
            return endpoint.delete(callerId, key, condition);
        }
    }

    private static final class RemoteEndpoint {

        private final Object target;
        private final Method defaultNamespace;
        private final Method canAccessNamespace;
        private final Method count;
        private final Method listIds;
        private final Method read;
        private final Method write;
        private final Method patch;
        private final Method delete;

        private RemoteEndpoint(Object target) {
            this.target = Objects.requireNonNull(target, "target");
            this.defaultNamespace = method("defaultNamespace", String.class);
            this.canAccessNamespace = method("canAccessNamespace", String.class, String.class);
            this.count = method("count", String.class, String.class, String.class);
            this.listIds = method("listIds", String.class, String.class, String.class);
            this.read = method("read", String.class, String.class, String.class, String.class);
            this.write = method("write", String.class, String.class, String.class, String.class, Map.class, Map.class);
            this.patch = method("patch", String.class, String.class, String.class, String.class, Map.class, Map.class);
            this.delete = method("delete", String.class, String.class, String.class, String.class, Map.class);
        }

        private String defaultNamespace(String callerId) {
            return (String) invoke(defaultNamespace, callerId);
        }

        private boolean canAccessNamespace(String callerId, String namespace) {
            return (Boolean) invoke(canAccessNamespace, callerId, namespace);
        }

        private CompletionStage<Long> count(String callerId, String namespace, String collection) {
            return invokeStage(count, callerId, namespace, collection)
                    .thenApply(value -> ((Number) value).longValue());
        }

        private CompletionStage<List<String>> listIds(String callerId, String namespace, String collection) {
            return invokeStage(listIds, callerId, namespace, collection)
                    .thenApply(value -> ((List<?>) value).stream().map(String::valueOf).toList());
        }

        private CompletionStage<DocumentSnapshot> read(String callerId, DocumentKey key) {
            return invokeStage(read, callerId, key.namespace(), key.collection(), key.id())
                    .thenApply(value -> snapshot((Map<String, Object>) value));
        }

        private CompletionStage<WriteResult> write(String callerId,
                                                   DocumentKey key,
                                                   Map<String, Object> data,
                                                   WriteCondition condition) {
            return invokeStage(write,
                    callerId,
                    key.namespace(),
                    key.collection(),
                    key.id(),
                    data,
                    conditionPayload(condition))
                    .thenApply(value -> result((Map<String, Object>) value));
        }

        private CompletionStage<WriteResult> patch(String callerId,
                                                   DocumentKey key,
                                                   DocumentPatch patchValue,
                                                   WriteCondition condition) {
            return invokeStage(patch,
                    callerId,
                    key.namespace(),
                    key.collection(),
                    key.id(),
                    patchPayload(patchValue),
                    conditionPayload(condition))
                    .thenApply(value -> result((Map<String, Object>) value));
        }

        private CompletionStage<WriteResult> delete(String callerId, DocumentKey key, WriteCondition condition) {
            return invokeStage(delete,
                    callerId,
                    key.namespace(),
                    key.collection(),
                    key.id(),
                    conditionPayload(condition))
                    .thenApply(value -> result((Map<String, Object>) value));
        }

        private Method method(String name, Class<?>... parameterTypes) {
            try {
                return target.getClass().getMethod(name, parameterTypes);
            } catch (NoSuchMethodException exception) {
                throw new IllegalStateException("shared data bridge is missing method " + name, exception);
            }
        }

        private Object invoke(Method method, Object... arguments) {
            try {
                return method.invoke(target, arguments);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("shared data bridge method is not accessible: " + method.getName(), exception);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("shared data bridge call failed: " + method.getName(), cause);
            }
        }

        private CompletionStage<Object> invokeStage(Method method, Object... arguments) {
            return (CompletionStage<Object>) invoke(method, arguments);
        }

        private static DocumentSnapshot snapshot(Map<String, Object> payload) {
            Map<String, Object> data = (Map<String, Object>) Objects.requireNonNull(payload.get("data"), "payload data");
            return new DocumentSnapshot(
                    new DocumentKey(
                            String.valueOf(payload.get("namespace")),
                            String.valueOf(payload.get("collection")),
                            String.valueOf(payload.get("id"))
                    ),
                    data,
                    Boolean.TRUE.equals(payload.get("exists")),
                    String.valueOf(payload.get("revision"))
            );
        }

        private static WriteResult result(Map<String, Object> payload) {
            WriteStatus status = WriteStatus.valueOf(String.valueOf(payload.get("status")));
            Object snapshotPayload = payload.get("snapshot");
            if (!(snapshotPayload instanceof Map<?, ?> snapshotMap)) {
                return status == WriteStatus.APPLIED
                        ? WriteResult.appliedWithoutSnapshot()
                        : WriteResult.conditionFailedWithoutSnapshot();
            }
            DocumentSnapshot snapshot = snapshot((Map<String, Object>) snapshotMap);
            return status == WriteStatus.APPLIED
                    ? WriteResult.applied(snapshot)
                    : WriteResult.conditionFailed(snapshot);
        }

        private static Map<String, Object> conditionPayload(WriteCondition condition) {
            Objects.requireNonNull(condition, "condition");
            Map<String, Object> payload = new LinkedHashMap<>();
            condition.expectedExists().ifPresent(value -> payload.put("exists", value));
            condition.expectedRevision().ifPresent(value -> payload.put("revision", value));
            return payload;
        }

        private static Map<String, Object> patchPayload(DocumentPatch patch) {
            Objects.requireNonNull(patch, "patch");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("set", patch.setValues());
            payload.put("remove", List.copyOf(patch.removePaths()));
            return payload;
        }
    }
}
