package sh.harold.creative.library.data.minestom;

import sh.harold.creative.library.data.SharedDataAccess;
import sh.harold.creative.library.data.core.ReflectiveSharedDataClient;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Properties;

public final class MinestomSharedDataBridge {

    private static final String REGISTRY_PROPERTY = MinestomSharedDataBridge.class.getName() + ".registry";

    private MinestomSharedDataBridge() {
    }

    public static void register(String ownerId, Object endpoint) {
        registry().put(requireName(ownerId), Objects.requireNonNull(endpoint, "endpoint"));
    }

    public static void unregister(String ownerId, Object endpoint) {
        registry().remove(requireName(ownerId), Objects.requireNonNull(endpoint, "endpoint"));
    }

    public static SharedDataAccess connect(String callerId, String ownerId) {
        Object endpoint = registry().get(requireName(ownerId));
        if (endpoint == null) {
            throw new IllegalStateException("shared data owner not registered: " + ownerId);
        }
        return ReflectiveSharedDataClient.connect(endpoint, requireName(callerId));
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentMap<String, Object> registry() {
        Properties properties = System.getProperties();
        synchronized (properties) {
            Object existing = properties.get(REGISTRY_PROPERTY);
            if (existing instanceof ConcurrentMap<?, ?> map) {
                return (ConcurrentMap<String, Object>) map;
            }
            ConcurrentMap<String, Object> created = new ConcurrentHashMap<>();
            properties.put(REGISTRY_PROPERTY, created);
            return created;
        }
    }

    private static String requireName(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
        return value;
    }
}
