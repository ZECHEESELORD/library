package sh.harold.creative.library.entity;

import java.util.Objects;
import java.util.UUID;

public record InteractorRef(UUID uniqueId) {

    public InteractorRef {
        Objects.requireNonNull(uniqueId, "uniqueId");
    }
}
