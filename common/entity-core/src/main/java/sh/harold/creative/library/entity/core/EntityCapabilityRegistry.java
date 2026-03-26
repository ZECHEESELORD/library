package sh.harold.creative.library.entity.core;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityCapabilityRegistry {

    private final Map<Class<?>, Object> capabilities = new ConcurrentHashMap<>();

    public <T> void register(Class<T> type, T capability) {
        capabilities.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(capability, "capability"));
    }

    public <T> Optional<T> find(Class<T> type) {
        Object capability = capabilities.get(Objects.requireNonNull(type, "type"));
        if (type.isInstance(capability)) {
            return Optional.of(type.cast(capability));
        }
        return Optional.empty();
    }
}
