package sh.harold.library.entity;

import java.util.Objects;
import java.util.Optional;

public record EntityInteractionContext(
        ManagedEntity entity,
        InteractorRef interactor,
        EntityInteractionAction action,
        Optional<InteractionHand> hand
) {

    public EntityInteractionContext {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(interactor, "interactor");
        Objects.requireNonNull(action, "action");
        hand = Objects.requireNonNull(hand, "hand");
        if (action == EntityInteractionAction.USE && hand.isEmpty()) {
            throw new IllegalArgumentException("USE interactions require a hand");
        }
        if (action == EntityInteractionAction.ATTACK && hand.isPresent()) {
            throw new IllegalArgumentException("ATTACK interactions do not have a hand");
        }
    }

    public EntityInteractionContext(
            ManagedEntity entity,
            InteractorRef interactor,
            EntityInteractionAction action,
            InteractionHand hand
    ) {
        this(entity, interactor, action, Optional.of(Objects.requireNonNull(hand, "hand")));
    }

    public static EntityInteractionContext use(
            ManagedEntity entity,
            InteractorRef interactor,
            InteractionHand hand
    ) {
        return new EntityInteractionContext(entity, interactor, EntityInteractionAction.USE, hand);
    }

    public static EntityInteractionContext attack(ManagedEntity entity, InteractorRef interactor) {
        return new EntityInteractionContext(entity, interactor, EntityInteractionAction.ATTACK, Optional.empty());
    }
}
