package sh.harold.library.entity;

@FunctionalInterface
public interface EntityInteractionHandler {

    void onInteract(EntityInteractionContext context);
}
