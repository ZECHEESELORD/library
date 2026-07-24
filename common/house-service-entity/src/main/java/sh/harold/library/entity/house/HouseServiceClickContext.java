package sh.harold.library.entity.house;

import sh.harold.library.entity.EntityInteractionAction;
import sh.harold.library.entity.InteractionHand;
import sh.harold.library.entity.InteractorRef;

import java.util.Objects;
import java.util.Optional;

public record HouseServiceClickContext(
        HouseServiceEntity serviceEntity,
        InteractorRef interactor,
        EntityInteractionAction action,
        Optional<InteractionHand> hand
) {

    public HouseServiceClickContext {
        Objects.requireNonNull(serviceEntity, "serviceEntity");
        Objects.requireNonNull(interactor, "interactor");
        Objects.requireNonNull(action, "action");
        hand = Objects.requireNonNull(hand, "hand");
    }
}
