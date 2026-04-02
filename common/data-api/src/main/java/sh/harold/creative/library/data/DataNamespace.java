package sh.harold.creative.library.data;

import java.util.Objects;
import java.util.Optional;

public interface DataNamespace {

    String name();

    DocumentCollection collection(String name);

    default <T> Optional<T> optionalCapability(Class<T> capabilityType) {
        Objects.requireNonNull(capabilityType, "capabilityType");
        return Optional.empty();
    }
}
