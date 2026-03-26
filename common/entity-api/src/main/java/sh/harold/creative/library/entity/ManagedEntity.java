package sh.harold.creative.library.entity;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ManagedEntity extends AutoCloseable {

    UUID id();

    EntityTypeKey type();

    default EntityFamily family() {
        return type().family();
    }

    boolean spawned();

    EntityTransform transform();

    Set<Key> tags();

    Optional<Component> customName();

    void customName(Component customName);

    void clearCustomName();

    boolean customNameVisible();

    void customNameVisible(boolean visible);

    boolean glowing();

    void glowing(boolean glowing);

    boolean silent();

    void silent(boolean silent);

    boolean gravity();

    void gravity(boolean gravity);

    boolean invulnerable();

    void invulnerable(boolean invulnerable);

    void teleport(EntityTransform transform);

    void interactionHandler(EntityInteractionHandler handler);

    void clearInteractionHandler();

    Optional<EntityInteractionHandler> interactionHandler();

    <T> Optional<T> capability(Class<T> capabilityType);

    void despawn();

    @Override
    default void close() {
        despawn();
    }
}
