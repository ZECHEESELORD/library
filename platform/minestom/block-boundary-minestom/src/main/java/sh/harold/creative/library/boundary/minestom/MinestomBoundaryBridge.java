package sh.harold.creative.library.boundary.minestom;

import sh.harold.creative.library.boundary.BoundaryService;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MinestomBoundaryBridge {

    private static final ConcurrentMap<String, BoundaryService> SERVICES = new ConcurrentHashMap<>();

    private MinestomBoundaryBridge() {
    }

    public static void register(String ownerId, BoundaryService service) {
        SERVICES.put(requireName(ownerId), Objects.requireNonNull(service, "service"));
    }

    public static void unregister(String ownerId, BoundaryService service) {
        SERVICES.remove(requireName(ownerId), Objects.requireNonNull(service, "service"));
    }

    public static BoundaryService connect(String ownerId) {
        BoundaryService service = SERVICES.get(requireName(ownerId));
        if (service == null) {
            throw new IllegalStateException("boundary service is not registered: " + ownerId);
        }
        return service;
    }

    private static String requireName(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
        return value;
    }
}
