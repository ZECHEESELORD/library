package sh.harold.library.entity;

import java.util.Objects;
import java.util.function.Consumer;

@FunctionalInterface
public interface EntityInteractionHandler {

    EntityInteractionResult onInteract(EntityInteractionContext context);

    static EntityInteractionHandler observing(
            Consumer<? super EntityInteractionContext> observer,
            EntityInteractionResult result
    ) {
        Objects.requireNonNull(observer, "observer");
        Objects.requireNonNull(result, "result");
        return context -> {
            observer.accept(context);
            return result;
        };
    }
}
