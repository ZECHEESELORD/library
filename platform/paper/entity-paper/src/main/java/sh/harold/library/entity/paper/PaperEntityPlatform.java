package sh.harold.library.entity.paper;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.github.retrooper.packetevents.PacketEventsAPI;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.entity.Leashable;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import sh.harold.library.entity.BlockDescriptor;
import sh.harold.library.entity.EntitySpec;
import sh.harold.library.entity.EntityInteractionAction;
import sh.harold.library.entity.EntityInteractionContext;
import sh.harold.library.entity.EntityInteractionResult;
import sh.harold.library.entity.EntityTransform;
import sh.harold.library.entity.EntityTypeKey;
import sh.harold.library.entity.EntityTypes;
import sh.harold.library.entity.EquipmentSlot;
import sh.harold.library.entity.InteractionHand;
import sh.harold.library.entity.InteractorRef;
import sh.harold.library.entity.ItemDescriptor;
import sh.harold.library.entity.ManagedEntity;
import sh.harold.library.entity.SkinTexture;
import sh.harold.library.entity.capability.AiCapable;
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
import sh.harold.library.npc.behavior.NpcAttentionLease;
import sh.harold.library.npc.behavior.NpcBehaviorProfile;
import sh.harold.library.npc.behavior.NpcBehaviorSnapshot;
import sh.harold.library.npc.behavior.NpcConversationRegistry;
import sh.harold.library.npc.behavior.NpcConversationStagingMode;
import sh.harold.library.npc.behavior.NpcPlayback;
import sh.harold.library.npc.behavior.NpcRoutine;
import sh.harold.library.npc.behavior.core.NpcAttentionStack;
import sh.harold.library.npc.behavior.core.NpcBehaviorActor;
import sh.harold.library.npc.behavior.core.NpcConversationParticipant;
import sh.harold.library.npc.behavior.core.NpcRenderFrame;
import sh.harold.library.npc.behavior.core.StandardNpcConversationRegistry;
import sh.harold.library.spatial.AnchorRef;
import sh.harold.library.spatial.AnchorSnapshot;
import sh.harold.library.spatial.Frame3;
import sh.harold.library.spatial.SpaceId;
import sh.harold.library.spatial.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.Consumer;

public final class PaperEntityPlatform implements Listener, AutoCloseable {

    private final Plugin plugin;
    private final Map<UUID, PaperManagedEntity> entities = new ConcurrentHashMap<>();
    private final Map<Integer, PaperManagedEntity> entitiesByNativeId = new ConcurrentHashMap<>();
    private final Map<UUID, PaperViewerLoop> viewerLoops = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> trackedNpcIndex = new ConcurrentHashMap<>();
    private final PaperOverheadLayerManager overheadLayers = new PaperOverheadLayerManager();
    private final PaperLineOfSightSampler lineOfSightSampler;
    private final PacketEventsAPI<?> packetEvents;
    private final PaperMannequinPacketCodec packetCodec;
    private final StandardNpcConversationRegistry conversations = new StandardNpcConversationRegistry();
    private final ScheduledTask conversationTask;
    private final LifecycleGate lifecycle = new LifecycleGate();
    private final AtomicBoolean shutdownFinalized = new AtomicBoolean();
    private final CompletableFuture<Void> closeCompletion = new CompletableFuture<>();

    public PaperEntityPlatform(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.packetEvents = PaperPacketEventsRequirement.verifyRuntime(plugin);
        this.packetCodec = new PaperMannequinPacketCodec(packetEvents);
        this.lineOfSightSampler = new PaperLineOfSightSampler(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getOnlinePlayers().forEach(this::startViewerLoop);
        this.conversationTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                ignored -> conversations.tick(),
                1L,
                1L
        );
    }

    public NpcConversationRegistry conversationRegistry() {
        requireOpen();
        return conversations;
    }

    /** Readable alias for {@link #conversationRegistry()}. */
    public NpcConversationRegistry conversations() {
        return conversationRegistry();
    }

    public ManagedEntity spawn(World world, EntitySpec spec) {
        requireOpen();
        Objects.requireNonNull(world, "world");
        EntitySpecValidator.validate(spec);
        Location location = toLocation(world, spec.transform());
        requireOwned(location, "spawn Paper entity");
        return spawnOwned(world, spec, location);
    }

    public CompletionStage<ManagedEntity> spawnAsync(World world, EntitySpec spec) {
        requireOpen();
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(spec, "spec");
        EntitySpecValidator.validate(spec);
        Location location = toLocation(world, spec.transform());
        CompletableFuture<ManagedEntity> completion = new CompletableFuture<>();
        try {
            plugin.getServer().getRegionScheduler().execute(plugin, location, () -> {
                if (lifecycle.closed()) {
                    completion.completeExceptionally(new IllegalStateException("Paper entity platform is closed"));
                    return;
                }
                try {
                    completion.complete(spawnOwned(world, spec, location));
                } catch (Throwable failure) {
                    completion.completeExceptionally(failure);
                }
            });
        } catch (Throwable failure) {
            completion.completeExceptionally(failure);
        }
        return completion;
    }

    private ManagedEntity spawnOwned(World world, EntitySpec spec, Location location) {
        return lifecycle.requireOpen(() -> {
            EntityType type = toBukkitType(spec.type());
            Entity entity = world.spawnEntity(location, type);
            PaperManagedEntity managedEntity;
            try {
                managedEntity = new PaperManagedEntity(entity, spec, () -> {
                    entities.remove(entity.getUniqueId());
                    entitiesByNativeId.remove(entity.getEntityId());
                    packetCodec.removeEntity(entity.getEntityId());
                });
            } catch (Throwable failure) {
                entity.remove();
                throw failure;
            }
            entities.put(managedEntity.id(), managedEntity);
            entitiesByNativeId.put(entity.getEntityId(), managedEntity);
            return managedEntity;
        });
    }

    public HouseServiceEntity spawnService(World world, HouseServiceSpec serviceSpec) {
        requireOpen();
        HouseValidator.validate(serviceSpec);
        HousePresentation presentation = HousePresentationFactory.create(serviceSpec);
        ManagedEntity anchor = spawn(world, serviceSpec.entitySpec());
        return finishServiceSpawn(serviceSpec, presentation, anchor, false).service();
    }

    public CompletionStage<HouseServiceEntity> spawnServiceAsync(World world, HouseServiceSpec serviceSpec) {
        requireOpen();
        HouseValidator.validate(serviceSpec);
        HousePresentation presentation = HousePresentationFactory.create(serviceSpec);
        return spawnAsync(world, serviceSpec.entitySpec()).thenCompose(anchor -> {
            ServiceSpawn result = finishServiceSpawn(serviceSpec, presentation, anchor, true);
            CompletableFuture<HouseServiceEntity> completion = new CompletableFuture<>();
            result.configuration().whenComplete((ignored, failure) -> {
                if (failure == null) {
                    completion.complete(result.service());
                    return;
                }
                ((PaperManagedEntity) anchor).despawnAsync().whenComplete((retired, retirementFailure) -> {
                    if (retirementFailure != null && retirementFailure != failure) {
                        failure.addSuppressed(retirementFailure);
                    }
                    completion.completeExceptionally(failure);
                });
            });
            return completion;
        });
    }

    private ServiceSpawn finishServiceSpawn(
            HouseServiceSpec serviceSpec,
            HousePresentation presentation,
            ManagedEntity anchor,
            boolean awaitConfiguration
    ) {
        anchor.clearCustomName();
        anchor.customNameVisible(false);

        PaperManagedEntity paperAnchor = (PaperManagedEntity) anchor;
        PaperOverheadLayerManager.Reservation reservation = overheadLayers.reserveHouse(
                anchor.id(),
                presentation.lines().size()
        );
        HousePresentationRenderer renderer;
        try {
            renderer = new PaperHouseRenderer(paperAnchor.entity, presentation, reservation);
        } catch (Throwable failure) {
            reservation.close();
            anchor.despawn();
            throw failure;
        }
        StandardHouseServiceEntity serviceEntity = new StandardHouseServiceEntity(anchor, presentation, renderer);
        paperAnchor.addRetirement(renderer);

        AtomicReference<HouseServiceEntity> reference = new AtomicReference<>(serviceEntity);
        if (serviceSpec.clickHandler().isPresent() || serviceSpec.entitySpec().interactionHandler().isPresent()) {
            anchor.interactionHandler(context -> {
                EntityInteractionResult result = serviceSpec.entitySpec().interactionHandler()
                        .map(handler -> handler.onInteract(context))
                        .orElse(EntityInteractionResult.PASS);
                serviceSpec.clickHandler().ifPresent(handler -> handler.onClick(
                        new HouseServiceClickContext(
                                reference.get(),
                                context.interactor(),
                                context.action(),
                                context.hand()
                        )
                ));
                return serviceSpec.clickHandler().isPresent() ? EntityInteractionResult.CONSUME : result;
            });
        }
        CompletionStage<Void> configuration = serviceSpec.behaviorProfile()
                .map(profile -> anchor.requireCapability(HumanoidBehaviorCapable.class).configure(profile))
                .orElseGet(() -> CompletableFuture.completedFuture(null));
        if (!awaitConfiguration) {
            configuration.whenComplete((ignored, failure) -> {
                if (failure == null) {
                    return;
                }
                plugin.getSLF4JLogger().error("Failed to configure House NPC {}; retiring the incomplete service",
                        anchor.id(), failure);
                paperAnchor.despawnAsync().whenComplete((retired, retirementFailure) -> {
                    if (retirementFailure != null) {
                        plugin.getSLF4JLogger().warn("Failed to retire incomplete House NPC {}",
                                anchor.id(), retirementFailure);
                    }
                });
            });
        }
        return new ServiceSpawn(serviceEntity, configuration);
    }

    public CompletionStage<Void> teleportAsync(ManagedEntity managedEntity, EntityTransform transform) {
        Objects.requireNonNull(managedEntity, "managedEntity");
        Objects.requireNonNull(transform, "transform");
        if (!(managedEntity instanceof PaperManagedEntity paperEntity)
                || entities.get(managedEntity.id()) != managedEntity) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Entity is not owned by this platform"));
        }
        return paperEntity.teleportAsync(transform);
    }

    public CompletionStage<Void> closeAsync() {
        Optional<ShutdownSnapshot> claimed = lifecycle.closeAndSnapshot(() -> new ShutdownSnapshot(
                List.copyOf(viewerLoops.values()),
                List.copyOf(entities.values())
        ));
        if (claimed.isEmpty()) {
            return closeCompletion;
        }
        ShutdownSnapshot snapshot = claimed.orElseThrow();
        Throwable startFailure = beginShutdown();
        List<CompletableFuture<Void>> retirements = new ArrayList<>();
        for (PaperViewerLoop loop : snapshot.viewers()) {
            try {
                retirements.add(loop.closeAsync().toCompletableFuture());
            } catch (Throwable retirementFailure) {
                retirements.add(CompletableFuture.failedFuture(retirementFailure));
            }
        }
        for (PaperManagedEntity entity : snapshot.entities()) {
            try {
                retirements.add(entity.despawnAsync().toCompletableFuture());
            } catch (Throwable retirementFailure) {
                retirements.add(CompletableFuture.failedFuture(retirementFailure));
            }
        }
        CompletableFuture.allOf(retirements.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) -> {
            finishShutdown(combineFailures(startFailure, failure));
        });
        return closeCompletion;
    }

    @Override
    public void close() {
        if (lifecycle.closed()) {
            if (!closeCompletion.isDone()) {
                throw new IllegalStateException("Paper entity platform is closing asynchronously; await closeAsync() instead");
            }
            closeCompletion.join();
            return;
        }
        Optional<ShutdownSnapshot> claimed = lifecycle.closeAndSnapshot(() -> {
            List<PaperManagedEntity> entitySnapshot = List.copyOf(entities.values());
            List<PaperViewerLoop> viewerSnapshot = List.copyOf(viewerLoops.values());
            for (PaperManagedEntity entity : entitySnapshot) {
                requireOwned(entity.entity, "close Paper entity platform");
            }
            for (PaperViewerLoop viewerLoop : viewerSnapshot) {
                requireOwned(viewerLoop.player, "close Paper entity platform");
            }
            return new ShutdownSnapshot(viewerSnapshot, entitySnapshot);
        });
        if (claimed.isEmpty()) {
            if (!closeCompletion.isDone()) {
                throw new IllegalStateException("Paper entity platform is closing asynchronously; await closeAsync() instead");
            }
            closeCompletion.join();
            return;
        }
        ShutdownSnapshot snapshot = claimed.orElseThrow();

        Throwable failure = beginShutdown();
        for (PaperViewerLoop viewerLoop : snapshot.viewers()) {
            try {
                viewerLoop.closeOwned();
            } catch (Throwable retirementFailure) {
                failure = combineFailures(failure, retirementFailure);
            }
        }
        for (PaperManagedEntity entity : snapshot.entities()) {
            try {
                entity.despawn();
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
            HandlerList.unregisterAll(this);
        } catch (Throwable unregisterFailure) {
            failure = combineFailures(failure, unregisterFailure);
        }
        try {
            conversationTask.cancel();
        } catch (Throwable cancellationFailure) {
            failure = combineFailures(failure, cancellationFailure);
        }
        try {
            conversations.close();
        } catch (Throwable conversationFailure) {
            failure = combineFailures(failure, conversationFailure);
        }
        return failure;
    }

    private void finishShutdown(Throwable failure) {
        if (!shutdownFinalized.compareAndSet(false, true)) {
            return;
        }
        viewerLoops.clear();
        trackedNpcIndex.clear();
        entities.clear();
        entitiesByNativeId.clear();
        overheadLayers.clear();
        try {
            packetCodec.close();
        } catch (Throwable codecFailure) {
            failure = combineFailures(failure, codecFailure);
        }
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

    /** Serializes registrations with the one-way shutdown snapshot. */
    static final class LifecycleGate {
        private volatile boolean closed;

        synchronized <T> T requireOpen(Supplier<T> action) {
            Objects.requireNonNull(action, "action");
            if (closed) {
                throw new IllegalStateException("Paper entity platform is closed");
            }
            return action.get();
        }

        synchronized boolean ifOpen(Runnable action) {
            Objects.requireNonNull(action, "action");
            if (closed) {
                return false;
            }
            action.run();
            return true;
        }

        synchronized <T> Optional<T> closeAndSnapshot(Supplier<T> snapshot) {
            Objects.requireNonNull(snapshot, "snapshot");
            if (closed) {
                return Optional.empty();
            }
            T value = Objects.requireNonNull(snapshot.get(), "snapshot result");
            closed = true;
            return Optional.of(value);
        }

        boolean closed() {
            return closed;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        PaperManagedEntity entity = entities.get(event.getRightClicked().getUniqueId());
        if (entity != null) {
            InteractionHand hand = event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND
                    ? InteractionHand.MAIN_HAND
                    : InteractionHand.OFF_HAND;
            EntityInteractionResult result = entity.handleInteraction(
                    new InteractorRef(event.getPlayer().getUniqueId()),
                    EntityInteractionAction.USE,
                    Optional.of(hand)
            );
            if (result == EntityInteractionResult.CONSUME) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        PaperManagedEntity entity = entities.get(event.getEntity().getUniqueId());
        if (entity != null && event.getDamager() instanceof Player player) {
            EntityInteractionResult result = entity.handleInteraction(
                    new InteractorRef(player.getUniqueId()),
                    EntityInteractionAction.ATTACK,
                    Optional.empty()
            );
            if (result == EntityInteractionResult.CONSUME) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onTrack(PlayerTrackEntityEvent event) {
        PaperManagedEntity entity = entities.get(event.getEntity().getUniqueId());
        if (entity != null) {
            entity.reapplyFor(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onUntrack(PlayerUntrackEntityEvent event) {
        PaperManagedEntity entity = entities.get(event.getEntity().getUniqueId());
        if (entity != null) {
            entity.retireViewer(event.getPlayer().getUniqueId(), NpcAttentionStack.ReleaseReason.UNTRACKED);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        startViewerLoop(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        retireViewer(event.getPlayer().getUniqueId(), NpcAttentionStack.ReleaseReason.UNTRACKED);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        retireViewerSessions(event.getPlayer().getUniqueId(), NpcAttentionStack.ReleaseReason.UNTRACKED);
    }

    @EventHandler
    public void onNativeRemove(EntityRemoveFromWorldEvent event) {
        PaperManagedEntity entity = entities.get(event.getEntity().getUniqueId());
        if (entity != null) {
            entity.retireNative();
        }
    }

    private static Location toLocation(World world, EntityTransform transform) {
        return new Location(world, transform.x(), transform.y(), transform.z(), transform.yaw(), transform.pitch());
    }

    private void requireOwned(Location location, String action) {
        if (!plugin.getServer().isOwnedByCurrentRegion(location)) {
            throw new IllegalStateException(action + " requires the location's owning Paper/Folia region; use the async method");
        }
    }

    private void requireOwned(Entity entity, String action) {
        if (!plugin.getServer().isOwnedByCurrentRegion(entity)) {
            throw new IllegalStateException(action + " requires the entity's owning Paper/Folia region; use the async method");
        }
    }

    private void requireOpen() {
        if (lifecycle.closed()) {
            throw new IllegalStateException("Paper entity platform is closed");
        }
    }

    private static EntityType toBukkitType(EntityTypeKey type) {
        if (EntityTypes.PLAYER_LIKE_HUMANOID.equals(type)) {
            return EntityType.MANNEQUIN;
        }
        String name = type.key().value().toUpperCase(Locale.ROOT).replace('-', '_');
        return EntityType.valueOf(name);
    }

    static boolean supportsHumanoidBehavior(EntityTypeKey type) {
        return EntityTypes.PLAYER_LIKE_HUMANOID.equals(Objects.requireNonNull(type, "type"));
    }

    private void startViewerLoop(Player player) {
        lifecycle.ifOpen(() -> {
            viewerLoops.computeIfAbsent(player.getUniqueId(), ignored -> {
                packetCodec.beginConnection(player.getUniqueId());
                return new PaperViewerLoop(player);
            });
        });
    }

    private boolean enqueueViewerWork(
            UUID viewerId,
            Consumer<Player> action,
            Runnable retired,
            ViewerWorkPriority priority
    ) {
        PaperViewerLoop loop = viewerLoops.get(Objects.requireNonNull(viewerId, "viewerId"));
        return loop != null && loop.submit(action, retired, priority);
    }

    private void retireViewer(UUID viewerId, NpcAttentionStack.ReleaseReason reason) {
        PaperViewerLoop loop = viewerLoops.remove(viewerId);
        if (loop != null) {
            loop.closeAsync();
        } else {
            packetCodec.endConnection(viewerId);
        }
        List.copyOf(entities.values()).forEach(entity -> entity.retireViewer(viewerId, reason));
    }

    private void retireViewerSessions(UUID viewerId, NpcAttentionStack.ReleaseReason reason) {
        PaperViewerLoop loop = viewerLoops.get(viewerId);
        if (loop != null) {
            loop.resetObservations();
        }
        List.copyOf(entities.values()).forEach(entity -> entity.retireViewer(viewerId, reason));
    }

    private Optional<AnchorSnapshot> resolveAnchor(AnchorRef anchor) {
        Objects.requireNonNull(anchor, "anchor");
        if (anchor instanceof AnchorRef.Fixed fixed) {
            return Optional.of(fixed.snapshot());
        }
        if (anchor instanceof AnchorRef.Entity entityRef) {
            PaperManagedEntity target = entities.get(entityRef.entityId());
            PaperActorView view = target == null ? null : target.actorView;
            if (view == null || !view.spaceId().equals(entityRef.spaceId())) {
                return Optional.empty();
            }
            return Optional.of(view.anchor());
        }
        AnchorRef.Offset offset = (AnchorRef.Offset) anchor;
        return resolveAnchor(offset.base()).map(snapshot -> snapshot.translated(offset.localOffset()));
    }

    private static SpaceId spaceId(World world) {
        return SpaceId.of("paper", world.getUID().toString());
    }

    private static Frame3 frame(Vec3 position, float yaw, float pitch) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        Vec3 forward = new Vec3(
                -Math.sin(yawRadians) * Math.cos(pitchRadians),
                -Math.sin(pitchRadians),
                Math.cos(yawRadians) * Math.cos(pitchRadians)
        );
        return Frame3.of(position, forward, Vec3.UNIT_Y);
    }

    private static ItemStack toItemStack(ItemDescriptor descriptor) {
        Material material = Material.matchMaterial(descriptor.key().asString());
        if (material == null) {
            throw new IllegalArgumentException("Unsupported item key " + descriptor.key());
        }
        return new ItemStack(material, descriptor.amount());
    }

    private static BlockData toBlockData(BlockDescriptor descriptor) {
        Material material = Material.matchMaterial(descriptor.key().asString());
        if (material == null || !material.isBlock()) {
            throw new IllegalArgumentException("Unsupported block key " + descriptor.key());
        }
        return Bukkit.createBlockData(material);
    }

    private static org.bukkit.inventory.EquipmentSlot toBukkitSlot(EquipmentSlot slot) {
        return switch (slot) {
            case MAIN_HAND -> org.bukkit.inventory.EquipmentSlot.HAND;
            case OFF_HAND -> org.bukkit.inventory.EquipmentSlot.OFF_HAND;
            case FEET -> org.bukkit.inventory.EquipmentSlot.FEET;
            case LEGS -> org.bukkit.inventory.EquipmentSlot.LEGS;
            case CHEST -> org.bukkit.inventory.EquipmentSlot.CHEST;
            case HEAD -> org.bukkit.inventory.EquipmentSlot.HEAD;
            case BODY -> org.bukkit.inventory.EquipmentSlot.BODY;
        };
    }

    static NpcRenderFrame withAuthoredLook(NpcRenderFrame base, float yaw, float pitch) {
        Objects.requireNonNull(base, "base");
        return base.withLook(yaw, yaw, pitch);
    }

    static NpcRenderFrame withAuthoredPose(
            NpcRenderFrame base,
            sh.harold.library.entity.EntityPose pose
    ) {
        Objects.requireNonNull(base, "base");
        return new NpcRenderFrame(
                base.bodyYaw(),
                base.headYaw(),
                base.pitch(),
                Objects.requireNonNull(pose, "pose"),
                base.equipment(),
                base.usingMainHand(),
                base.usingOffHand()
        );
    }

    static NpcRenderFrame withAuthoredEquipment(
            NpcRenderFrame base,
            EquipmentSlot slot,
            Optional<ItemDescriptor> item
    ) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(item, "item");
        Map<EquipmentSlot, ItemDescriptor> equipment = new EnumMap<>(EquipmentSlot.class);
        equipment.putAll(base.equipment());
        if (item.isPresent()) {
            equipment.put(slot, item.orElseThrow());
        } else {
            equipment.remove(slot);
        }
        return new NpcRenderFrame(
                base.bodyYaw(),
                base.headYaw(),
                base.pitch(),
                base.pose(),
                equipment,
                base.usingMainHand(),
                base.usingOffHand()
        );
    }

    private static Pose toBukkitPose(sh.harold.library.entity.EntityPose pose) {
        return switch (pose) {
            case STANDING -> Pose.STANDING;
            case CROUCHING -> Pose.SNEAKING;
            case SLEEPING -> Pose.SLEEPING;
            case SITTING -> Pose.SITTING;
            case SWIMMING -> Pose.SWIMMING;
            case SPIN_ATTACK -> Pose.SPIN_ATTACK;
        };
    }

    private static sh.harold.library.entity.EntityPose fromBukkitPose(Pose pose) {
        return switch (pose) {
            case SNEAKING -> sh.harold.library.entity.EntityPose.CROUCHING;
            case SLEEPING -> sh.harold.library.entity.EntityPose.SLEEPING;
            case SITTING -> sh.harold.library.entity.EntityPose.SITTING;
            case SWIMMING -> sh.harold.library.entity.EntityPose.SWIMMING;
            case SPIN_ATTACK -> sh.harold.library.entity.EntityPose.SPIN_ATTACK;
            default -> sh.harold.library.entity.EntityPose.STANDING;
        };
    }

    /** Exactly one ownership-lane task per online viewer. */
    private final class PaperViewerLoop {
        private static final int MAX_INTERACTIVE_WORK_PER_DRAIN = 64;
        private static final int MAX_BACKGROUND_WORK_PER_TICK = 64;

        private final Player player;
        private final Map<UUID, LosState> lineOfSight = new HashMap<>();
        private final Set<UUID> observed = new HashSet<>();
        private final PaperViewerWorkQueue<ViewerWork> work = new PaperViewerWorkQueue<>();
        private final CompletableFuture<Void> retirement = new CompletableFuture<>();
        private final AtomicBoolean retired = new AtomicBoolean();
        private final ScheduledTask task;
        private long tick;

        private PaperViewerLoop(Player player) {
            this.player = Objects.requireNonNull(player, "player");
            this.task = player.getScheduler().runAtFixedRate(
                    plugin,
                    ignored -> sample(),
                    this::retiredByScheduler,
                    1L,
                    1L
            );
        }

        private void sample() {
            if (lifecycle.closed() || retired.get() || !player.isOnline()) {
                return;
            }
            drainWork();
            tick++;
            Location playerLocation = player.getLocation();
            Location eye = player.getEyeLocation();
            UUID viewerId = player.getUniqueId();
            UUID worldId = playerLocation.getWorld().getUID();
            Set<UUID> seen = new HashSet<>();

            Set<UUID> nearbyNpcIds = Set.copyOf(trackedNpcIndex.getOrDefault(viewerId, Set.of()));
            for (UUID nearbyNpcId : nearbyNpcIds) {
                PaperManagedEntity managed = entities.get(nearbyNpcId);
                if (managed == null) {
                    continue;
                }
                NpcBehaviorActor actor = managed.behaviorActor;
                NpcBehaviorProfile profile = actor == null ? null : actor.profile().orElse(null);
                PaperActorView view = managed.actorView;
                if (profile == null || view == null) {
                    continue;
                }

                boolean tracked = view.trackingViewers().contains(viewerId);
                boolean sameSpace = view.worldId().equals(worldId);
                if (!tracked) {
                    if (observed.remove(managed.id())) {
                        actor.removeViewer(viewerId, NpcAttentionStack.ReleaseReason.UNTRACKED);
                    }
                    lineOfSight.remove(managed.id());
                    continue;
                }
                seen.add(managed.id());
                observed.add(managed.id());

                Vec3 actorPosition = view.position();
                double dx = eye.getX() - actorPosition.x();
                double dy = eye.getY() - actorPosition.y();
                double dz = eye.getZ() - actorPosition.z();
                double horizontalSquared = dx * dx + dz * dz;
                double vertical = Math.abs(playerLocation.getY() - actorPosition.y());
                double horizontal = Math.sqrt(horizontalSquared);
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(1.0e-9, horizontal)));

                LosState los = lineOfSight.computeIfAbsent(managed.id(), ignored -> new LosState());
                double exitRadius = profile.attention().exitRadius() * profile.tuning().radiusMultiplier();
                boolean locallyEligible = sameSpace
                        && horizontalSquared <= exitRadius * exitRadius
                        && vertical <= profile.attention().maximumVerticalDifference();
                if (!profile.attention().lineOfSightRequired()) {
                    los.assumeClear();
                } else if (locallyEligible && shouldProbe(managed.id(), viewerId, profile)) {
                    Vector from = new Vector(actorPosition.x(), actorPosition.y() + 1.62D, actorPosition.z());
                    Vector to = eye.toVector();
                    long epoch = los.beginProbe();
                    if (epoch >= 0L) {
                        lineOfSightSampler.sample(playerLocation.getWorld(), from, to, epoch)
                                .whenComplete((result, failure) -> los.complete(epoch, result, failure));
                    }
                } else if (!locallyEligible) {
                    los.failClosed();
                }

                actor.observeViewer(new NpcAttentionStack.Observation(
                        viewerId,
                        true,
                        sameSpace,
                        horizontalSquared,
                        vertical,
                        los.clear(),
                        new NpcAttentionStack.GazeTarget(yaw, pitch)
                ), los.resultEpoch());
            }

            for (UUID entityId : Set.copyOf(observed)) {
                if (!seen.contains(entityId)) {
                    PaperManagedEntity managed = entities.get(entityId);
                    if (managed != null && managed.behaviorActor != null) {
                        managed.behaviorActor.removeViewer(viewerId, NpcAttentionStack.ReleaseReason.UNTRACKED);
                    }
                    observed.remove(entityId);
                    lineOfSight.remove(entityId);
                }
            }
        }

        private boolean submit(
                Consumer<Player> action,
                Runnable retiredAction,
                ViewerWorkPriority priority
        ) {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(retiredAction, "retiredAction");
            Objects.requireNonNull(priority, "priority");
            if (retired.get() || lifecycle.closed()) {
                return false;
            }
            ViewerWork queued = new ViewerWork(action, retiredAction);
            work.add(queued, priority);
            if (retired.get() || lifecycle.closed()) {
                if (work.remove(queued)) {
                    return false;
                }
            }
            if (priority == ViewerWorkPriority.INTERACTIVE
                    && plugin.getServer().isOwnedByCurrentRegion(player)) {
                // Queue first, then drain, so work submitted before a region
                // handoff cannot be overtaken by this same-region fast path.
                work.drainInteractive(MAX_INTERACTIVE_WORK_PER_DRAIN, this::runWork);
            }
            return true;
        }

        private void drainWork() {
            work.drainPrioritized(
                    MAX_INTERACTIVE_WORK_PER_DRAIN,
                    MAX_BACKGROUND_WORK_PER_TICK,
                    this::runWork
            );
        }

        private void runWork(ViewerWork queued) {
            try {
                queued.action().accept(player);
            } catch (Throwable failure) {
                try {
                    queued.retired().run();
                } catch (Throwable cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                plugin.getSLF4JLogger().warn("Failed to compose NPC viewer work for {}",
                        player.getUniqueId(), failure);
            }
        }

        private void retireQueuedWork() {
            work.drainAll(queued -> {
                try {
                    queued.retired().run();
                } catch (Throwable failure) {
                    plugin.getSLF4JLogger().warn("Failed to retire NPC viewer work for {}",
                            player.getUniqueId(), failure);
                }
            });
        }

        private boolean shouldProbe(UUID actorId, UUID viewerId, NpcBehaviorProfile profile) {
            int interval = profile.attention().lineOfSightProbeIntervalTicks();
            long stagger = actorId.getLeastSignificantBits() ^ viewerId.getMostSignificantBits();
            return Math.floorMod(tick + stagger, interval) == 0L;
        }

        private NpcAttentionStack.Observation untrackedObservation(UUID viewerId) {
            return new NpcAttentionStack.Observation(
                    viewerId,
                    false,
                    false,
                    Double.MAX_VALUE,
                    Double.MAX_VALUE,
                    false,
                    new NpcAttentionStack.GazeTarget(0.0f, 0.0f)
            );
        }

        private void resetObservations() {
            observed.clear();
            lineOfSight.values().forEach(LosState::reset);
            lineOfSight.clear();
        }

        private CompletionStage<Void> closeAsync() {
            if (!retired.compareAndSet(false, true)) {
                return retirement;
            }
            if (task != null) {
                task.cancel();
            }
            boolean accepted = player.getScheduler().execute(
                    plugin,
                    this::finishRetirement,
                    this::finishRetirement,
                    1L
            );
            if (!accepted) {
                finishRetirement();
            }
            return retirement;
        }

        private void closeOwned() {
            requireOwned(player, "retire Paper viewer loop");
            if (!retired.compareAndSet(false, true)) {
                if (!retirement.isDone()) {
                    throw new IllegalStateException("Paper viewer loop is already retiring asynchronously");
                }
                retirement.join();
                return;
            }
            if (task != null) {
                task.cancel();
            }
            finishRetirement();
        }

        private void retiredByScheduler() {
            retired.set(true);
            finishRetirement();
        }

        private void finishRetirement() {
            UUID viewerId = player.getUniqueId();
            viewerLoops.remove(viewerId, this);
            retireQueuedWork();
            resetObservations();
            packetCodec.endConnection(viewerId);
            retirement.complete(null);
        }

        private record ViewerWork(Consumer<Player> action, Runnable retired) {
            private ViewerWork {
                Objects.requireNonNull(action, "action");
                Objects.requireNonNull(retired, "retired");
            }
        }
    }

    private static final class LosState {
        private long requestEpoch;
        private long resultEpoch;
        private boolean pending;
        private volatile boolean clear;

        private synchronized long beginProbe() {
            if (pending) {
                return -1L;
            }
            pending = true;
            return ++requestEpoch;
        }

        private synchronized void complete(long requestedEpoch, PaperLineOfSightSampler.Result result, Throwable failure) {
            if (requestedEpoch != requestEpoch) {
                return;
            }
            pending = false;
            clear = failure == null && result != null
                    && result.epoch() == requestedEpoch
                    && result.clear();
            resultEpoch = requestedEpoch;
        }

        private synchronized void assumeClear() {
            resultEpoch = ++requestEpoch;
            pending = false;
            clear = true;
        }

        private synchronized void failClosed() {
            resultEpoch = ++requestEpoch;
            pending = false;
            clear = false;
        }

        private synchronized void reset() {
            failClosed();
        }

        private boolean clear() {
            return clear;
        }

        private synchronized long resultEpoch() {
            return resultEpoch;
        }
    }

    private record PaperActorView(
            World world,
            UUID worldId,
            SpaceId spaceId,
            Vec3 position,
            NpcRenderFrame baseFrame,
            Set<UUID> trackingViewers,
            AnchorSnapshot anchor
    ) {
        private PaperActorView {
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(worldId, "worldId");
            Objects.requireNonNull(spaceId, "spaceId");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(baseFrame, "baseFrame");
            trackingViewers = Set.copyOf(Objects.requireNonNull(trackingViewers, "trackingViewers"));
            Objects.requireNonNull(anchor, "anchor");
        }
    }

    private record ServiceSpawn(HouseServiceEntity service, CompletionStage<Void> configuration) {
    }

    private record ShutdownSnapshot(
            List<PaperViewerLoop> viewers,
            List<PaperManagedEntity> entities
    ) {
        private ShutdownSnapshot {
            viewers = List.copyOf(viewers);
            entities = List.copyOf(entities);
        }
    }

    private final class PaperManagedEntity extends AbstractManagedEntity {
        private final Entity entity;
        private final Runnable onDespawn;
        private final List<AutoCloseable> retirementResources = new ArrayList<>();
        private final Set<UUID> indexedViewers = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean retired = new AtomicBoolean();
        private final PaperNpcBehaviorRenderPort behaviorPort;
        private final NpcBehaviorActor behaviorActor;
        private final PaperBehaviorCapability behaviorCapability;
        private volatile PaperActorView actorView;
        private volatile SkinTexture appliedSkin;

        private PaperManagedEntity(Entity entity, EntitySpec spec, Runnable onDespawn) {
            super(entity.getUniqueId(), spec);
            this.entity = entity;
            this.onDespawn = onDespawn;
            this.behaviorPort = supportsHumanoidBehavior(spec.type()) && entity instanceof Mannequin mannequin
                    ? new PaperNpcBehaviorRenderPort(
                            plugin,
                            mannequin,
                             packetEvents,
                             packetCodec,
                             PaperEntityPlatform.this::enqueueViewerWork,
                             overheadLayers,
                            PaperEntityPlatform.this::resolveAnchor
                    )
                    : null;
            this.behaviorActor = behaviorPort == null
                    ? null
                    : new NpcBehaviorActor(id(), spec.transform().yaw(), spec.transform().pitch(), behaviorPort);
            this.behaviorCapability = behaviorActor == null ? null : new PaperBehaviorCapability();
            registerCapabilities();
            applyInitialState();
            if (entity instanceof Mannequin mannequin) {
                mannequin.setDescription(Component.empty());
                mannequin.setImmovable(true);
                invulnerable(true);
                mannequin.setCollidable(false);
                mannequin.setPersistent(true);
                gravity(false);
                publishInitialAuthoredBase();
            }
            refreshActorView();
        }

        private void registerCapabilities() {
            if (behaviorCapability != null) {
                registerCapability(HumanoidBehaviorCapable.class, behaviorCapability);
            }

            registerCapability(PassengerCapable.class, new PassengerCapable() {
                @Override
                public List<UUID> passengers() {
                    PaperManagedEntity.this.requireMutable();
                    return entity.getPassengers().stream().map(Entity::getUniqueId).toList();
                }

                @Override
                public boolean addPassenger(ManagedEntity other) {
                    PaperManagedEntity.this.requireMutable();
                    if (other instanceof PaperManagedEntity paperManagedEntity) {
                        paperManagedEntity.requireMutable();
                        return entity.addPassenger(paperManagedEntity.entity);
                    }
                    return false;
                }

                @Override
                public boolean removePassenger(ManagedEntity other) {
                    PaperManagedEntity.this.requireMutable();
                    if (other instanceof PaperManagedEntity paperManagedEntity) {
                        paperManagedEntity.requireMutable();
                        return entity.removePassenger(paperManagedEntity.entity);
                    }
                    return false;
                }
            });

            registerCapability(PoseCapable.class, new PoseCapable() {
                @Override
                public sh.harold.library.entity.EntityPose pose() {
                    PaperManagedEntity.this.requireMutable();
                    return fromBukkitPose(entity.getPose());
                }

                @Override
                public void pose(sh.harold.library.entity.EntityPose pose) {
                    PaperManagedEntity.this.requireMutable();
                    entity.setPose(toBukkitPose(pose));
                    publishAuthoredPose(pose);
                }
            });

            if (entity instanceof Mannequin mannequin) {
                registerCapability(SkinCapable.class, new SkinCapable() {
                    @Override
                    public Optional<SkinTexture> skin() {
                        return Optional.ofNullable(appliedSkin);
                    }

                    @Override
                    public void skin(SkinTexture skinTexture) {
                        PaperManagedEntity.this.requireMutable();
                        Objects.requireNonNull(skinTexture, "skinTexture");
                        appliedSkin = skinTexture;
                        mannequin.setProfile(ResolvableProfile.resolvableProfile()
                                .uuid(id())
                                .addProperty(new ProfileProperty(
                                        "textures",
                                        skinTexture.texture(),
                                        skinTexture.signature()
                                ))
                                .build());
                    }

                    @Override
                    public void clearSkin() {
                        PaperManagedEntity.this.requireMutable();
                        appliedSkin = null;
                        mannequin.setProfile(Mannequin.defaultProfile());
                    }
                });
            }

            if (entity instanceof Mob mob) {
                registerCapability(AiCapable.class, new AiCapable() {
                    @Override
                    public boolean aiEnabled() {
                        PaperManagedEntity.this.requireMutable();
                        return mob.hasAI();
                    }

                    @Override
                    public void aiEnabled(boolean enabled) {
                        PaperManagedEntity.this.requireMutable();
                        mob.setAI(enabled);
                    }
                });
            }

            if (entity instanceof Ageable ageable) {
                registerCapability(AgeableCapable.class, new AgeableCapable() {
                    @Override
                    public boolean adult() {
                        PaperManagedEntity.this.requireMutable();
                        return ageable.isAdult();
                    }

                    @Override
                    public void adult(boolean adult) {
                        PaperManagedEntity.this.requireMutable();
                        if (adult) {
                            ageable.setAdult();
                        } else {
                            ageable.setBaby();
                        }
                    }
                });
            }

            if (entity instanceof LivingEntity livingEntity) {
                registerCapability(Equipable.class, new Equipable() {
                    @Override
                    public java.util.Optional<ItemDescriptor> equipment(EquipmentSlot slot) {
                        PaperManagedEntity.this.requireMutable();
                        ItemStack stack = livingEntity.getEquipment().getItem(toBukkitSlot(slot));
                        if (stack == null || stack.getType() == Material.AIR) {
                            return java.util.Optional.empty();
                        }
                        return java.util.Optional.of(new ItemDescriptor(stack.getType().key(), stack.getAmount()));
                    }

                    @Override
                    public void equipment(EquipmentSlot slot, ItemDescriptor item) {
                        PaperManagedEntity.this.requireMutable();
                        livingEntity.getEquipment().setItem(toBukkitSlot(slot), toItemStack(item));
                        publishAuthoredEquipment(slot, Optional.of(item));
                    }

                    @Override
                    public void clearEquipment(EquipmentSlot slot) {
                        PaperManagedEntity.this.requireMutable();
                        livingEntity.getEquipment().setItem(toBukkitSlot(slot), null);
                        publishAuthoredEquipment(slot, Optional.empty());
                    }
                });

            }

            registerCapability(PersistenceCapable.class, new PersistenceCapable() {
                @Override
                public boolean persistent() {
                    PaperManagedEntity.this.requireMutable();
                    return entity.isPersistent();
                }

                @Override
                public void persistent(boolean persistent) {
                    PaperManagedEntity.this.requireMutable();
                    entity.setPersistent(persistent);
                }
            });

            if (entity instanceof LivingEntity collidableEntity) {
                registerCapability(CollidableCapable.class, new CollidableCapable() {
                    @Override
                    public boolean collidable() {
                        PaperManagedEntity.this.requireMutable();
                        return collidableEntity.isCollidable();
                    }

                    @Override
                    public void collidable(boolean collidable) {
                        PaperManagedEntity.this.requireMutable();
                        collidableEntity.setCollidable(collidable);
                    }
                });
            }

            if (entity instanceof Leashable leashable) {
                registerCapability(LeashCapable.class, new LeashCapable() {
                    @Override
                    public java.util.Optional<UUID> leashHolder() {
                        PaperManagedEntity.this.requireMutable();
                        Entity leashHolder = leashable.getLeashHolder();
                        return leashHolder == null ? java.util.Optional.empty() : java.util.Optional.of(leashHolder.getUniqueId());
                    }

                    @Override
                    public boolean leashHolder(ManagedEntity other) {
                        PaperManagedEntity.this.requireMutable();
                        if (other instanceof PaperManagedEntity paperManagedEntity) {
                            paperManagedEntity.requireMutable();
                            return leashable.setLeashHolder(paperManagedEntity.entity);
                        }
                        return false;
                    }

                    @Override
                    public void clearLeash() {
                        PaperManagedEntity.this.requireMutable();
                        leashable.setLeashHolder(null);
                    }
                });
            }

            if (entity instanceof Villager villager) {
                registerCapability(VillagerLikeCapable.class, new VillagerLikeCapable() {
                    @Override
                    public int level() {
                        PaperManagedEntity.this.requireMutable();
                        return villager.getVillagerLevel();
                    }

                    @Override
                    public void level(int level) {
                        PaperManagedEntity.this.requireMutable();
                        villager.setVillagerLevel(level);
                    }

                    @Override
                    public java.util.Optional<net.kyori.adventure.key.Key> profession() {
                        PaperManagedEntity.this.requireMutable();
                        return java.util.Optional.of(villager.getProfession().key());
                    }

                    @Override
                    public void profession(net.kyori.adventure.key.Key profession) {
                        PaperManagedEntity.this.requireMutable();
                        villager.setProfession(Villager.Profession.valueOf(profession.value().toUpperCase(Locale.ROOT).replace('-', '_')));
                    }

                    @Override
                    public void clearProfession() {
                        PaperManagedEntity.this.requireMutable();
                        villager.setProfession(Villager.Profession.NONE);
                    }
                });
            } else if (entity instanceof AbstractVillager abstractVillager) {
                registerCapability(VillagerLikeCapable.class, new VillagerLikeCapable() {
                    @Override
                    public int level() {
                        return 0;
                    }

                    @Override
                    public void level(int level) {
                        throw new UnsupportedOperationException("This villager-like entity has no level");
                    }

                    @Override
                    public java.util.Optional<net.kyori.adventure.key.Key> profession() {
                        return java.util.Optional.empty();
                    }

                    @Override
                    public void profession(net.kyori.adventure.key.Key profession) {
                        throw new UnsupportedOperationException("This villager-like entity has no profession");
                    }

                    @Override
                    public void clearProfession() {
                    }
                });
            }

            if (entity instanceof Display display) {
                registerCapability(DisplayCapable.class, new DisplayCapable() {
                    @Override
                    public float width() {
                        PaperManagedEntity.this.requireMutable();
                        return display.getDisplayWidth();
                    }

                    @Override
                    public void width(float width) {
                        PaperManagedEntity.this.requireMutable();
                        display.setDisplayWidth(width);
                    }

                    @Override
                    public float height() {
                        PaperManagedEntity.this.requireMutable();
                        return display.getDisplayHeight();
                    }

                    @Override
                    public void height(float height) {
                        PaperManagedEntity.this.requireMutable();
                        display.setDisplayHeight(height);
                    }
                });
            }

            if (entity instanceof TextDisplay textDisplay) {
                registerCapability(TextDisplayCapable.class, new TextDisplayCapable() {
                    @Override
                    public Component text() {
                        PaperManagedEntity.this.requireMutable();
                        return textDisplay.text();
                    }

                    @Override
                    public void text(Component text) {
                        PaperManagedEntity.this.requireMutable();
                        textDisplay.text(text);
                    }

                    @Override
                    public float width() {
                        PaperManagedEntity.this.requireMutable();
                        return textDisplay.getDisplayWidth();
                    }

                    @Override
                    public void width(float width) {
                        PaperManagedEntity.this.requireMutable();
                        textDisplay.setDisplayWidth(width);
                    }

                    @Override
                    public float height() {
                        PaperManagedEntity.this.requireMutable();
                        return textDisplay.getDisplayHeight();
                    }

                    @Override
                    public void height(float height) {
                        PaperManagedEntity.this.requireMutable();
                        textDisplay.setDisplayHeight(height);
                    }
                });
            }

            if (entity instanceof ItemDisplay itemDisplay) {
                registerCapability(ItemDisplayCapable.class, new ItemDisplayCapable() {
                    @Override
                    public ItemDescriptor item() {
                        PaperManagedEntity.this.requireMutable();
                        ItemStack stack = itemDisplay.getItemStack();
                        return new ItemDescriptor(stack.getType().key(), stack.getAmount());
                    }

                    @Override
                    public void item(ItemDescriptor item) {
                        PaperManagedEntity.this.requireMutable();
                        itemDisplay.setItemStack(toItemStack(item));
                    }

                    @Override
                    public float width() {
                        PaperManagedEntity.this.requireMutable();
                        return itemDisplay.getDisplayWidth();
                    }

                    @Override
                    public void width(float width) {
                        PaperManagedEntity.this.requireMutable();
                        itemDisplay.setDisplayWidth(width);
                    }

                    @Override
                    public float height() {
                        PaperManagedEntity.this.requireMutable();
                        return itemDisplay.getDisplayHeight();
                    }

                    @Override
                    public void height(float height) {
                        PaperManagedEntity.this.requireMutable();
                        itemDisplay.setDisplayHeight(height);
                    }
                });
            }

            if (entity instanceof BlockDisplay blockDisplay) {
                registerCapability(BlockDisplayCapable.class, new BlockDisplayCapable() {
                    @Override
                    public BlockDescriptor block() {
                        PaperManagedEntity.this.requireMutable();
                        return new BlockDescriptor(blockDisplay.getBlock().getMaterial().key());
                    }

                    @Override
                    public void block(BlockDescriptor block) {
                        PaperManagedEntity.this.requireMutable();
                        blockDisplay.setBlock(toBlockData(block));
                    }

                    @Override
                    public float width() {
                        PaperManagedEntity.this.requireMutable();
                        return blockDisplay.getDisplayWidth();
                    }

                    @Override
                    public void width(float width) {
                        PaperManagedEntity.this.requireMutable();
                        blockDisplay.setDisplayWidth(width);
                    }

                    @Override
                    public float height() {
                        PaperManagedEntity.this.requireMutable();
                        return blockDisplay.getDisplayHeight();
                    }

                    @Override
                    public void height(float height) {
                        PaperManagedEntity.this.requireMutable();
                        blockDisplay.setDisplayHeight(height);
                    }
                });
            }
        }

        @Override
        protected void assertOwnerThread() {
            requireOwned(entity, "access Paper entity " + id());
        }

        @Override
        protected long interactionTick() {
            // handleInteraction already asserts the entity lane, so the
            // entity's own tick age is an exact Folia-safe debounce clock.
            return entity.getTicksLived();
        }

        @Override
        protected void doTeleport(EntityTransform transform) {
            World world = entity.getWorld();
            Location destination = toLocation(world, transform);
            requireOwned(destination, "teleport Paper entity " + id());
            if (!entity.teleport(destination)) {
                throw new IllegalStateException("Native Paper teleport rejected for entity " + id());
            }
            publishAuthoredLook(transform.yaw(), transform.pitch());
            refreshActorView();
        }

        @Override
        protected void doCustomName(Component customName) {
            entity.customName(customName);
        }

        @Override
        protected void doClearCustomName() {
            entity.customName(null);
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
            entity.setGravity(gravity);
        }

        @Override
        protected void doInvulnerable(boolean invulnerable) {
            entity.setInvulnerable(invulnerable);
        }

        @Override
        protected void doDespawn() {
            if (!retired.compareAndSet(false, true)) {
                return;
            }
            if (behaviorCapability != null) {
                behaviorCapability.closeOwned();
            }
            for (int index = retirementResources.size() - 1; index >= 0; index--) {
                try {
                    retirementResources.get(index).close();
                } catch (Exception failure) {
                    plugin.getSLF4JLogger().warn("Failed to close attachment for NPC {}", id(), failure);
                }
            }
            retirementResources.clear();
            if (entity.isValid()) {
                entity.remove();
            }
            clearTrackingIndex();
            onDespawn.run();
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

        private CompletionStage<Void> teleportAsync(EntityTransform transform) {
            if (!spawned() || retired.get()) {
                return CompletableFuture.failedFuture(new IllegalStateException("Entity " + id() + " is despawned"));
            }
            PaperActorView view = actorView;
            if (view == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("Entity has no live actor snapshot"));
            }
            Location destination = toLocation(view.world(), transform);
            CompletableFuture<Void> completion = new CompletableFuture<>();
            entity.teleportAsync(destination).whenComplete((success, failure) -> {
                if (failure != null) {
                    completion.completeExceptionally(failure);
                    return;
                }
                if (!Boolean.TRUE.equals(success)) {
                    completion.completeExceptionally(new IllegalStateException(
                            "Native Paper teleport rejected for entity " + id()
                    ));
                    return;
                }
                boolean accepted = entity.getScheduler().execute(plugin, () -> {
                    try {
                        publishTransform(transform);
                        publishAuthoredLook(transform.yaw(), transform.pitch());
                        refreshActorView();
                        completion.complete(null);
                    } catch (Throwable updateFailure) {
                        completion.completeExceptionally(updateFailure);
                    }
                }, () -> completion.completeExceptionally(new IllegalStateException(
                        "Entity " + id() + " retired during teleport"
                )), 1L);
                if (!accepted) {
                    completion.completeExceptionally(new IllegalStateException(
                            "Entity " + id() + " retired during teleport"
                    ));
                }
            });
            return completion;
        }

        private CompletionStage<Void> despawnAsync() {
            if (!spawned() || retired.get()) {
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<Void> completion = new CompletableFuture<>();
            boolean accepted = entity.getScheduler().execute(plugin, () -> {
                try {
                    despawn();
                    completion.complete(null);
                } catch (Throwable failure) {
                    completion.completeExceptionally(failure);
                }
            }, () -> {
                retirePluginData();
                completion.complete(null);
            }, 1L);
            if (!accepted) {
                retirePluginData();
                completion.complete(null);
            }
            return completion;
        }

        private void retireNative() {
            if (!retired.get() && spawned()) {
                despawn();
            }
        }

        private void retirePluginData() {
            if (retired.compareAndSet(false, true)) {
                clearInteractionDebounce();
                clearTrackingIndex();
                onDespawn.run();
            }
        }

        private void retireViewer(UUID viewerId, NpcAttentionStack.ReleaseReason reason) {
            if (!spawned() || retired.get()) {
                return;
            }
            entity.getScheduler().execute(plugin, () -> {
                if (!spawned() || retired.get()) {
                    return;
                }
                clearInteractionDebounce(viewerId);
                if (behaviorActor != null) {
                    behaviorActor.removeViewer(viewerId, reason);
                    behaviorPort.retireViewer(viewerId);
                }
            }, () -> {
                // Retired callbacks clear only library data; the viewer codec
                // is independently retired on the viewer lane.
            }, 1L);
        }

        private void reapplyFor(UUID viewerId) {
            if (behaviorPort != null) {
                behaviorPort.reapplyAfterTrack(viewerId);
            }
        }

        private void behaviorTick() {
            if (retired.get() || behaviorActor == null) {
                return;
            }
            refreshActorView();
            PaperActorView view = actorView;
            behaviorActor.updateActorView(
                    view.position(),
                    Optional.of(view.spaceId()),
                    view.trackingViewers().size()
            );
            behaviorActor.tick();
            behaviorPort.tick();
        }

        private void refreshActorView() {
            Location location = entity.getLocation();
            Vec3 position = new Vec3(location.getX(), location.getY(), location.getZ());
            NpcRenderFrame base = behaviorPort == null
                    ? captureBaseFrame()
                    : behaviorPort.baseFrame();
            Set<UUID> tracking = entity.getTrackedBy().stream()
                    .map(Player::getUniqueId)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            updateTrackingIndex(tracking);
            SpaceId space = spaceId(location.getWorld());
            actorView = new PaperActorView(
                    location.getWorld(),
                    location.getWorld().getUID(),
                    space,
                    position,
                    base,
                    tracking,
                    new AnchorSnapshot(space, frame(position, base.bodyYaw(), base.pitch()))
            );
        }

        private void updateTrackingIndex(Set<UUID> tracking) {
            if (behaviorActor == null) {
                return;
            }
            for (UUID removed : Set.copyOf(indexedViewers)) {
                if (!tracking.contains(removed)) {
                    Set<UUID> ids = trackedNpcIndex.get(removed);
                    if (ids != null) {
                        ids.remove(id());
                        if (ids.isEmpty()) {
                            trackedNpcIndex.remove(removed, ids);
                        }
                    }
                    indexedViewers.remove(removed);
                }
            }
            for (UUID added : tracking) {
                if (indexedViewers.add(added)) {
                    trackedNpcIndex.computeIfAbsent(added, ignored -> ConcurrentHashMap.newKeySet()).add(id());
                }
            }
        }

        private void clearTrackingIndex() {
            for (UUID viewerId : Set.copyOf(indexedViewers)) {
                Set<UUID> ids = trackedNpcIndex.get(viewerId);
                if (ids != null) {
                    ids.remove(id());
                    if (ids.isEmpty()) {
                        trackedNpcIndex.remove(viewerId, ids);
                    }
                }
            }
            indexedViewers.clear();
        }

        private void publishInitialAuthoredBase() {
            if (behaviorActor == null) {
                return;
            }
            publishAuthoredFrame(captureBaseFrame());
        }

        private void publishAuthoredLook(float yaw, float pitch) {
            if (behaviorActor == null) {
                return;
            }
            publishAuthoredFrame(withAuthoredLook(behaviorPort.baseFrame(), yaw, pitch));
        }

        private void publishAuthoredPose(sh.harold.library.entity.EntityPose pose) {
            if (behaviorActor == null) {
                return;
            }
            publishAuthoredFrame(withAuthoredPose(behaviorPort.baseFrame(), pose));
        }

        private void publishAuthoredEquipment(EquipmentSlot slot, Optional<ItemDescriptor> item) {
            if (behaviorActor == null) {
                return;
            }
            publishAuthoredFrame(withAuthoredEquipment(behaviorPort.baseFrame(), slot, item));
        }

        private void publishAuthoredFrame(NpcRenderFrame base) {
            behaviorActor.updateBaseFrame(base);
            behaviorPort.updateBaseFrame(base);
        }

        private NpcRenderFrame captureBaseFrame() {
            float bodyYaw = entity instanceof LivingEntity living ? living.getBodyYaw() : entity.getYaw();
            Map<EquipmentSlot, ItemDescriptor> equipment = new EnumMap<>(EquipmentSlot.class);
            boolean usingMain = false;
            boolean usingOff = false;
            if (entity instanceof LivingEntity living) {
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    ItemStack stack = living.getEquipment().getItem(toBukkitSlot(slot));
                    if (stack != null && stack.getType() != Material.AIR) {
                        equipment.put(slot, new ItemDescriptor(stack.getType().key(), stack.getAmount()));
                    }
                }
                if (living.hasActiveItem()) {
                    usingOff = living.getActiveItemHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND;
                    usingMain = !usingOff;
                }
            }
            return new NpcRenderFrame(
                    bodyYaw,
                    entity.getYaw(),
                    entity.getPitch(),
                    fromBukkitPose(entity.getPose()),
                    equipment,
                    usingMain,
                    usingOff
            );
        }

        private final class PaperBehaviorCapability implements HumanoidBehaviorCapable, NpcConversationParticipant {
            private final AtomicReference<ScheduledTask> actorTask = new AtomicReference<>();
            private final AtomicLong generation = new AtomicLong();

            @Override
            public Optional<NpcBehaviorProfile> profile() {
                return behaviorActor.profile();
            }

            @Override
            public CompletionStage<Void> configure(NpcBehaviorProfile profile) {
                Objects.requireNonNull(profile, "profile");
                long token = generation.incrementAndGet();
                ensureActorTask();
                return behaviorActor.configure(profile).whenComplete((ignored, failure) -> {
                    if (failure != null && generation.get() == token && !behaviorActor.configured()) {
                        clearTrackingIndex();
                        cancelActorTask();
                    }
                });
            }

            @Override
            public CompletionStage<Void> disable() {
                long token = generation.incrementAndGet();
                ensureActorTask();
                return behaviorActor.disable().whenComplete((ignored, failure) -> {
                    if (failure == null && generation.get() == token) {
                        clearTrackingIndex();
                        cancelActorTask();
                    }
                });
            }

            @Override
            public NpcPlayback speak(Component text) {
                return behaviorActor.speak(text);
            }

            @Override
            public NpcPlayback speakNow(Component text) {
                return behaviorActor.speakNow(text);
            }

            @Override
            public void clearSpeech() {
                behaviorActor.clearSpeech();
            }

            @Override
            public NpcPlayback perform(NpcRoutine routine) {
                return behaviorActor.perform(routine);
            }

            @Override
            public NpcAttentionLease attendTo(UUID viewerId) {
                return behaviorActor.attendTo(viewerId);
            }

            @Override
            public NpcBehaviorSnapshot snapshot() {
                return behaviorActor.snapshot();
            }

            @Override
            public UUID actorId() {
                return behaviorActor.actorId();
            }

            @Override
            public boolean configured() {
                return behaviorActor.configured();
            }

            @Override
            public boolean atCleanupCheckpoint() {
                return behaviorActor.atCleanupCheckpoint();
            }

            @Override
            public int trackingViewerCount() {
                return behaviorActor.trackingViewerCount();
            }

            @Override
            public Optional<SpaceId> spaceId() {
                return behaviorActor.spaceId();
            }

            @Override
            public Vec3 position() {
                return behaviorActor.position();
            }

            @Override
            public boolean tryReserveConversation(UUID registrationId) {
                return behaviorActor.tryReserveConversation(registrationId);
            }

            @Override
            public boolean conversationReservedBy(UUID registrationId) {
                return behaviorActor.conversationReservedBy(registrationId);
            }

            @Override
            public void conversationInterruption(boolean active) {
                behaviorActor.conversationInterruption(active);
            }

            @Override
            public void releaseConversation(UUID registrationId) {
                behaviorActor.releaseConversation(registrationId);
            }

            @Override
            public NpcPlayback speakConversation(Component line, boolean interruption) {
                return behaviorActor.speakConversation(line, interruption);
            }

            @Override
            public void clearConversationSpeech() {
                behaviorActor.clearConversationSpeech();
            }

            @Override
            public List<Component> interruptionLines(List<Component> generic) {
                return behaviorActor.interruptionLines(generic);
            }

            @Override
            public void finishDeferredInteraction(UUID viewerId) {
                behaviorActor.finishDeferredInteraction(viewerId);
            }

            @Override
            public AutoCloseable beginInterruptionBarrier() {
                return behaviorActor.beginInterruptionBarrier();
            }

            @Override
            public void stageConversation(NpcConversationStagingMode mode, Vec3 focus, boolean selectedToReact) {
                behaviorActor.stageConversation(mode, focus, selectedToReact);
            }

            @Override
            public void clearConversationStage() {
                behaviorActor.clearConversationStage();
            }

            @Override
            public void reactToInterruption() {
                behaviorActor.reactToInterruption();
            }

            @Override
            public void interactionRouter(NpcBehaviorActor.InteractionRouter router) {
                behaviorActor.interactionRouter(router);
            }

            private void ensureActorTask() {
                if (actorTask.get() != null) {
                    return;
                }
                synchronized (actorTask) {
                    if (actorTask.get() != null) {
                        return;
                    }
                    ScheduledTask scheduled = entity.getScheduler().runAtFixedRate(
                            plugin,
                            ignored -> behaviorTick(),
                            () -> actorTask.set(null),
                            1L,
                            1L
                    );
                    if (scheduled == null) {
                        throw new IllegalStateException("Cannot schedule behavior for retired entity " + id());
                    }
                    actorTask.set(scheduled);
                }
            }

            private void cancelActorTask() {
                ScheduledTask scheduled = actorTask.getAndSet(null);
                if (scheduled != null) {
                    scheduled.cancel();
                }
            }

            private void closeOwned() {
                generation.incrementAndGet();
                cancelActorTask();
                behaviorActor.close();
                behaviorPort.close();
            }
        }
    }

    private static final class PaperHouseRenderer implements HousePresentationRenderer {
        private static final double LINE_SPACING = 0.3;
        private static final float SPACER_WIDTH = 0.01f;

        private final List<Entity> attachments = new ArrayList<>();
        private final PaperOverheadLayerManager.Reservation reservation;
        private boolean closed;

        private PaperHouseRenderer(
                Entity anchor,
                HousePresentation presentation,
                PaperOverheadLayerManager.Reservation reservation
        ) {
            this.reservation = Objects.requireNonNull(reservation, "reservation");
            List<Component> lines = presentation.lines();
            Entity vehicle = anchor;
            for (int index = lines.size() - 1; index >= 0; index--) {
                ArmorStand display = spawnLine(anchor.getWorld(), anchor.getLocation(), lines.get(index));
                if (!vehicle.addPassenger(display)) {
                    display.remove();
                    close();
                    throw new IllegalStateException("Failed to mount House line armor stand to " + vehicle.getUniqueId());
                }
                attachments.add(display);
                vehicle = display;
                if (index > 0) {
                    Interaction spacer = spawnSpacer(anchor.getWorld(), anchor.getLocation());
                    if (!vehicle.addPassenger(spacer)) {
                        spacer.remove();
                        close();
                        throw new IllegalStateException("Failed to mount House line spacer to " + vehicle.getUniqueId());
                    }
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
            if (closed) {
                return;
            }
            closed = true;
            for (int index = attachments.size() - 1; index >= 0; index--) {
                attachments.get(index).remove();
            }
            attachments.clear();
            reservation.close();
        }

        private static ArmorStand spawnLine(World world, Location anchorLocation, Component text) {
            return world.spawn(
                    anchorLocation.clone(),
                    ArmorStand.class,
                    display -> {
                        display.customName(text);
                        display.setCustomNameVisible(true);
                        display.setVisible(false);
                        display.setMarker(true);
                        display.setSmall(true);
                        display.setBasePlate(false);
                        display.setGravity(false);
                        display.setInvulnerable(true);
                        display.setSilent(true);
                        display.setCollidable(false);
                        display.setPersistent(false);
                    }
            );
        }

        private static Interaction spawnSpacer(World world, Location anchorLocation) {
            return world.spawn(
                    anchorLocation.clone(),
                    Interaction.class,
                    spacer -> {
                        spacer.setInteractionWidth(SPACER_WIDTH);
                        spacer.setInteractionHeight((float) LINE_SPACING);
                        spacer.setResponsive(false);
                        spacer.setGravity(false);
                        spacer.setInvulnerable(true);
                        spacer.setSilent(true);
                        spacer.setPersistent(false);
                        spacer.customName(null);
                        spacer.setCustomNameVisible(false);
                    }
            );
        }
    }
}
