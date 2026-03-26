package sh.harold.creative.library.entity.core;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import sh.harold.creative.library.entity.CommonEntityFlags;
import sh.harold.creative.library.entity.EntityInteractionContext;
import sh.harold.creative.library.entity.EntityInteractionHandler;
import sh.harold.creative.library.entity.EntitySpec;
import sh.harold.creative.library.entity.EntityTransform;
import sh.harold.creative.library.entity.EntityTypeKey;
import sh.harold.creative.library.entity.InteractionKind;
import sh.harold.creative.library.entity.InteractorRef;
import sh.harold.creative.library.entity.ManagedEntity;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public abstract class AbstractManagedEntity implements ManagedEntity {

    private final UUID id;
    private final EntityTypeKey type;
    private final Set<Key> tags;
    private final EntityCapabilityRegistry capabilityRegistry = new EntityCapabilityRegistry();

    private volatile boolean spawned = true;
    private volatile EntityTransform transform;
    private volatile Component customName;
    private volatile boolean customNameVisible;
    private volatile boolean glowing;
    private volatile boolean silent;
    private volatile boolean gravity;
    private volatile boolean invulnerable;
    private volatile EntityInteractionHandler interactionHandler;

    protected AbstractManagedEntity(UUID id, EntitySpec spec) {
        EntitySpecValidator.validate(spec);
        this.id = Objects.requireNonNull(id, "id");
        this.type = spec.type();
        this.tags = Set.copyOf(spec.tags());
        this.transform = spec.transform();

        CommonEntityFlags flags = spec.flags();
        this.customName = flags.customName().orElse(null);
        this.customNameVisible = flags.customNameVisible();
        this.glowing = flags.glowing();
        this.silent = flags.silent();
        this.gravity = flags.gravity();
        this.invulnerable = flags.invulnerable();
        this.interactionHandler = spec.interactionHandler().orElse(null);
    }

    protected final void applyInitialState() {
        if (customName != null) {
            doCustomName(customName);
        }
        doCustomNameVisible(customNameVisible);
        doGlowing(glowing);
        doSilent(silent);
        doGravity(gravity);
        doInvulnerable(invulnerable);
    }

    protected final <T> void registerCapability(Class<T> type, T capability) {
        capabilityRegistry.register(type, capability);
    }

    protected final void requireSpawned() {
        if (!spawned) {
            throw new IllegalStateException("Entity " + id + " has already been despawned");
        }
    }

    @Override
    public final UUID id() {
        return id;
    }

    @Override
    public final EntityTypeKey type() {
        return type;
    }

    @Override
    public final boolean spawned() {
        return spawned;
    }

    @Override
    public final EntityTransform transform() {
        return transform;
    }

    @Override
    public final Set<Key> tags() {
        return tags;
    }

    @Override
    public final Optional<Component> customName() {
        return Optional.ofNullable(customName);
    }

    @Override
    public final void customName(Component customName) {
        requireSpawned();
        this.customName = Objects.requireNonNull(customName, "customName");
        doCustomName(customName);
    }

    @Override
    public final void clearCustomName() {
        requireSpawned();
        this.customName = null;
        doClearCustomName();
    }

    @Override
    public final boolean customNameVisible() {
        return customNameVisible;
    }

    @Override
    public final void customNameVisible(boolean visible) {
        requireSpawned();
        this.customNameVisible = visible;
        doCustomNameVisible(visible);
    }

    @Override
    public final boolean glowing() {
        return glowing;
    }

    @Override
    public final void glowing(boolean glowing) {
        requireSpawned();
        this.glowing = glowing;
        doGlowing(glowing);
    }

    @Override
    public final boolean silent() {
        return silent;
    }

    @Override
    public final void silent(boolean silent) {
        requireSpawned();
        this.silent = silent;
        doSilent(silent);
    }

    @Override
    public final boolean gravity() {
        return gravity;
    }

    @Override
    public final void gravity(boolean gravity) {
        requireSpawned();
        this.gravity = gravity;
        doGravity(gravity);
    }

    @Override
    public final boolean invulnerable() {
        return invulnerable;
    }

    @Override
    public final void invulnerable(boolean invulnerable) {
        requireSpawned();
        this.invulnerable = invulnerable;
        doInvulnerable(invulnerable);
    }

    @Override
    public final void teleport(EntityTransform transform) {
        requireSpawned();
        this.transform = Objects.requireNonNull(transform, "transform");
        doTeleport(transform);
    }

    @Override
    public final void interactionHandler(EntityInteractionHandler handler) {
        requireSpawned();
        this.interactionHandler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public final void clearInteractionHandler() {
        requireSpawned();
        this.interactionHandler = null;
    }

    @Override
    public final Optional<EntityInteractionHandler> interactionHandler() {
        return Optional.ofNullable(interactionHandler);
    }

    @Override
    public final <T> Optional<T> capability(Class<T> capabilityType) {
        return capabilityRegistry.find(capabilityType);
    }

    public final void handleInteraction(InteractorRef interactor, InteractionKind kind) {
        requireSpawned();
        EntityInteractionHandler handler = interactionHandler;
        if (handler != null) {
            handler.onInteract(new EntityInteractionContext(this, interactor, kind));
        }
    }

    @Override
    public final void despawn() {
        if (!spawned) {
            return;
        }
        doDespawn();
        spawned = false;
    }

    protected abstract void doTeleport(EntityTransform transform);

    protected abstract void doCustomName(Component customName);

    protected abstract void doClearCustomName();

    protected abstract void doCustomNameVisible(boolean visible);

    protected abstract void doGlowing(boolean glowing);

    protected abstract void doSilent(boolean silent);

    protected abstract void doGravity(boolean gravity);

    protected abstract void doInvulnerable(boolean invulnerable);

    protected abstract void doDespawn();
}
