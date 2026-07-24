package sh.harold.library.entity.core;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import sh.harold.library.entity.CommonEntityFlags;
import sh.harold.library.entity.EntityInteractionAction;
import sh.harold.library.entity.EntityInteractionContext;
import sh.harold.library.entity.EntityInteractionHandler;
import sh.harold.library.entity.EntityInteractionResult;
import sh.harold.library.entity.EntitySpec;
import sh.harold.library.entity.EntityTransform;
import sh.harold.library.entity.EntityTypeKey;
import sh.harold.library.entity.InteractionHand;
import sh.harold.library.entity.InteractorRef;
import sh.harold.library.entity.ManagedEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public abstract class AbstractManagedEntity implements ManagedEntity {
    private static final long NANOS_PER_TICK = 50_000_000L;

    private final UUID id;
    private final EntityTypeKey type;
    private final Set<Key> tags;
    private final EntityCapabilityRegistry capabilityRegistry = new EntityCapabilityRegistry();
    private final Map<UUID, UseDelivery> lastUseDeliveryByInteractor = new HashMap<>();

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
        EntityTransform requested = Objects.requireNonNull(transform, "transform");
        doTeleport(requested);
        publishTransform(requested);
    }

    /**
     * Publishes a transform already applied by an asynchronous native platform operation.
     */
    protected final void publishTransform(EntityTransform transform) {
        requireMutable();
        this.transform = Objects.requireNonNull(transform, "transform");
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

    public final EntityInteractionResult handleInteraction(
            InteractorRef interactor,
            EntityInteractionAction action,
            Optional<InteractionHand> hand
    ) {
        requireMutable();
        EntityInteractionContext context = new EntityInteractionContext(this, interactor, action, hand);
        long tick = interactionTick();
        if (action == EntityInteractionAction.USE) {
            UseDelivery prior = lastUseDeliveryByInteractor.get(interactor.uniqueId());
            if (prior != null && prior.tick() == tick) {
                return prior.result();
            }
        }

        EntityInteractionResult result = Objects.requireNonNull(
                observeInteraction(context),
                "observeInteraction returned null"
        );
        EntityInteractionHandler handler = interactionHandler;
        if (handler != null) {
            result = result.or(Objects.requireNonNull(
                    handler.onInteract(context),
                    "EntityInteractionHandler returned null"
            ));
        }
        if (action == EntityInteractionAction.USE) {
            lastUseDeliveryByInteractor.put(interactor.uniqueId(), new UseDelivery(tick, result));
        }
        return result;
    }

    public final EntityInteractionResult handleInteraction(
            InteractorRef interactor,
            EntityInteractionAction action,
            InteractionHand hand
    ) {
        return handleInteraction(interactor, action, Optional.of(Objects.requireNonNull(hand, "hand")));
    }

    public final EntityInteractionResult handleUse(InteractorRef interactor, InteractionHand hand) {
        return handleInteraction(interactor, EntityInteractionAction.USE, hand);
    }

    public final EntityInteractionResult handleAttack(InteractorRef interactor) {
        return handleInteraction(interactor, EntityInteractionAction.ATTACK, Optional.empty());
    }

    /**
     * Runs before the application handler. Platform behavior integrations override this to observe and consume safely.
     */
    protected EntityInteractionResult observeInteraction(EntityInteractionContext context) {
        return EntityInteractionResult.PASS;
    }

    public final void clearInteractionDebounce(UUID interactorId) {
        requireMutable();
        lastUseDeliveryByInteractor.remove(Objects.requireNonNull(interactorId, "interactorId"));
    }

    protected final void clearInteractionDebounce() {
        lastUseDeliveryByInteractor.clear();
    }

    protected long interactionNowNanos() {
        return System.nanoTime();
    }

    /**
     * Platforms with a native server tick counter may override this for exact dual-hand deduplication.
     */
    protected long interactionTick() {
        return Math.floorDiv(interactionNowNanos(), NANOS_PER_TICK);
    }

    @Override
    public final void despawn() {
        if (!spawned) {
            return;
        }
        assertOwnerThread();
        doDespawn();
        clearInteractionDebounce();
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

    private record UseDelivery(long tick, EntityInteractionResult result) {
    }
}
