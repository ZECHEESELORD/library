package sh.harold.creative.library.data;

import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.CompletionStage;

public interface DocumentCollection {

    DataNamespace namespace();

    String name();

    DocumentKey key(String id);

    Document document(String id);

    CompletionStage<Long> count();

    CompletionStage<List<String>> listIds();

    default <T> Optional<T> optionalCapability(Class<T> capabilityType) {
        Objects.requireNonNull(capabilityType, "capabilityType");
        return Optional.empty();
    }
}
