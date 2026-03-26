package sh.harold.creative.library.entity;

@FunctionalInterface
public interface EntityInteractionHandler {

    void onInteract(EntityInteractionContext context);
}
