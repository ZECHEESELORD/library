package sh.harold.library.data;

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
