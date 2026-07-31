package sh.harold.library.entity.paper;

import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import sh.harold.library.entity.ItemDescriptor;
import sh.harold.library.npc.behavior.core.NpcBehaviorRenderPort;
import sh.harold.library.npc.behavior.core.NpcBubbleFrame;
import sh.harold.library.npc.behavior.core.NpcRenderAnimation;
import sh.harold.library.npc.behavior.core.NpcRenderFrame;
import sh.harold.library.npc.behavior.core.NpcRenderedSound;
import sh.harold.library.spatial.AnchorRef;
import sh.harold.library.spatial.AnchorSnapshot;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Folia-safe composition boundary for a native Paper mannequin.
 *
 * <p>Calls that touch the mannequin or the real text display run on the
 * mannequin's actor lane. Viewer-only packets and sounds are always submitted
 * to that player's entity scheduler. Cross-lane handoff consists exclusively
 * of UUIDs and immutable render snapshots.</p>
 */
final class PaperNpcBehaviorRenderPort implements NpcBehaviorRenderPort, AutoCloseable {

    private static final int APPROXIMATE_FORTY_CHARACTER_LINE_WIDTH = 240;
    private static final Set<PaperMannequinPacketCodec.Channel> ATTENTION_CHANNELS = Set.of(
            PaperMannequinPacketCodec.Channel.HEAD,
            PaperMannequinPacketCodec.Channel.BODY,
            PaperMannequinPacketCodec.Channel.POSE
    );

    private final Plugin plugin;
    private final Mannequin mannequin;
    private final PacketEventsAPI<?> packetEvents;
    private final PaperMannequinPacketCodec codec;
    private final ViewerDispatcher viewerDispatcher;
    private final PaperOverheadLayerManager overheadLayers;
    private final Function<AnchorRef, Optional<AnchorSnapshot>> anchorResolver;
    private final UUID mannequinUuid;
    private final int mannequinEntityId;
    private final Map<UUID, NpcRenderFrame> overlays = new ConcurrentHashMap<>();
    private final Map<UUID, NpcRenderFrame> deliveredOverlays = new ConcurrentHashMap<>();
    private final Set<UUID> pendingOverlayDispatches = ConcurrentHashMap.newKeySet();
    private final Map<UUID, VirtualBubble> virtualBubbles = new ConcurrentHashMap<>();

    private volatile NpcRenderFrame baseFrame;
    private volatile NpcRenderFrame nativeFrame;
    private volatile ActorSnapshot actorSnapshot;
    private SharedBubble sharedBubble;
    private volatile boolean closing;
    private volatile boolean closed;

    PaperNpcBehaviorRenderPort(
            Plugin plugin,
            Mannequin mannequin,
            PacketEventsAPI<?> packetEvents,
            PaperMannequinPacketCodec codec,
            ViewerDispatcher viewerDispatcher,
            PaperOverheadLayerManager overheadLayers,
            Function<AnchorRef, Optional<AnchorSnapshot>> anchorResolver
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.mannequin = Objects.requireNonNull(mannequin, "mannequin");
        this.packetEvents = Objects.requireNonNull(packetEvents, "packetEvents");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.viewerDispatcher = Objects.requireNonNull(viewerDispatcher, "viewerDispatcher");
        this.overheadLayers = Objects.requireNonNull(overheadLayers, "overheadLayers");
        this.anchorResolver = Objects.requireNonNull(anchorResolver, "anchorResolver");
        requireActorLane();
        mannequinUuid = mannequin.getUniqueId();
        mannequinEntityId = mannequin.getEntityId();
        baseFrame = new NpcRenderFrame(
                mannequin.getBodyYaw(),
                mannequin.getYaw(),
                mannequin.getPitch(),
                fromBukkitPose(mannequin.getPose()),
                Map.of(),
                mannequin.hasActiveItem() && mannequin.getActiveItemHand() == org.bukkit.inventory.EquipmentSlot.HAND,
                mannequin.hasActiveItem() && mannequin.getActiveItemHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND
        );
        nativeFrame = baseFrame;
        updateActorSnapshot();
    }

    NpcRenderFrame baseFrame() {
        return baseFrame;
    }

    NpcRenderFrame nativeFrame() {
        return nativeFrame;
    }

    void updateBaseFrame(NpcRenderFrame frame) {
        baseFrame = Objects.requireNonNull(frame, "frame");
    }

    boolean hasOverlay(UUID viewerId) {
        return overlays.containsKey(viewerId);
    }

    @Override
    public CompletionStage<Void> restoreNativePresentation() {
        if (closed) {
            return CompletableFuture.completedFuture(null);
        }
        if (plugin.getServer().isOwnedByCurrentRegion(mannequin)) {
            restoreOnActorLane();
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> completion = new CompletableFuture<>();
        boolean scheduled = mannequin.getScheduler().execute(
                plugin,
                () -> {
                    try {
                        restoreOnActorLane();
                        completion.complete(null);
                    } catch (Throwable failure) {
                        completion.completeExceptionally(failure);
                    }
                },
                () -> completion.complete(null),
                1L
        );
        if (!scheduled) {
            completion.complete(null);
        }
        return completion;
    }

    @Override
    public Optional<AnchorSnapshot> resolveAnchor(AnchorRef anchor) {
        return Objects.requireNonNull(anchorResolver.apply(Objects.requireNonNull(anchor, "anchor")),
                "anchorResolver result");
    }

    @Override
    public void renderSharedFrame(NpcRenderFrame frame) {
        requireOpenActorLane();
        applyNativeFrame(Objects.requireNonNull(frame, "frame"));
    }

    @Override
    public void renderViewerOverlay(UUID viewerId, NpcRenderFrame frame) {
        requireOpen();
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(frame, "frame");
        overlays.put(viewerId, frame);
        scheduleLatestOverlay(viewerId);
    }

    @Override
    public void clearViewerOverlay(UUID viewerId, NpcRenderFrame currentNativeFrame) {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(currentNativeFrame, "currentNativeFrame");
        NpcRenderFrame removed = overlays.remove(viewerId);
        clearVirtualBubble(viewerId, -1L);
        if (removed == null || closed) {
            return;
        }
        scheduleViewer(viewerId, true, user -> {
            if (!overlays.containsKey(viewerId)) {
                deliveredOverlays.remove(viewerId);
                codec.clearComposition(viewerId, mannequinEntityId);
                codec.sendCompleteFrame(user, mannequinEntityId, currentNativeFrame);
            }
        }, () -> {
            deliveredOverlays.remove(viewerId);
            codec.clearComposition(viewerId, mannequinEntityId);
        }, false);
    }

    @Override
    public void showSharedBubble(NpcBubbleFrame bubble) {
        requireOpenActorLane();
        Objects.requireNonNull(bubble, "bubble");
        if (sharedBubble != null && sharedBubble.frame().id() == bubble.id()) {
            updateSharedBubble(sharedBubble, bubble);
            return;
        }
        clearSharedBubbleInternal();
        spawnSharedBubble(bubble);
    }

    @Override
    public void clearSharedBubble(long bubbleId) {
        requireActorLane();
        if (sharedBubble != null && (bubbleId < 0L || sharedBubble.frame().id() == bubbleId)) {
            clearSharedBubbleInternal();
        }
    }

    @Override
    public void showVirtualBubble(UUID viewerId, NpcBubbleFrame bubble) {
        requireOpen();
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(bubble, "bubble");
        clearVirtualBubble(viewerId, -1L);
        VirtualBubble pending = new VirtualBubble(bubble.id(), 0);
        virtualBubbles.put(viewerId, pending);
        ActorSnapshot snapshot = actorSnapshot;
        Vector3d position = bubblePosition(snapshot);
        scheduleViewer(viewerId, true, user -> {
            if (virtualBubbles.get(viewerId) != pending) {
                return;
            }
            int entityId = codec.showVirtualBubble(user, bubble.text(), position);
            VirtualBubble active = new VirtualBubble(bubble.id(), entityId);
            if (!virtualBubbles.replace(viewerId, pending, active)) {
                codec.destroyVirtualBubble(user, entityId);
            }
        }, () -> virtualBubbles.remove(viewerId, pending), false, ViewerWorkPriority.INTERACTIVE);
    }

    @Override
    public void clearVirtualBubble(UUID viewerId, long bubbleId) {
        Objects.requireNonNull(viewerId, "viewerId");
        VirtualBubble bubble = virtualBubbles.get(viewerId);
        if (bubble == null || (bubbleId >= 0L && bubble.bubbleId() != bubbleId)) {
            return;
        }
        if (!virtualBubbles.remove(viewerId, bubble) || bubble.entityId() == 0) {
            return;
        }
        scheduleViewer(viewerId, false, user -> codec.destroyVirtualBubble(user, bubble.entityId()),
                () -> { }, true, ViewerWorkPriority.INTERACTIVE);
    }

    @Override
    public void animateShared(NpcRenderAnimation animation) {
        requireOpenActorLane();
        Objects.requireNonNull(animation, "animation");
        switch (animation.type()) {
            case SWING_OFF_HAND, USE_OFF_HAND -> mannequin.swingOffHand();
            case SWING_MAIN_HAND, USE_MAIN_HAND, WAVE -> mannequin.swingMainHand();
            default -> {
                // Pose and gaze gestures are represented by renderSharedFrame.
            }
        }
    }

    @Override
    public void animateAttention(NpcRenderAnimation animation, Set<UUID> excludedViewers) {
        requireOpenActorLane();
        Objects.requireNonNull(animation, "animation");
        Set<UUID> excluded = Set.copyOf(Objects.requireNonNull(excludedViewers, "excludedViewers"));
        updateActorSnapshot();
        for (UUID viewerId : actorSnapshot.trackedViewers()) {
            if (!excluded.contains(viewerId)) {
                scheduleViewer(viewerId, true, user -> codec.sendAnimation(user, mannequinEntityId, animation),
                        () -> codec.clearComposition(viewerId, mannequinEntityId), false);
            }
        }
    }

    @Override
    public void animateViewer(UUID viewerId, NpcRenderAnimation animation) {
        requireOpen();
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(animation, "animation");
        scheduleViewer(viewerId, true, user -> codec.sendAnimation(user, mannequinEntityId, animation),
                () -> codec.clearComposition(viewerId, mannequinEntityId), false);
    }

    @Override
    public void playSound(NpcRenderedSound renderedSound) {
        requireOpenActorLane();
        Objects.requireNonNull(renderedSound, "renderedSound");
        updateActorSnapshot();
        ActorSnapshot snapshot = actorSnapshot;
        Set<UUID> recipients = renderedSound.recipient()
                .map(Set::of)
                .orElse(snapshot.trackedViewers());
        for (UUID viewerId : recipients) {
            scheduleSound(viewerId, renderedSound, snapshot);
        }
    }

    /** Refreshes immutable cross-lane state and keeps the real display anchored. */
    void tick() {
        requireOpenActorLane();
        updateActorSnapshot();
        synchronizeSharedBubble();
    }

    /** Reapplies the complete composed frame after the server's native spawn frame. */
    void reapplyAfterTrack(UUID viewerId) {
        requireOpen();
        Objects.requireNonNull(viewerId, "viewerId");
        scheduleViewer(viewerId, false, user -> {
            NpcRenderFrame latest = overlays.get(viewerId);
            if (latest != null) {
                codec.compose(viewerId, mannequinEntityId, latest, ATTENTION_CHANNELS);
                deliveredOverlays.put(viewerId, latest);
            } else {
                codec.clearComposition(viewerId, mannequinEntityId);
                deliveredOverlays.remove(viewerId);
            }
            codec.sendCompleteFrame(user, mannequinEntityId, latest == null ? nativeFrame : latest);
        }, () -> {
            deliveredOverlays.remove(viewerId);
            codec.clearComposition(viewerId, mannequinEntityId);
        }, true);
    }

    /** Retires all client-only state owned by one viewer. */
    void retireViewer(UUID viewerId) {
        Objects.requireNonNull(viewerId, "viewerId");
        overlays.remove(viewerId);
        deliveredOverlays.remove(viewerId);
        codec.clearComposition(viewerId, mannequinEntityId);
        clearVirtualBubble(viewerId, -1L);
        SharedBubble bubble = sharedBubble;
        if (bubble != null) {
            codec.revealEntity(viewerId, bubble.entityId());
        }
    }

    @Override
    public void close() {
        if (closing || closed) {
            return;
        }
        closing = true;
        if (plugin.getServer().isOwnedByCurrentRegion(mannequin)) {
            closeOnActorLane();
            return;
        }
        boolean scheduled = mannequin.getScheduler().execute(
                plugin,
                this::closeOnActorLane,
                this::retirePluginState,
                1L
        );
        if (!scheduled) {
            retirePluginState();
        }
    }

    private void restoreOnActorLane() {
        if (closed) {
            return;
        }
        requireActorLane();
        clearSharedBubbleInternal();
        clearAllVirtualBubbles();
        NpcRenderFrame restored = baseFrame;
        applyNativeFrame(restored);
        Set<UUID> affectedViewers = new LinkedHashSet<>(overlays.keySet());
        affectedViewers.addAll(deliveredOverlays.keySet());
        for (UUID viewerId : affectedViewers) {
            overlays.remove(viewerId);
            scheduleViewer(viewerId, false, user -> {
                deliveredOverlays.remove(viewerId);
                codec.clearComposition(viewerId, mannequinEntityId);
                codec.sendCompleteFrame(user, mannequinEntityId, restored);
            }, () -> {
                deliveredOverlays.remove(viewerId);
                codec.clearComposition(viewerId, mannequinEntityId);
            }, true);
        }
    }

    private void applyNativeFrame(NpcRenderFrame frame) {
        nativeFrame = frame;
        mannequin.setRotation(frame.headYaw(), frame.pitch());
        mannequin.setBodyYaw(frame.bodyYaw());
        mannequin.setPose(toBukkitPose(frame.pose()), true);
        for (sh.harold.library.entity.EquipmentSlot slot : sh.harold.library.entity.EquipmentSlot.values()) {
            mannequin.getEquipment().setItem(toBukkitSlot(slot), toItemStack(frame.equipment().get(slot)), true);
        }

        org.bukkit.inventory.EquipmentSlot activeHand = frame.usingOffHand()
                ? org.bukkit.inventory.EquipmentSlot.OFF_HAND
                : frame.usingMainHand() ? org.bukkit.inventory.EquipmentSlot.HAND : null;
        if (activeHand == null) {
            if (mannequin.hasActiveItem()) {
                mannequin.clearActiveItem();
            }
        } else if (!mannequin.hasActiveItem() || mannequin.getActiveItemHand() != activeHand) {
            if (mannequin.hasActiveItem()) {
                mannequin.clearActiveItem();
            }
            mannequin.startUsingItem(activeHand);
        }
    }

    private void spawnSharedBubble(NpcBubbleFrame bubble) {
        Location position = bubbleLocation();
        Set<UUID> excluded = Set.copyOf(bubble.excludedViewers());
        TextDisplay display = position.getWorld().spawn(
                position,
                TextDisplay.class,
                candidate -> {
                    configureTextDisplay(candidate, bubble.text());
                    for (UUID viewerId : excluded) {
                        codec.hideEntity(viewerId, candidate.getEntityId());
                    }
                },
                CreatureSpawnEvent.SpawnReason.CUSTOM
        );
        sharedBubble = new SharedBubble(
                display,
                display.getEntityId(),
                display.getUniqueId(),
                position.getWorld().getUID(),
                position.getBlockX() >> 4,
                position.getBlockZ() >> 4,
                bubble
        );
    }

    private void updateSharedBubble(SharedBubble current, NpcBubbleFrame updated) {
        if (!current.frame().text().equals(updated.text())) {
            current.display().text(updated.text());
        }
        Set<UUID> newlyExcluded = new LinkedHashSet<>(updated.excludedViewers());
        newlyExcluded.removeAll(current.frame().excludedViewers());
        for (UUID viewerId : newlyExcluded) {
            codec.hideEntity(viewerId, current.entityId());
            scheduleViewer(viewerId, false, user -> codec.destroyVirtualBubble(user, current.entityId()),
                    () -> { }, true, ViewerWorkPriority.INTERACTIVE);
        }

        Set<UUID> newlyRevealed = new LinkedHashSet<>(current.frame().excludedViewers());
        newlyRevealed.removeAll(updated.excludedViewers());
        ActorSnapshot snapshot = actorSnapshot;
        Vector3d position = bubblePosition(snapshot);
        for (UUID viewerId : newlyRevealed) {
            codec.revealEntity(viewerId, current.entityId());
            scheduleViewer(viewerId, true, user -> codec.sendTextDisplay(
                    user,
                    current.entityId(),
                    current.entityUuid(),
                    updated.text(),
                    position
            ), () -> { }, false, ViewerWorkPriority.INTERACTIVE);
        }
        sharedBubble = current.withFrame(updated);
    }

    private void synchronizeSharedBubble() {
        SharedBubble bubble = sharedBubble;
        if (bubble == null) {
            return;
        }
        Location wanted = bubbleLocation();
        int wantedChunkX = wanted.getBlockX() >> 4;
        int wantedChunkZ = wanted.getBlockZ() >> 4;
        if (!bubble.worldId().equals(wanted.getWorld().getUID())
                || bubble.chunkX() != wantedChunkX
                || bubble.chunkZ() != wantedChunkZ) {
            NpcBubbleFrame frame = bubble.frame();
            sharedBubble = null;
            removeSharedDisplay(bubble);
            spawnSharedBubble(frame);
            return;
        }
        Location current = bubble.display().getLocation();
        if (current.distanceSquared(wanted) > 1.0e-6) {
            bubble.display().teleport(wanted);
        }
    }

    private void clearSharedBubbleInternal() {
        SharedBubble bubble = sharedBubble;
        sharedBubble = null;
        if (bubble != null) {
            removeSharedDisplay(bubble);
        }
    }

    private void removeSharedDisplay(SharedBubble bubble) {
        codec.removeEntity(bubble.entityId());
        if (plugin.getServer().isOwnedByCurrentRegion(bubble.display())) {
            bubble.display().remove();
            return;
        }
        bubble.display().getScheduler().execute(plugin, bubble.display()::remove, () -> { }, 1L);
    }

    private void clearAllVirtualBubbles() {
        for (UUID viewerId : Set.copyOf(virtualBubbles.keySet())) {
            clearVirtualBubble(viewerId, -1L);
        }
    }

    private void scheduleSound(UUID viewerId, NpcRenderedSound sound, ActorSnapshot origin) {
        viewerDispatcher.enqueue(viewerId, player -> {
            if (!player.isOnline()
                    || !origin.trackedViewers().contains(viewerId)
                    || !player.getWorld().getUID().equals(origin.worldId())) {
                return;
            }
            World world = player.getWorld();
            player.playSound(
                    new Location(world, origin.x(), origin.y(), origin.z()),
                    sound.key().asString(),
                    soundCategory(sound.source()),
                    sound.volume(),
                    sound.pitch()
            );
        }, () -> { }, ViewerWorkPriority.BACKGROUND);
    }

    /** Keeps at most one queued overlay delivery per viewer for this NPC. */
    private void scheduleLatestOverlay(UUID viewerId) {
        if (!pendingOverlayDispatches.add(viewerId)) {
            return;
        }
        scheduleViewer(viewerId, true, user -> {
            NpcRenderFrame latest = overlays.get(viewerId);
            if (latest != null) {
                NpcRenderFrame previous = deliveredOverlays.get(viewerId);
                NpcRenderFrame comparison = previous == null ? nativeFrame : previous;
                codec.compose(viewerId, mannequinEntityId, latest, ATTENTION_CHANNELS);
                codec.sendFrameDelta(user, mannequinEntityId, comparison, latest, ATTENTION_CHANNELS);
                deliveredOverlays.put(viewerId, latest);
            }
            finishOverlayDispatch(viewerId, true);
        }, () -> {
            deliveredOverlays.remove(viewerId);
            codec.clearComposition(viewerId, mannequinEntityId);
            finishOverlayDispatch(viewerId, false);
        }, false);
    }

    private void finishOverlayDispatch(UUID viewerId, boolean rescheduleLatest) {
        pendingOverlayDispatches.remove(viewerId);
        if (!rescheduleLatest) {
            return;
        }
        NpcRenderFrame latest = overlays.get(viewerId);
        if (latest != null && !latest.equals(deliveredOverlays.get(viewerId))) {
            scheduleLatestOverlay(viewerId);
        }
    }

    private void scheduleViewer(
            UUID viewerId,
            boolean requireTracked,
            Consumer<User> action,
            Runnable retired,
            boolean allowUntracked
    ) {
        scheduleViewer(
                viewerId,
                requireTracked,
                action,
                retired,
                allowUntracked,
                ViewerWorkPriority.BACKGROUND
        );
    }

    private void scheduleViewer(
            UUID viewerId,
            boolean requireTracked,
            Consumer<User> action,
            Runnable retired,
            boolean allowUntracked,
            ViewerWorkPriority priority
    ) {
        boolean queued = viewerDispatcher.enqueue(viewerId, player -> {
            ActorSnapshot snapshot = actorSnapshot;
            if (!player.isOnline()
                    || !player.getWorld().getUID().equals(snapshot.worldId())
                    || (requireTracked && !snapshot.trackedViewers().contains(viewerId) && !allowUntracked)) {
                retired.run();
                return;
            }
            User user = packetEvents.getPlayerManager().getUser(player);
            if (user == null) {
                retired.run();
                return;
            }
            action.accept(user);
        }, retired, priority);
        if (!queued) {
            retired.run();
        }
    }

    private void updateActorSnapshot() {
        requireActorLane();
        Location location = mannequin.getLocation();
        Set<UUID> tracked = new LinkedHashSet<>();
        for (Player player : mannequin.getTrackedPlayers()) {
            tracked.add(player.getUniqueId());
        }
        actorSnapshot = new ActorSnapshot(
                location.getWorld().getUID(),
                location.getX(),
                location.getY(),
                location.getZ(),
                Set.copyOf(tracked)
        );
    }

    private Location bubbleLocation() {
        Location location = mannequin.getLocation();
        return location.add(0.0D, overheadLayers.bubbleOffset(mannequinUuid), 0.0D);
    }

    private Vector3d bubblePosition(ActorSnapshot snapshot) {
        return new Vector3d(
                snapshot.x(),
                snapshot.y() + overheadLayers.bubbleOffset(mannequinUuid),
                snapshot.z()
        );
    }

    private void closeOnActorLane() {
        if (closed) {
            return;
        }
        requireActorLane();
        clearSharedBubbleInternal();
        clearAllVirtualBubbles();
        retirePluginState();
    }

    private void retirePluginState() {
        for (UUID viewerId : Set.copyOf(overlays.keySet())) {
            codec.clearComposition(viewerId, mannequinEntityId);
        }
        overlays.clear();
        deliveredOverlays.clear();
        pendingOverlayDispatches.clear();
        virtualBubbles.clear();
        codec.removeEntity(mannequinEntityId);
        closed = true;
    }

    private void requireOpen() {
        if (closing || closed) {
            throw new IllegalStateException("Paper NPC behavior renderer is closed");
        }
    }

    private void requireOpenActorLane() {
        requireOpen();
        requireActorLane();
    }

    private void requireActorLane() {
        if (!plugin.getServer().isOwnedByCurrentRegion(mannequin)) {
            throw new IllegalStateException("Paper mannequin access requires its owning entity scheduler");
        }
    }

    private static void configureTextDisplay(TextDisplay display, Component text) {
        display.text(text);
        display.setLineWidth(APPROXIMATE_FORTY_CHARACTER_LINE_WIDTH);
        display.setShadowed(true);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setDefaultBackground(false);
        display.setSeeThrough(false);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setBillboard(Display.Billboard.CENTER);
        display.setViewRange(1.0F);
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.setGravity(false);
    }

    private static ItemStack toItemStack(ItemDescriptor descriptor) {
        if (descriptor == null) {
            return ItemStack.empty();
        }
        Material material = Material.matchMaterial(descriptor.key().asString());
        if (material == null) {
            throw new IllegalArgumentException("Unsupported item key " + descriptor.key());
        }
        return ItemStack.of(material, descriptor.amount());
    }

    private static org.bukkit.inventory.EquipmentSlot toBukkitSlot(
            sh.harold.library.entity.EquipmentSlot slot
    ) {
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

    private static org.bukkit.entity.Pose toBukkitPose(sh.harold.library.entity.EntityPose pose) {
        return switch (pose) {
            case STANDING -> org.bukkit.entity.Pose.STANDING;
            case CROUCHING -> org.bukkit.entity.Pose.SNEAKING;
            case SLEEPING -> org.bukkit.entity.Pose.SLEEPING;
            case SITTING -> org.bukkit.entity.Pose.SITTING;
            case SWIMMING -> org.bukkit.entity.Pose.SWIMMING;
            case SPIN_ATTACK -> org.bukkit.entity.Pose.SPIN_ATTACK;
        };
    }

    private static sh.harold.library.entity.EntityPose fromBukkitPose(org.bukkit.entity.Pose pose) {
        return switch (pose) {
            case SNEAKING -> sh.harold.library.entity.EntityPose.CROUCHING;
            case SLEEPING -> sh.harold.library.entity.EntityPose.SLEEPING;
            case SITTING -> sh.harold.library.entity.EntityPose.SITTING;
            case SWIMMING -> sh.harold.library.entity.EntityPose.SWIMMING;
            case SPIN_ATTACK -> sh.harold.library.entity.EntityPose.SPIN_ATTACK;
            default -> sh.harold.library.entity.EntityPose.STANDING;
        };
    }

    private static SoundCategory soundCategory(Sound.Source source) {
        return switch (source.name()) {
            case "MUSIC" -> SoundCategory.MUSIC;
            case "RECORD" -> SoundCategory.RECORDS;
            case "WEATHER" -> SoundCategory.WEATHER;
            case "BLOCK" -> SoundCategory.BLOCKS;
            case "HOSTILE" -> SoundCategory.HOSTILE;
            case "NEUTRAL" -> SoundCategory.NEUTRAL;
            case "PLAYER" -> SoundCategory.PLAYERS;
            case "AMBIENT" -> SoundCategory.AMBIENT;
            case "VOICE" -> SoundCategory.VOICE;
            case "UI" -> SoundCategory.UI;
            default -> SoundCategory.MASTER;
        };
    }

    private record ActorSnapshot(UUID worldId, double x, double y, double z, Set<UUID> trackedViewers) {
        private ActorSnapshot {
            Objects.requireNonNull(worldId, "worldId");
            trackedViewers = Set.copyOf(Objects.requireNonNull(trackedViewers, "trackedViewers"));
        }
    }

    private record VirtualBubble(long bubbleId, int entityId) {
    }

    private record SharedBubble(
            TextDisplay display,
            int entityId,
            UUID entityUuid,
            UUID worldId,
            int chunkX,
            int chunkZ,
            NpcBubbleFrame frame
    ) {
        private SharedBubble withFrame(NpcBubbleFrame replacement) {
            return new SharedBubble(display, entityId, entityUuid, worldId, chunkX, chunkZ, replacement);
        }
    }

    @FunctionalInterface
    interface ViewerDispatcher {
        boolean enqueue(
                UUID viewerId,
                Consumer<Player> action,
                Runnable retired,
                ViewerWorkPriority priority
        );
    }
}
