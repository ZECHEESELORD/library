package sh.harold.creative.library.data.minestom;

import sh.harold.creative.library.data.SharedDataAccess;
import sh.harold.creative.library.data.core.ReflectiveSharedDataClient;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MinestomSharedDataBridge {

    private static final ConcurrentMap<String, Object> ENDPOINTS = new ConcurrentHashMap<>();

    private MinestomSharedDataBridge() {
    }

    public static void register(String ownerId, Object endpoint) {
        ENDPOINTS.put(requireName(ownerId), Objects.requireNonNull(endpoint, "endpoint"));
    }

    public static void unregister(String ownerId, Object endpoint) {
        ENDPOINTS.remove(requireName(ownerId), Objects.requireNonNull(endpoint, "endpoint"));
    }

    public static SharedDataAccess connect(String callerId, String ownerId) {
        Object endpoint = ENDPOINTS.get(requireName(ownerId));
        if (endpoint == null) {
            throw new IllegalStateException("shared data owner not registered: " + ownerId);
        }
        return ReflectiveSharedDataClient.connect(endpoint, requireName(callerId));
    }

    private static String requireName(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
        return value;
    }
}
