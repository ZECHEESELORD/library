package sh.harold.creative.library.data.core;

import sh.harold.creative.library.data.DataNamespace;
import sh.harold.creative.library.data.Document;
import sh.harold.creative.library.data.DocumentPatch;
import sh.harold.creative.library.data.SharedDataAccess;
import sh.harold.creative.library.data.SharedDataProvider;
import sh.harold.creative.library.data.WriteCondition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

public final class ReflectiveSharedDataBridgeEndpoint {

    private final SharedDataProvider provider;

    public ReflectiveSharedDataBridgeEndpoint(SharedDataProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    public String defaultNamespace(String callerId) {
        return provider.access(callerId).defaultNamespace().name();
    }

    public boolean canAccessNamespace(String callerId, String namespace) {
        SharedDataAccess access = provider.access(callerId);
        return access.defaultNamespace().name().equals(namespace) || access.namespace(namespace).isPresent();
    }

    public CompletionStage<Long> count(String callerId, String namespace, String collection) {
        return namespace(callerId, namespace).collection(collection).count();
    }

    public CompletionStage<List<String>> listIds(String callerId, String namespace, String collection) {
        return namespace(callerId, namespace).collection(collection).listIds();
    }

    public CompletionStage<Map<String, Object>> read(String callerId, String namespace, String collection, String id) {
        return document(callerId, namespace, collection, id)
                .read()
                .thenApply(BridgePayloads::snapshot);
    }

    public CompletionStage<Map<String, Object>> write(String callerId,
                                                      String namespace,
                                                      String collection,
                                                      String id,
                                                      Map<String, Object> data,
                                                      Map<String, Object> conditionPayload) {
        return document(callerId, namespace, collection, id)
                .write(data, BridgePayloads.condition(conditionPayload))
                .thenApply(BridgePayloads::result);
    }

    public CompletionStage<Map<String, Object>> patch(String callerId,
                                                      String namespace,
                                                      String collection,
                                                      String id,
                                                      Map<String, Object> patchPayload,
                                                      Map<String, Object> conditionPayload) {
        return document(callerId, namespace, collection, id)
                .patch(BridgePayloads.patch(patchPayload), BridgePayloads.condition(conditionPayload))
                .thenApply(BridgePayloads::result);
    }

    public CompletionStage<Map<String, Object>> delete(String callerId,
                                                       String namespace,
                                                       String collection,
                                                       String id,
                                                       Map<String, Object> conditionPayload) {
        return document(callerId, namespace, collection, id)
                .delete(BridgePayloads.condition(conditionPayload))
                .thenApply(BridgePayloads::result);
    }

    private DataNamespace namespace(String callerId, String namespace) {
        SharedDataAccess access = provider.access(callerId);
        if (access.defaultNamespace().name().equals(namespace)) {
            return access.defaultNamespace();
        }
        return access.namespace(namespace)
                .orElseThrow(() -> new SecurityException("namespace not allowed: " + namespace));
    }

    private Document document(String callerId, String namespace, String collection, String id) {
        return namespace(callerId, namespace).collection(collection).document(id);
    }

    static final class BridgePayloads {

        private BridgePayloads() {
        }

        static Map<String, Object> snapshot(sh.harold.creative.library.data.DocumentSnapshot snapshot) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("namespace", snapshot.key().namespace());
            payload.put("collection", snapshot.key().collection());
            payload.put("id", snapshot.key().id());
            payload.put("exists", snapshot.exists());
            payload.put("revision", snapshot.revision());
            payload.put("data", snapshot.data());
            return payload;
        }

        static Map<String, Object> result(sh.harold.creative.library.data.WriteResult result) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", result.status().name());
            payload.put("snapshot", result.snapshot().map(BridgePayloads::snapshot).orElse(null));
            return payload;
        }

        static WriteCondition condition(Map<String, Object> payload) {
            if (payload == null || payload.isEmpty()) {
                return WriteCondition.none();
            }
            WriteCondition condition = WriteCondition.none();
            Object exists = payload.get("exists");
            if (exists instanceof Boolean value) {
                condition = value ? condition.requireExists() : condition.requireNotExists();
            }
            Object revision = payload.get("revision");
            if (revision instanceof String value) {
                condition = condition.withExpectedRevision(value);
            }
            return condition;
        }

        static DocumentPatch patch(Map<String, Object> payload) {
            Objects.requireNonNull(payload, "payload");
            DocumentPatch patch = new DocumentPatch();
            Object sets = payload.get("set");
            if (sets instanceof Map<?, ?> values) {
                for (Map.Entry<?, ?> entry : values.entrySet()) {
                    patch.set(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            Object removes = payload.get("remove");
            if (removes instanceof Iterable<?> paths) {
                for (Object path : paths) {
                    patch.remove(String.valueOf(path));
                }
            }
            return patch;
        }
    }
}
