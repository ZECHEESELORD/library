package sh.harold.library.entity.minestom;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.EntityPose;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.entity.VillagerProfession;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.metadata.AgeableMobMeta;
import net.minestom.server.entity.metadata.avatar.MannequinMeta;
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta;
import net.minestom.server.entity.metadata.display.BlockDisplayMeta;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.entity.metadata.other.ArmorStandMeta;
import net.minestom.server.entity.metadata.other.InteractionMeta;
import net.minestom.server.entity.metadata.villager.VillagerMeta;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerEntityInteractEvent;
import net.minestom.server.event.player.PlayerPacketOutEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.player.ResolvableProfile;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.EntityAnimationPacket;
import net.minestom.server.network.packet.server.play.EntityHeadLookPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.EntityPositionAndRotationPacket;
import net.minestom.server.network.packet.server.play.EntityPositionSyncPacket;
import net.minestom.server.network.packet.server.play.EntityRotationPacket;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import net.minestom.server.thread.AcquirableOwnershipException;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import sh.harold.library.entity.BlockDescriptor;
import sh.harold.library.entity.EntityInteractionAction;
import sh.harold.library.entity.EntityInteractionContext;
import sh.harold.library.entity.EntityInteractionResult;
import sh.harold.library.entity.EntitySpec;
import sh.harold.library.entity.EntityTransform;
import sh.harold.library.entity.EntityTypeKey;
import sh.harold.library.entity.EntityTypes;
import sh.harold.library.entity.InteractionHand;
import sh.harold.library.entity.InteractorRef;
import sh.harold.library.entity.ItemDescriptor;
import sh.harold.library.entity.ManagedEntity;
import sh.harold.library.entity.SkinTexture;
import sh.harold.library.entity.capability.AgeableCapable;
import sh.harold.library.entity.capability.BlockDisplayCapable;
import sh.harold.library.entity.capability.CollidableCapable;
import sh.harold.library.entity.capability.DisplayCapable;
import sh.harold.library.entity.capability.Equipable;
import sh.harold.library.entity.capability.ItemDisplayCapable;
import sh.harold.library.entity.capability.LeashCapable;
import sh.harold.library.entity.capability.PassengerCapable;
import sh.harold.library.entity.capability.PoseCapable;
import sh.harold.library.entity.capability.PersistenceCapable;
import sh.harold.library.entity.capability.SkinCapable;
import sh.harold.library.entity.capability.TextDisplayCapable;
import sh.harold.library.entity.capability.VillagerLikeCapable;
import sh.harold.library.entity.core.AbstractManagedEntity;
import sh.harold.library.entity.core.EntitySpecValidator;
import sh.harold.library.entity.house.HousePresentation;
import sh.harold.library.entity.house.HousePresentationFactory;
import sh.harold.library.entity.house.HousePresentationRenderer;
import sh.harold.library.entity.house.HouseServiceClickContext;
import sh.harold.library.entity.house.HouseServiceEntity;
import sh.harold.library.entity.house.HouseServiceSpec;
import sh.harold.library.entity.house.HouseValidator;
import sh.harold.library.entity.house.StandardHouseServiceEntity;
import sh.harold.library.npc.behavior.HumanoidBehaviorCapable;
import sh.harold.library.npc.behavior.NpcBehaviorProfile;
import sh.harold.library.npc.behavior.NpcConversationRegistry;
import sh.harold.library.npc.behavior.core.NpcAttentionStack;
import sh.harold.library.npc.behavior.core.NpcBehaviorActor;
import sh.harold.library.npc.behavior.core.NpcRenderFrame;
import sh.harold.library.npc.behavior.core.StandardNpcConversationRegistry;
import sh.harold.library.spatial.SpaceId;
import sh.harold.library.spatial.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class MinestomEntityPlatform implements AutoCloseable {

    private final Map<UUID, MinestomManagedEntity> entities = new ConcurrentHashMap<>();
    private final Map<Integer, MinestomNpcBehaviorRenderPort> behaviorPortsByEntityId = new ConcurrentHashMap<>();
    private final Map<UUID, Set<MinestomManagedEntity>> trackedBehaviorEntities = new ConcurrentHashMap<>();
    private final EventNode<Event> eventNode = EventNode.all("library-entity-platform");
    private final Function<Instance, SpaceId> spaceResolver;
    private final StandardNpcConversationRegistry conversationRegistry;
    private final Task conversationTask;
    private final Object lifecycleLock = new Object();
    private final AtomicBoolean shutdownFinalized = new AtomicBoolean();
    private final CompletableFuture<Void> closeCompletion = new CompletableFuture<>();
    private volatile boolean closed;

    public MinestomEntityPlatform() {
        this(instance -> SpaceId.of("minestom", instance.getUuid().toString()));
    }

    public MinestomEntityPlatform(Function<Instance, SpaceId> spaceResolver) {
        this.spaceResolver = Objects.requireNonNull(spaceResolver, "spaceResolver");
        this.conversationRegistry = new StandardNpcConversationRegistry();
        this.conversationTask = MinecraftServer.getSchedulerManager()
                .buildTask(conversationRegistry::tick)
                .repeat(TaskSchedule.tick(1))
                .schedule();
        eventNode.addListener(PlayerEntityInteractEvent.class, this::onInteract);
        eventNode.addListener(EntityAttackEvent.class, this::onAttack);
        eventNode.addListener(PlayerPacketOutEvent.class, this::onPacketOut);
        eventNode.addListener(PlayerTickEvent.class, event -> observeViewer(event.getPlayer()));
        eventNode.addListener(PlayerDisconnectEvent.class, event -> retireViewer(event.getPlayer().getUuid()));
        eventNode.addListener(RemoveEntityFromInstanceEvent.class, event -> {
            if (event.getEntity() instanceof net.minestom.server.entity.Player player) {
                retireViewer(player.getUuid());
            }
            MinestomManagedEntity managedEntity = entities.get(event.getEntity().getUuid());
            if (managedEntity != null && event.getEntity().isRemoved()) {
                managedEntity.retireNative();
            }
        });
        MinecraftServer.getGlobalEventHandler().addChild(eventNode);
    }

    public NpcConversationRegistry conversationRegistry() {
        return conversationRegistry;
    }

    public ManagedEntity spawn(Instance instance, EntitySpec spec) {
        return MinestomFutureGuard.requireCompleted(spawnAsync(instance, spec), "entity spawn");
    }

    public CompletionStage<ManagedEntity> spawnAsync(Instance instance, EntitySpec spec) {
        requireOpen();
        Objects.requireNonNull(instance, "instance");
        EntitySpecValidator.validate(spec);
        Entity entity = createEntity(spec.type());
        return entity.setInstance(instance, toPos(spec.transform())).thenApply(ignored -> {
            synchronized (lifecycleLock) {
                if (closed) {
                    entity.remove();
                    throw new IllegalStateException("Minestom entity platform is closed");
                }
                MinestomManagedEntity managedEntity = new MinestomManagedEntity(entity, spec, () -> {
                    entities.remove(entity.getUuid());
                    MinestomNpcBehaviorRenderPort removed = behaviorPortsByEntityId.remove(entity.getEntityId());
                    if (removed != null) {
                        removed.close();
                    }
                });
                entities.put(managedEntity.id(), managedEntity);
                if (managedEntity.behaviorPort != null) {
                    behaviorPortsByEntityId.put(entity.getEntityId(), managedEntity.behaviorPort);
                }
                return managedEntity;
            }
        });
    }

    public HouseServiceEntity spawnService(Instance instance, HouseServiceSpec serviceSpec) {
        return MinestomFutureGuard.requireCompleted(spawnServiceAsync(instance, serviceSpec), "House service spawn");
    }

    public CompletionStage<HouseServiceEntity> spawnServiceAsync(Instance instance, HouseServiceSpec serviceSpec) {
        requireOpen();
        HouseValidator.validate(serviceSpec);
        HousePresentation presentation = HousePresentationFactory.create(serviceSpec);
        return spawnAsync(instance, serviceSpec.entitySpec())
                .thenCompose(anchor -> {
                    HouseServiceEntity service = finishServiceSpawn(serviceSpec, presentation, anchor);
                    if (serviceSpec.behaviorProfile().isEmpty()) {
                        return CompletableFuture.completedFuture(service);
                    }
                    HumanoidBehaviorCapable behavior = anchor.requireCapability(HumanoidBehaviorCapable.class);
                    CompletableFuture<HouseServiceEntity> completion = new CompletableFuture<>();
                    behavior.configure(serviceSpec.behaviorProfile().orElseThrow())
                            .whenComplete((ignored, failure) -> {
                                if (failure == null) {
                                    completion.complete(service);
                                    return;
                                }
                                ((MinestomManagedEntity) anchor).despawnAsync()
                                        .whenComplete((retired, retirementFailure) -> {
                                            if (retirementFailure != null && retirementFailure != failure) {
                                                failure.addSuppressed(retirementFailure);
                                            }
                                            completion.completeExceptionally(failure);
                                        });
                            });
                    return completion;
                });
    }

    private HouseServiceEntity finishServiceSpawn(
            HouseServiceSpec serviceSpec,
            HousePresentation presentation,
            ManagedEntity anchor
    ) {
        anchor.clearCustomName();
        anchor.customNameVisible(false);

        MinestomManagedEntity managedAnchor = (MinestomManagedEntity) anchor;
        HousePresentationRenderer renderer = new MinestomHouseRenderer(managedAnchor.entity, presentation);
        if (managedAnchor.behaviorPort != null) {
            managedAnchor.behaviorPort.reserveOverheadHeight(
                    presentation.lines().size() * MinestomHouseRenderer.LINE_SPACING
            );
        }
        StandardHouseServiceEntity serviceEntity = new StandardHouseServiceEntity(anchor, presentation, renderer);
        managedAnchor.addRetirement(renderer);

        AtomicReference<HouseServiceEntity> reference = new AtomicReference<>(serviceEntity);
        if (serviceSpec.clickHandler().isPresent() || serviceSpec.entitySpec().interactionHandler().isPresent()) {
            anchor.interactionHandler(context -> {
                EntityInteractionResult applicationResult = serviceSpec.entitySpec().interactionHandler()
                        .map(handler -> handler.onInteract(context))
                        .orElse(EntityInteractionResult.PASS);
                serviceSpec.clickHandler().ifPresent(handler -> handler.onClick(
                        new HouseServiceClickContext(reference.get(), context.interactor(), context.action(), context.hand())
                ));
                return serviceSpec.clickHandler().isPresent() ? EntityInteractionResult.CONSUME : applicationResult;
            });
        }
        return serviceEntity;
    }

    public CompletionStage<Void> teleportAsync(ManagedEntity managedEntity, EntityTransform transform) {
        Objects.requireNonNull(managedEntity, "managedEntity");
        Objects.requireNonNull(transform, "transform");
        if (!(managedEntity instanceof MinestomManagedEntity minestomManagedEntity)
                || entities.get(managedEntity.id()) != managedEntity) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Entity is not owned by this platform"));
        }
        return minestomManagedEntity.teleportAsync(transform);
    }

    public CompletionStage<Void> closeAsync() {
        List<MinestomManagedEntity> snapshot;
        synchronized (lifecycleLock) {
            if (closed) {
                return closeCompletion;
            }
            closed = true;
            snapshot = List.copyOf(entities.values());
        }
        Throwable startFailure = beginShutdown();
        List<CompletableFuture<Void>> retirements = new ArrayList<>();
        for (MinestomManagedEntity managedEntity : snapshot) {
            try {
                retirements.add(managedEntity.despawnAsync().toCompletableFuture());
            } catch (Throwable retirementFailure) {
                retirements.add(CompletableFuture.failedFuture(retirementFailure));
            }
        }
        CompletableFuture.allOf(retirements.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, failure) -> finishShutdown(combineFailures(startFailure, failure)));
        return closeCompletion;
    }

    @Override
    public void close() {
        List<MinestomManagedEntity> snapshot;
        synchronized (lifecycleLock) {
            if (closed) {
                if (!closeCompletion.isDone()) {
                    throw new IllegalStateException(
                            "Minestom entity platform is closing asynchronously; await closeAsync() instead"
                    );
                }
                closeCompletion.join();
                return;
            }
            try {
                for (MinestomManagedEntity managedEntity : entities.values()) {
                    managedEntity.entity.acquirable().assertOwnership();
                }
            } catch (AcquirableOwnershipException exception) {
                throw new IllegalStateException(
                        "entity platform close requires every NPC owner tick; use closeAsync() from other threads",
                        exception
                );
            }
            closed = true;
            snapshot = List.copyOf(entities.values());
        }
        Throwable failure = beginShutdown();
        for (MinestomManagedEntity managedEntity : snapshot) {
            try {
                managedEntity.despawn();
            } catch (Throwable retirementFailure) {
                failure = combineFailures(failure, retirementFailure);
            }
        }
        finishShutdown(failure);
        closeCompletion.join();
    }

    private Throwable beginShutdown() {
        Throwable failure = null;
        try {
            MinecraftServer.getGlobalEventHandler().removeChild(eventNode);
        } catch (Throwable eventFailure) {
            failure = combineFailures(failure, eventFailure);
        }
        try {
            conversationTask.cancel();
        } catch (Throwable taskFailure) {
            failure = combineFailures(failure, taskFailure);
        }
        try {
            conversationRegistry.close();
        } catch (Throwable conversationFailure) {
            failure = combineFailures(failure, conversationFailure);
        }
        return failure;
    }

    private void finishShutdown(Throwable failure) {
        if (!shutdownFinalized.compareAndSet(false, true)) {
            return;
        }
        entities.clear();
        for (MinestomNpcBehaviorRenderPort behaviorPort : behaviorPortsByEntityId.values()) {
            try {
                behaviorPort.close();
            } catch (Throwable portFailure) {
                failure = combineFailures(failure, portFailure);
            }
        }
        behaviorPortsByEntityId.clear();
        trackedBehaviorEntities.clear();
        if (failure == null) {
            closeCompletion.complete(null);
        } else {
            closeCompletion.completeExceptionally(failure);
        }
    }

    private static Throwable combineFailures(Throwable current, Throwable additional) {
        if (additional == null) {
            return current;
        }
        if (current == null) {
            return additional;
        }
        if (current != additional) {
            current.addSuppressed(additional);
        }
        return current;
    }

    private void onInteract(PlayerEntityInteractEvent event) {
        MinestomManagedEntity entity = entities.get(event.getTarget().getUuid());
        if (entity != null) {
            InteractionHand hand = event.getHand() == PlayerHand.MAIN
                    ? InteractionHand.MAIN_HAND
                    : InteractionHand.OFF_HAND;
            dispatchInteraction(
                    entity,
                    new InteractorRef(event.getPlayer().getUuid()),
                    EntityInteractionAction.USE,
                    java.util.Optional.of(hand)
            );
        }
    }

    private void onAttack(EntityAttackEvent event) {
        MinestomManagedEntity entity = entities.get(event.getTarget().getUuid());
        if (entity != null && event.getEntity() instanceof net.minestom.server.entity.Player player) {
            dispatchInteraction(
                    entity,
                    new InteractorRef(player.getUuid()),
                    EntityInteractionAction.ATTACK,
                    java.util.Optional.empty()
            );
        }
    }

    private static void dispatchInteraction(
            MinestomManagedEntity entity,
            InteractorRef interactor,
            EntityInteractionAction action,
            Optional<InteractionHand> hand
    ) {
        Runnable delivery = () -> {
            if (entity.spawned()) {
                entity.handleInteraction(interactor, action, hand);
            }
        };
        if (entity.entity.acquirable().isOwned()) {
            delivery.run();
        } else {
            entity.entity.scheduleNextTick(ignored -> delivery.run());
        }
    }

    private void onPacketOut(PlayerPacketOutEvent event) {
        ServerPacket packet = event.getPacket();
        int entityId = packetEntityId(packet);
        if (entityId < 0) {
            return;
        }
        MinestomNpcBehaviorRenderPort port = behaviorPortsByEntityId.get(entityId);
        if (port != null && port.intercept(event.getPlayer(), packet)) {
            event.setCancelled(true);
        }
    }

    private static int packetEntityId(ServerPacket packet) {
        return switch (packet) {
            case EntityRotationPacket rotation -> rotation.entityId();
            case EntityHeadLookPacket headLook -> headLook.entityId();
            case EntityMetaDataPacket metadata -> metadata.entityId();
            case EntityAnimationPacket animation -> animation.entityId();
            case EntityPositionAndRotationPacket movement -> movement.entityId();
            case EntityPositionSyncPacket sync -> sync.entityId();
            case EntityTeleportPacket teleport -> teleport.entityId();
            case SpawnEntityPacket spawn -> spawn.entityId();
            default -> -1;
        };
    }

    private void retireViewer(UUID viewerId) {
        Set<MinestomManagedEntity> tracked = trackedBehaviorEntities.remove(viewerId);
        if (tracked == null) {
            return;
        }
        tracked.forEach(managedEntity -> {
            if (managedEntity.entity.isRemoved()) {
                return;
            }
            managedEntity.entity.scheduleNextTick(ignored -> {
                if (managedEntity.spawned()) {
                    managedEntity.clearInteractionDebounce(viewerId);
                    if (managedEntity.behaviorActor != null) {
                        managedEntity.behaviorActor.removeViewer(
                                viewerId,
                                NpcAttentionStack.ReleaseReason.UNTRACKED
                        );
                    }
                    if (managedEntity.behaviorPort != null) {
                        managedEntity.behaviorPort.retireViewer(viewerId);
                    }
                }
            });
        });
    }

    private void observeViewer(net.minestom.server.entity.Player player) {
        Set<MinestomManagedEntity> tracked = trackedBehaviorEntities.get(player.getUuid());
        if (tracked == null) {
            return;
        }
        for (MinestomManagedEntity managedEntity : tracked) {
            if (managedEntity.behaviorActor != null
                    && managedEntity.behaviorActor.configured()
                    && managedEntity.trackedViewerIds.contains(player.getUuid())) {
                managedEntity.observeViewer(player);
            }
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Minestom entity platform is closed");
        }
    }

    private static Entity createEntity(EntityTypeKey type) {
        EntityType resolvedType = resolveEntityType(type);
        if (EntityTypes.PLAYER_LIKE_HUMANOID.equals(type)) {
            return new SafeMannequinEntity(resolvedType);
        }
        return switch (type.family()) {
            case HUMANOID, MONSTER, ANIMAL, VILLAGER -> new EntityCreature(resolvedType);
            default -> new Entity(resolvedType);
        };
    }

    private static EntityType resolveEntityType(EntityTypeKey type) {
        if (EntityTypes.PLAYER_LIKE_HUMANOID.equals(type)) {
            return Objects.requireNonNull(EntityType.fromKey("minecraft:mannequin"), "Minestom mannequin type");
        }
        EntityType resolved = EntityType.fromKey(type.key());
        if (resolved == null) {
            throw new IllegalArgumentException("Unsupported Minestom entity type " + type.key());
        }
        return resolved;
    }

    private static Pos toPos(EntityTransform transform) {
        return new Pos(transform.x(), transform.y(), transform.z(), transform.yaw(), transform.pitch());
    }

    private static ItemStack toItemStack(ItemDescriptor descriptor) {
        Material material = Material.fromKey(descriptor.key());
        if (material == null) {
            throw new IllegalArgumentException("Unsupported item key " + descriptor.key());
        }
        return ItemStack.of(material, descriptor.amount());
    }

    private static Block toBlock(BlockDescriptor descriptor) {
        Block block = Block.fromKey(descriptor.key());
        if (block == null) {
            throw new IllegalArgumentException("Unsupported block key " + descriptor.key());
        }
        return block;
    }

    private static EquipmentSlot toMinestomSlot(sh.harold.library.entity.EquipmentSlot slot) {
        return switch (slot) {
            case MAIN_HAND -> EquipmentSlot.MAIN_HAND;
            case OFF_HAND -> EquipmentSlot.OFF_HAND;
            case FEET -> EquipmentSlot.BOOTS;
            case LEGS -> EquipmentSlot.LEGGINGS;
            case CHEST -> EquipmentSlot.CHESTPLATE;
            case HEAD -> EquipmentSlot.HELMET;
            case BODY -> EquipmentSlot.BODY;
        };
    }

    private static sh.harold.library.entity.EntityPose fromMinestomPose(EntityPose pose) {
        return switch (pose) {
            case STANDING -> sh.harold.library.entity.EntityPose.STANDING;
            case SNEAKING -> sh.harold.library.entity.EntityPose.CROUCHING;
            case SLEEPING -> sh.harold.library.entity.EntityPose.SLEEPING;
            case SITTING -> sh.harold.library.entity.EntityPose.SITTING;
            case SWIMMING -> sh.harold.library.entity.EntityPose.SWIMMING;
            case SPIN_ATTACK -> sh.harold.library.entity.EntityPose.SPIN_ATTACK;
            default -> sh.harold.library.entity.EntityPose.STANDING;
        };
    }

    private static EntityPose toMinestomPose(sh.harold.library.entity.EntityPose pose) {
        return switch (pose) {
            case CROUCHING -> EntityPose.SNEAKING;
            case SLEEPING -> EntityPose.SLEEPING;
            case SITTING -> EntityPose.SITTING;
            case SWIMMING -> EntityPose.SWIMMING;
            case SPIN_ATTACK -> EntityPose.SPIN_ATTACK;
            case STANDING -> EntityPose.STANDING;
        };
    }

    private final class MinestomManagedEntity extends AbstractManagedEntity {
        private final Entity entity;
        private final Runnable onDespawn;
        private final MinestomNpcBehaviorRenderPort behaviorPort;
        private final NpcBehaviorActor behaviorActor;
        private final List<AutoCloseable> retirementResources = new ArrayList<>();
        private final Map<UUID, LineOfSightSample> lineOfSightByViewer = new ConcurrentHashMap<>();
        private final AtomicBoolean retirementStarted = new AtomicBoolean();
        private final CompletableFuture<Void> retirementCompletion = new CompletableFuture<>();
        private volatile Set<UUID> trackedViewerIds = Set.of();
        private volatile NativeViewerFrame nativeViewerFrame;
        private volatile NpcRenderFrame behaviorBaseFrame;
        private volatile SkinTexture appliedSkin;

        private MinestomManagedEntity(Entity entity, EntitySpec spec, Runnable onDespawn) {
            super(entity.getUuid(), spec);
            this.entity = entity;
            this.onDespawn = onDespawn;
            this.behaviorPort = entity instanceof SafeMannequinEntity safeMannequin
                    ? new MinestomNpcBehaviorRenderPort(safeMannequin, spaceResolver)
                    : null;
            this.behaviorActor = behaviorPort == null
                    ? null
                    : new NpcBehaviorActor(
                            entity.getUuid(),
                            spec.transform().yaw(),
                            spec.transform().pitch(),
                            behaviorPort
                    );
            this.nativeViewerFrame = new NativeViewerFrame(
                    new Vec3(spec.transform().x(), spec.transform().y(), spec.transform().z()),
                    spec.transform().y() + entity.getEyeHeight(),
                    null
            );
            this.behaviorBaseFrame = NpcRenderFrame.standing(spec.transform().yaw(), spec.transform().pitch());
            if (behaviorPort != null) {
                behaviorPort.updateBaseFrame(behaviorBaseFrame);
                behaviorActor.updateBaseFrame(behaviorBaseFrame);
            }
            if (entity instanceof SafeMannequinEntity safeMannequin) {
                safeMannequin.renderTick = this::tickBehavior;
            }
            registerCapabilities();
            applyInitialState();
            if (entity instanceof SafeMannequinEntity safeMannequin) {
                // Native mannequins are safe interaction fixtures regardless of
                // the author-supplied generic flag defaults.
                gravity(false);
                invulnerable(true);
                safeMannequin.setHasPhysics(false);
                safeMannequin.collidable(false);
                safeMannequin.persistent(true);
                entity.editEntityMeta(MannequinMeta.class, meta -> meta.setImmovable(true));
            }
        }

        private void registerCapabilities() {
            if (behaviorActor != null) {
                registerCapability(HumanoidBehaviorCapable.class, behaviorActor);
            }

            registerCapability(PassengerCapable.class, new PassengerCapable() {
                @Override
                public List<UUID> passengers() {
                    return entity.getPassengers().stream().map(Entity::getUuid).toList();
                }

                @Override
                public boolean addPassenger(ManagedEntity other) {
                    if (other instanceof MinestomManagedEntity minestomManagedEntity) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.addPassenger(minestomManagedEntity.entity);
                        return true;
                    }
                    return false;
                }

                @Override
                public boolean removePassenger(ManagedEntity other) {
                    if (other instanceof MinestomManagedEntity minestomManagedEntity) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.removePassenger(minestomManagedEntity.entity);
                        return true;
                    }
                    return false;
                }
            });

            registerCapability(LeashCapable.class, new LeashCapable() {
                @Override
                public java.util.Optional<UUID> leashHolder() {
                    Entity leashHolder = entity.getLeashHolder();
                    return leashHolder == null ? java.util.Optional.empty() : java.util.Optional.of(leashHolder.getUuid());
                }

                @Override
                    public boolean leashHolder(ManagedEntity other) {
                        MinestomManagedEntity.this.requireMutable();
                        if (other instanceof MinestomManagedEntity minestomManagedEntity) {
                        entity.setLeashHolder(minestomManagedEntity.entity);
                        return true;
                    }
                    return false;
                }

                @Override
                    public void clearLeash() {
                        MinestomManagedEntity.this.requireMutable();
                        entity.setLeashHolder(null);
                    }
            });

            registerCapability(PoseCapable.class, new PoseCapable() {
                @Override
                public sh.harold.library.entity.EntityPose pose() {
                    return fromMinestomPose(entity.getPose());
                }

                @Override
                public void pose(sh.harold.library.entity.EntityPose pose) {
                    MinestomManagedEntity.this.requireMutable();
                    entity.setSneaking(pose == sh.harold.library.entity.EntityPose.CROUCHING);
                    if (pose != sh.harold.library.entity.EntityPose.CROUCHING) {
                        entity.setPose(toMinestomPose(pose));
                    }
                    updateBasePose(pose);
                }
            });

            if (entity instanceof LivingEntity livingEntity) {
                registerCapability(Equipable.class, new Equipable() {
                    @Override
                    public java.util.Optional<ItemDescriptor> equipment(sh.harold.library.entity.EquipmentSlot slot) {
                        ItemStack stack = livingEntity.getEquipment(toMinestomSlot(slot));
                        if (stack == null || stack.isAir()) {
                            return java.util.Optional.empty();
                        }
                        return java.util.Optional.of(new ItemDescriptor(stack.material().key(), stack.amount()));
                    }

                    @Override
                    public void equipment(sh.harold.library.entity.EquipmentSlot slot, ItemDescriptor item) {
                        MinestomManagedEntity.this.requireMutable();
                        livingEntity.setEquipment(toMinestomSlot(slot), toItemStack(item));
                        updateBaseEquipment(slot, item);
                    }

                    @Override
                    public void clearEquipment(sh.harold.library.entity.EquipmentSlot slot) {
                        MinestomManagedEntity.this.requireMutable();
                        livingEntity.setEquipment(toMinestomSlot(slot), ItemStack.AIR);
                        updateBaseEquipment(slot, null);
                    }
                });
            }

            if (entity instanceof SafeMannequinEntity safeMannequin) {
                registerCapability(CollidableCapable.class, new CollidableCapable() {
                    @Override
                    public boolean collidable() {
                        return safeMannequin.hasEntityCollision();
                    }

                    @Override
                    public void collidable(boolean collidable) {
                        MinestomManagedEntity.this.requireMutable();
                        safeMannequin.collidable(collidable);
                    }
                });
                registerCapability(PersistenceCapable.class, new PersistenceCapable() {
                    @Override
                    public boolean persistent() {
                        return safeMannequin.persistent();
                    }

                    @Override
                    public void persistent(boolean persistent) {
                        MinestomManagedEntity.this.requireMutable();
                        safeMannequin.persistent(persistent);
                    }
                });
            }

            if (entity.getEntityMeta() instanceof AgeableMobMeta) {
                registerCapability(AgeableCapable.class, new AgeableCapable() {
                    @Override
                    public boolean adult() {
                        return !((AgeableMobMeta) entity.getEntityMeta()).isBaby();
                    }

                    @Override
                    public void adult(boolean adult) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(AgeableMobMeta.class, meta -> meta.setBaby(!adult));
                    }
                });
            }

            if (entity.getEntityMeta() instanceof VillagerMeta villagerMeta) {
                registerCapability(VillagerLikeCapable.class, new VillagerLikeCapable() {
                    @Override
                    public int level() {
                        return villagerMeta.getVillagerData().level().ordinal() + 1;
                    }

                    @Override
                    public void level(int level) {
                        MinestomManagedEntity.this.requireMutable();
                        VillagerMeta.Level[] levels = VillagerMeta.Level.values();
                        int index = Math.max(0, Math.min(level - 1, levels.length - 1));
                        entity.editEntityMeta(VillagerMeta.class, meta ->
                                meta.setVillagerData(meta.getVillagerData().withLevel(levels[index]))
                        );
                    }

                    @Override
                    public java.util.Optional<Key> profession() {
                        return java.util.Optional.of(villagerMeta.getVillagerData().profession().key());
                    }

                    @Override
                    public void profession(Key profession) {
                        MinestomManagedEntity.this.requireMutable();
                        VillagerProfession villagerProfession = VillagerProfession.fromKey(profession);
                        if (villagerProfession == null) {
                            throw new IllegalArgumentException("Unknown villager profession " + profession);
                        }
                        entity.editEntityMeta(VillagerMeta.class, meta ->
                                meta.setVillagerData(meta.getVillagerData().withProfession(villagerProfession))
                        );
                    }

                    @Override
                    public void clearProfession() {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(VillagerMeta.class, meta ->
                                meta.setVillagerData(meta.getVillagerData().withProfession(VillagerProfession.fromKey("minecraft:none")))
                        );
                    }
                });
            }

            if (entity.getEntityMeta() instanceof AbstractDisplayMeta displayMeta) {
                registerCapability(DisplayCapable.class, new DisplayCapable() {
                    @Override
                    public float width() {
                        return displayMeta.getWidth();
                    }

                    @Override
                    public void width(float width) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(AbstractDisplayMeta.class, meta -> meta.setWidth(width));
                    }

                    @Override
                    public float height() {
                        return displayMeta.getHeight();
                    }

                    @Override
                    public void height(float height) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(AbstractDisplayMeta.class, meta -> meta.setHeight(height));
                    }
                });
            }

            if (entity.getEntityMeta() instanceof TextDisplayMeta textDisplayMeta) {
                registerCapability(TextDisplayCapable.class, new TextDisplayCapable() {
                    @Override
                    public Component text() {
                        return textDisplayMeta.getText();
                    }

                    @Override
                    public void text(Component text) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(TextDisplayMeta.class, meta -> meta.setText(text));
                    }

                    @Override
                    public float width() {
                        return textDisplayMeta.getWidth();
                    }

                    @Override
                    public void width(float width) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(TextDisplayMeta.class, meta -> meta.setWidth(width));
                    }

                    @Override
                    public float height() {
                        return textDisplayMeta.getHeight();
                    }

                    @Override
                    public void height(float height) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(TextDisplayMeta.class, meta -> meta.setHeight(height));
                    }
                });
            }

            if (entity.getEntityMeta() instanceof ItemDisplayMeta itemDisplayMeta) {
                registerCapability(ItemDisplayCapable.class, new ItemDisplayCapable() {
                    @Override
                    public ItemDescriptor item() {
                        ItemStack stack = itemDisplayMeta.getItemStack();
                        return new ItemDescriptor(stack.material().key(), stack.amount());
                    }

                    @Override
                    public void item(ItemDescriptor item) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(ItemDisplayMeta.class, meta -> meta.setItemStack(toItemStack(item)));
                    }

                    @Override
                    public float width() {
                        return itemDisplayMeta.getWidth();
                    }

                    @Override
                    public void width(float width) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(ItemDisplayMeta.class, meta -> meta.setWidth(width));
                    }

                    @Override
                    public float height() {
                        return itemDisplayMeta.getHeight();
                    }

                    @Override
                    public void height(float height) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(ItemDisplayMeta.class, meta -> meta.setHeight(height));
                    }
                });
            }

            if (entity.getEntityMeta() instanceof BlockDisplayMeta blockDisplayMeta) {
                registerCapability(BlockDisplayCapable.class, new BlockDisplayCapable() {
                    @Override
                    public BlockDescriptor block() {
                        return new BlockDescriptor(blockDisplayMeta.getBlockStateId().key());
                    }

                    @Override
                    public void block(BlockDescriptor block) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(BlockDisplayMeta.class, meta -> meta.setBlockState(toBlock(block)));
                    }

                    @Override
                    public float width() {
                        return blockDisplayMeta.getWidth();
                    }

                    @Override
                    public void width(float width) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(BlockDisplayMeta.class, meta -> meta.setWidth(width));
                    }

                    @Override
                    public float height() {
                        return blockDisplayMeta.getHeight();
                    }

                    @Override
                    public void height(float height) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(BlockDisplayMeta.class, meta -> meta.setHeight(height));
                    }
                });
            }

            if (entity.getEntityMeta() instanceof MannequinMeta mannequinMeta) {
                registerCapability(SkinCapable.class, new SkinCapable() {
                    @Override
                    public java.util.Optional<SkinTexture> skin() {
                        return java.util.Optional.ofNullable(appliedSkin);
                    }

                    @Override
                    public void skin(SkinTexture skinTexture) {
                        MinestomManagedEntity.this.requireMutable();
                        appliedSkin = skinTexture;
                        entity.editEntityMeta(MannequinMeta.class, meta ->
                                meta.setProfile(new ResolvableProfile(new PlayerSkin(skinTexture.texture(), skinTexture.signature())))
                        );
                    }

                    @Override
                    public void clearSkin() {
                        MinestomManagedEntity.this.requireMutable();
                        appliedSkin = null;
                        entity.editEntityMeta(MannequinMeta.class, meta -> meta.setProfile(ResolvableProfile.EMPTY));
                    }
                });
            }
        }

        @Override
        protected void assertOwnerThread() {
            try {
                entity.acquirable().assertOwnership();
            } catch (AcquirableOwnershipException exception) {
                throw new IllegalStateException(
                        "Mutating Minestom entity " + id() + " requires the owning tick thread or an acquired entity context",
                        exception
                );
            }
        }

        @Override
        protected void doTeleport(EntityTransform transform) {
            MinestomFutureGuard.requireCompleted(entity.teleport(toPos(transform)), "entity teleport");
            updateBaseLook(transform.yaw(), transform.pitch());
        }

        @Override
        protected long interactionTick() {
            return entity.getAliveTicks();
        }

        @Override
        protected void doCustomName(Component customName) {
            entity.setCustomName(customName);
        }

        @Override
        protected void doClearCustomName() {
            entity.setCustomName(Component.empty());
        }

        @Override
        protected void doCustomNameVisible(boolean visible) {
            entity.setCustomNameVisible(visible);
        }

        @Override
        protected void doGlowing(boolean glowing) {
            entity.setGlowing(glowing);
        }

        @Override
        protected void doSilent(boolean silent) {
            entity.setSilent(silent);
        }

        @Override
        protected void doGravity(boolean gravity) {
            entity.setNoGravity(!gravity);
        }

        @Override
        protected void doInvulnerable(boolean invulnerable) {
            if (entity instanceof LivingEntity livingEntity) {
                livingEntity.setInvulnerable(invulnerable);
            } else if (invulnerable) {
                throw new UnsupportedOperationException("This Minestom entity type is not invulnerable-capable");
            }
        }

        @Override
        protected void doDespawn() {
            if (!retirementStarted.compareAndSet(false, true)) {
                return;
            }
            try {
                for (UUID viewerId : trackedViewerIds) {
                    unindexViewer(viewerId);
                }
                trackedViewerIds = Set.of();
                if (behaviorActor != null) {
                    behaviorActor.close();
                }
                if (behaviorPort != null) {
                    behaviorPort.close();
                }
                for (int index = retirementResources.size() - 1; index >= 0; index--) {
                    try {
                        retirementResources.get(index).close();
                    } catch (Exception ignored) {
                        // Continue retiring the remaining nonpersistent attachments.
                    }
                }
                retirementResources.clear();
                if (!entity.isRemoved()) {
                    entity.remove();
                }
                onDespawn.run();
                retirementCompletion.complete(null);
            } catch (RuntimeException | Error failure) {
                retirementCompletion.completeExceptionally(failure);
                throw failure;
            }
        }

        private void addRetirement(AutoCloseable resource) {
            requireMutable();
            retirementResources.add(Objects.requireNonNull(resource, "resource"));
        }

        @Override
        protected EntityInteractionResult observeInteraction(EntityInteractionContext context) {
            if (behaviorActor == null) {
                return EntityInteractionResult.PASS;
            }
            behaviorActor.observeInteraction(context.interactor().uniqueId(), context.action());
            return EntityInteractionResult.CONSUME;
        }

        private CompletionStage<Void> despawnAsync() {
            if (!spawned() || retirementStarted.get()) {
                return retirementCompletion;
            }
            entity.scheduleNextTick(ignored -> {
                try {
                    despawn();
                } catch (Throwable failure) {
                    retirementCompletion.completeExceptionally(failure);
                }
            });
            return retirementCompletion;
        }

        private void retireNative() {
            if (spawned() && !retirementStarted.get()) {
                despawn();
            }
        }

        private CompletionStage<Void> teleportAsync(EntityTransform transform) {
            requireSpawned();
            return entity.teleport(toPos(transform)).thenRun(() -> {
                publishTransform(transform);
                updateBaseLook(transform.yaw(), transform.pitch());
            });
        }

        private void tickBehavior() {
            if (behaviorActor == null
                    || behaviorPort == null
                    || entity.isRemoved()
                    || !behaviorActor.evaluationRequired()) {
                return;
            }
            behaviorPort.tick();
            Pos position = entity.getPosition();
            Set<UUID> nowTracked = entity.getViewers().stream()
                    .map(Entity::getUuid)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            Set<UUID> retired = new java.util.HashSet<>(trackedViewerIds);
            retired.removeAll(nowTracked);
            Set<UUID> acquired = new java.util.HashSet<>(nowTracked);
            acquired.removeAll(trackedViewerIds);
            for (UUID viewerId : acquired) {
                trackedBehaviorEntities.computeIfAbsent(viewerId, ignored -> ConcurrentHashMap.newKeySet())
                        .add(this);
            }
            for (UUID viewerId : retired) {
                unindexViewer(viewerId);
                behaviorActor.removeViewer(viewerId, NpcAttentionStack.ReleaseReason.UNTRACKED);
                behaviorPort.retireViewer(viewerId);
                lineOfSightByViewer.remove(viewerId);
                clearInteractionDebounce(viewerId);
            }
            trackedViewerIds = nowTracked;
            nativeViewerFrame = new NativeViewerFrame(
                    new Vec3(position.x(), position.y(), position.z()),
                    position.y() + entity.getEyeHeight(),
                    entity.getInstance()
            );
            behaviorActor.updateActorView(
                    nativeViewerFrame.position(),
                    Optional.ofNullable(entity.getInstance()).map(spaceResolver),
                    nowTracked.size()
            );
            behaviorActor.tick(entity.getAliveTicks());
            if (!behaviorActor.configured()) {
                for (UUID viewerId : trackedViewerIds) {
                    unindexViewer(viewerId);
                }
                trackedViewerIds = Set.of();
                lineOfSightByViewer.clear();
            }
        }

        private void unindexViewer(UUID viewerId) {
            Set<MinestomManagedEntity> indexed = trackedBehaviorEntities.get(viewerId);
            if (indexed == null) {
                return;
            }
            indexed.remove(this);
            if (indexed.isEmpty()) {
                trackedBehaviorEntities.remove(viewerId, indexed);
            }
        }

        private void updateBasePose(sh.harold.library.entity.EntityPose pose) {
            if (behaviorPort == null) {
                return;
            }
            NpcRenderFrame previous = behaviorBaseFrame;
            behaviorBaseFrame = new NpcRenderFrame(
                    previous.bodyYaw(),
                    previous.headYaw(),
                    previous.pitch(),
                    pose,
                    previous.equipment(),
                    previous.usingMainHand(),
                    previous.usingOffHand()
            );
            behaviorPort.updateBaseFrame(behaviorBaseFrame);
            behaviorActor.updateBaseFrame(behaviorBaseFrame);
        }

        private void updateBaseEquipment(sh.harold.library.entity.EquipmentSlot slot, ItemDescriptor item) {
            if (behaviorPort == null) {
                return;
            }
            NpcRenderFrame previous = behaviorBaseFrame;
            Map<sh.harold.library.entity.EquipmentSlot, ItemDescriptor> equipment =
                    new java.util.EnumMap<>(sh.harold.library.entity.EquipmentSlot.class);
            equipment.putAll(previous.equipment());
            if (item == null) {
                equipment.remove(slot);
            } else {
                equipment.put(slot, item);
            }
            behaviorBaseFrame = new NpcRenderFrame(
                    previous.bodyYaw(),
                    previous.headYaw(),
                    previous.pitch(),
                    previous.pose(),
                    equipment,
                    previous.usingMainHand(),
                    previous.usingOffHand()
            );
            behaviorPort.updateBaseFrame(behaviorBaseFrame);
            behaviorActor.updateBaseFrame(behaviorBaseFrame);
        }

        private void updateBaseLook(float yaw, float pitch) {
            if (behaviorPort == null) {
                return;
            }
            NpcRenderFrame previous = behaviorBaseFrame;
            behaviorBaseFrame = new NpcRenderFrame(
                    yaw,
                    yaw,
                    pitch,
                    previous.pose(),
                    previous.equipment(),
                    previous.usingMainHand(),
                    previous.usingOffHand()
            );
            behaviorPort.updateBaseFrame(behaviorBaseFrame);
            behaviorActor.updateBaseFrame(behaviorBaseFrame);
        }

        private void observeViewer(net.minestom.server.entity.Player player) {
            behaviorPort.flushViewer(player);
            NpcBehaviorProfile currentProfile = behaviorActor.profile().orElse(null);
            if (currentProfile == null) {
                return;
            }
            NativeViewerFrame npc = nativeViewerFrame;
            Pos playerPosition = player.getPosition();
            boolean sameSpace = npc.instance() != null && player.getInstance() == npc.instance();
            double deltaX = playerPosition.x() - npc.position().x();
            double baseDeltaY = playerPosition.y() - npc.position().y();
            double deltaY = (playerPosition.y() + player.getEyeHeight()) - npc.eyeY();
            double deltaZ = playerPosition.z() - npc.position().z();
            double horizontalSquared = deltaX * deltaX + deltaZ * deltaZ;
            double verticalDifference = Math.abs(baseDeltaY);
            double exitRadius = currentProfile.attention().exitRadius()
                    * currentProfile.tuning().radiusMultiplier();
            boolean locallyEligible = sameSpace
                    && horizontalSquared <= exitRadius * exitRadius
                    && verticalDifference <= currentProfile.attention().maximumVerticalDifference();
            LineOfSightSample lineOfSightSample = null;
            if (currentProfile.attention().lineOfSightRequired() && locallyEligible) {
                long stagger = id().getLeastSignificantBits() ^ player.getUuid().getLeastSignificantBits();
                lineOfSightSample = lineOfSightByViewer.get(player.getUuid());
                int interval = currentProfile.attention().lineOfSightProbeIntervalTicks();
                boolean probe = lineOfSightSample == null
                        || Math.floorMod(player.getAliveTicks() + stagger, interval) == 0L;
                if (probe) {
                    boolean visible = !entity.isRemoved() && entity.hasLineOfSight(player);
                    lineOfSightSample = lineOfSightSample == null
                            ? new LineOfSightSample(visible, 1L)
                            : lineOfSightSample.next(visible);
                    lineOfSightByViewer.put(player.getUuid(), lineOfSightSample);
                }
            } else if (!locallyEligible) {
                lineOfSightByViewer.remove(player.getUuid());
            }
            boolean lineOfSight = !currentProfile.attention().lineOfSightRequired()
                    || (lineOfSightSample != null && lineOfSightSample.visible());
            long lineOfSightEpoch = lineOfSightSample == null ? 0L : lineOfSightSample.epoch();
            float yaw = (float) Math.toDegrees(Math.atan2(-deltaX, deltaZ));
            float pitch = (float) -Math.toDegrees(Math.atan2(deltaY, Math.sqrt(horizontalSquared)));
            behaviorActor.observeViewer(new NpcAttentionStack.Observation(
                    player.getUuid(),
                    true,
                    sameSpace,
                    horizontalSquared,
                    verticalDifference,
                    lineOfSight,
                    new NpcAttentionStack.GazeTarget(yaw, pitch)
            ), lineOfSightEpoch);
        }

        private record LineOfSightSample(boolean visible, long epoch) {
            private LineOfSightSample next(boolean nextVisible) {
                return new LineOfSightSample(nextVisible, epoch + 1L);
            }
        }

        private record NativeViewerFrame(Vec3 position, double eyeY, Instance instance) {
        }
    }

    /** A native mannequin with safe physical behavior and no synthetic hitbox. */
    private static final class SafeMannequinEntity extends LivingEntity {
        private boolean persistent = true;
        private Runnable renderTick = () -> {
        };

        private SafeMannequinEntity(EntityType entityType) {
            super(entityType);
            setHasPhysics(false);
            setNoGravity(true);
            setInvulnerable(true);
            collidable(false);
            editEntityMeta(MannequinMeta.class, meta -> meta.setImmovable(true));
        }

        @Override
        public void tick(long time) {
            super.tick(time);
            renderTick.run();
        }

        @Override
        public void takeKnockback(float strength, double x, double z) {
            // Safe NPC mannequins never accept vanilla knockback.
        }

        private void collidable(boolean collidable) {
            this.collidesWithEntities = collidable;
        }

        private boolean persistent() {
            return persistent;
        }

        private void persistent(boolean persistent) {
            this.persistent = persistent;
        }
    }

    private static final class MinestomHouseRenderer implements HousePresentationRenderer {
        private static final double LINE_SPACING = 0.3;
        private static final double SPACER_WIDTH = 0.01;

        private final List<Entity> attachments = new ArrayList<>();

        private MinestomHouseRenderer(Entity anchor, HousePresentation presentation) {
            List<Component> lines = presentation.lines();
            Entity vehicle = anchor;
            for (int index = lines.size() - 1; index >= 0; index--) {
                Entity display = spawnLine(anchor.getInstance(), anchor.getPosition(), lines.get(index));
                vehicle.addPassenger(display);
                attachments.add(display);
                vehicle = display;
                if (index > 0) {
                    Entity spacer = spawnSpacer(anchor.getInstance(), anchor.getPosition());
                    vehicle.addPassenger(spacer);
                    attachments.add(spacer);
                    vehicle = spacer;
                }
            }
        }

        @Override
        public void teleport(EntityTransform transform) {
            // Mounted passenger stacks follow the anchor entity automatically.
        }

        @Override
        public void close() {
            for (int index = attachments.size() - 1; index >= 0; index--) {
                attachments.get(index).remove();
            }
            attachments.clear();
        }

        private static Entity spawnLine(Instance instance, Pos anchorPosition, Component text) {
            Entity entity = createEntity(EntityTypes.ARMOR_STAND);
            entity.editEntityMeta(ArmorStandMeta.class, meta -> {
                meta.setSmall(true);
                meta.setHasNoBasePlate(true);
                meta.setMarker(true);
            });
            entity.setCustomName(text);
            entity.setCustomNameVisible(true);
            entity.setInvisible(true);
            entity.setSilent(true);
            entity.setNoGravity(true);
            entity.setHasPhysics(false);
            entity.setBoundingBox(SPACER_WIDTH, SPACER_WIDTH, SPACER_WIDTH);
            MinestomFutureGuard.requireCompleted(entity.setInstance(instance, anchorPosition), "house renderer line spawn");
            return entity;
        }

        private static Entity spawnSpacer(Instance instance, Pos anchorPosition) {
            Entity entity = createEntity(EntityTypes.INTERACTION);
            entity.editEntityMeta(InteractionMeta.class, meta -> {
                meta.setWidth((float) SPACER_WIDTH);
                meta.setHeight((float) LINE_SPACING);
                meta.setResponse(false);
            });
            entity.setCustomName(Component.empty());
            entity.setCustomNameVisible(false);
            entity.setInvisible(true);
            entity.setSilent(true);
            entity.setNoGravity(true);
            entity.setHasPhysics(false);
            entity.setBoundingBox(SPACER_WIDTH, LINE_SPACING, SPACER_WIDTH);
            MinestomFutureGuard.requireCompleted(entity.setInstance(instance, anchorPosition), "house renderer spacer spawn");
            return entity;
        }
    }
}
