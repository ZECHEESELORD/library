package sh.harold.creative.library.entity;

import java.util.Objects;

public record EntityInteractionContext(ManagedEntity entity, InteractorRef interactor, InteractionKind kind) {

    public EntityInteractionContext {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(interactor, "interactor");
        Objects.requireNonNull(kind, "kind");
    }
}
