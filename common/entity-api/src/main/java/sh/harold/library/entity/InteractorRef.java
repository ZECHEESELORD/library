package sh.harold.library.entity;

import net.kyori.adventure.identity.Identified;
import net.kyori.adventure.identity.Identity;

import java.util.Objects;
import java.util.UUID;

public record InteractorRef(UUID uniqueId) implements Identified {

    public InteractorRef {
        Objects.requireNonNull(uniqueId, "uniqueId");
    }

    @Override
    public Identity identity() {
        return Identity.identity(uniqueId);
    }
}
