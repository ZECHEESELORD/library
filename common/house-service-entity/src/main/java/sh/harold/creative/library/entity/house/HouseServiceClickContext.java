package sh.harold.creative.library.entity.house;

import sh.harold.creative.library.entity.InteractionKind;
import sh.harold.creative.library.entity.InteractorRef;

import java.util.Objects;

public record HouseServiceClickContext(HouseServiceEntity serviceEntity, InteractorRef interactor, InteractionKind kind) {

    public HouseServiceClickContext {
        Objects.requireNonNull(serviceEntity, "serviceEntity");
        Objects.requireNonNull(interactor, "interactor");
        Objects.requireNonNull(kind, "kind");
    }
}
