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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

public abstract class AbstractManagedEntity implements ManagedEntity {
    private static final long INTERNAL_INTERACTION_DEBOUNCE_TICKS = 5L;
    private static final long MILLIS_PER_TICK = 50L;
    private static final long INTERNAL_INTERACTION_DEBOUNCE_NANOS =
            TimeUnit.MILLISECONDS.toNanos(INTERNAL_INTERACTION_DEBOUNCE_TICKS * MILLIS_PER_TICK);

    private final UUID id;
    private final EntityTypeKey type;
    private final Set<Key> tags;
    private final EntityCapabilityRegistry capabilityRegistry = new EntityCapabilityRegistry();
    private final Map<UUID, Long> lastInteractionNanosByInteractor = new HashMap<>();

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

    protected final void requireMutable() {
        requireSpawned();
        assertOwnerThread();
    }

    protected void assertOwnerThread() {
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
        requireMutable();
        this.customName = Objects.requireNonNull(customName, "customName");
        doCustomName(customName);
    }

    @Override
    public final void clearCustomName() {
        requireMutable();
        this.customName = null;
        doClearCustomName();
    }

    @Override
    public final boolean customNameVisible() {
        return customNameVisible;
    }

    @Override
    public final void customNameVisible(boolean visible) {
        requireMutable();
        this.customNameVisible = visible;
        doCustomNameVisible(visible);
    }

    @Override
    public final boolean glowing() {
        return glowing;
    }

    @Override
    public final void glowing(boolean glowing) {
        requireMutable();
        this.glowing = glowing;
        doGlowing(glowing);
    }

    @Override
    public final boolean silent() {
        return silent;
    }

    @Override
    public final void silent(boolean silent) {
        requireMutable();
        this.silent = silent;
        doSilent(silent);
    }

    @Override
    public final boolean gravity() {
        return gravity;
    }

    @Override
    public final void gravity(boolean gravity) {
        requireMutable();
        this.gravity = gravity;
        doGravity(gravity);
    }

    @Override
    public final boolean invulnerable() {
        return invulnerable;
    }

    @Override
    public final void invulnerable(boolean invulnerable) {
        requireMutable();
        this.invulnerable = invulnerable;
        doInvulnerable(invulnerable);
    }

    @Override
    public final void teleport(EntityTransform transform) {
        requireMutable();
        this.transform = Objects.requireNonNull(transform, "transform");
        doTeleport(transform);
    }

    @Override
    public final void interactionHandler(EntityInteractionHandler handler) {
        requireMutable();
        this.interactionHandler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public final void clearInteractionHandler() {
        requireMutable();
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
        requireMutable();
        EntityInteractionHandler handler = interactionHandler;
        if (handler == null) {
            return;
        }
        long now = interactionNowNanos();
        Long last = lastInteractionNanosByInteractor.get(interactor.uniqueId());
        if (last != null && now - last < INTERNAL_INTERACTION_DEBOUNCE_NANOS) {
            return;
        }
        lastInteractionNanosByInteractor.put(interactor.uniqueId(), now);
        handler.onInteract(new EntityInteractionContext(this, interactor, kind));
    }

    protected long interactionNowNanos() {
        return System.nanoTime();
    }

    @Override
    public final void despawn() {
        if (!spawned) {
            return;
        }
        assertOwnerThread();
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
