package sh.harold.library.entity.house;

import sh.harold.library.entity.InteractionKind;
import sh.harold.library.entity.InteractorRef;

import java.util.Objects;

public record HouseServiceClickContext(HouseServiceEntity serviceEntity, InteractorRef interactor, InteractionKind kind) {

    public HouseServiceClickContext {
        Objects.requireNonNull(serviceEntity, "serviceEntity");
        Objects.requireNonNull(interactor, "interactor");
        Objects.requireNonNull(kind, "kind");
    }
}
