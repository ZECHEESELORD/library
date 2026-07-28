package sh.harold.library.entity.minestom;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityPose;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.MetadataDef;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket;
import net.minestom.server.network.packet.server.play.EntityAnimationPacket;
import net.minestom.server.network.packet.server.play.EntityEquipmentPacket;
import net.minestom.server.network.packet.server.play.EntityHeadLookPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.EntityPositionAndRotationPacket;
import net.minestom.server.network.packet.server.play.EntityPositionSyncPacket;
import net.minestom.server.network.packet.server.play.EntityRotationPacket;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import sh.harold.library.entity.ItemDescriptor;
import sh.harold.library.npc.behavior.core.NpcBehaviorRenderPort;
import sh.harold.library.npc.behavior.core.NpcBubbleFrame;
import sh.harold.library.npc.behavior.core.NpcRenderAnimation;
import sh.harold.library.npc.behavior.core.NpcRenderFrame;
import sh.harold.library.npc.behavior.core.NpcRenderedSound;
import sh.harold.library.spatial.AnchorRef;
import sh.harold.library.spatial.AnchorSnapshot;
import sh.harold.library.spatial.Frame3;
import sh.harold.library.spatial.SpaceId;
import sh.harold.library.spatial.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Minestom's native/packet composition boundary for mannequin behavior.
 *
 * <p>The real mannequin remains authoritative for the shared performance. Only
 * the rotation, pose, active-hand and animation channels are replaced for a
 * viewer that owns an attention overlay.</p>
 */
final class MinestomNpcBehaviorRenderPort implements NpcBehaviorRenderPort, AutoCloseable {

    private static final double DEFAULT_BUBBLE_HEIGHT = 2.35;
    private static final int APPROXIMATE_FORTY_CHARACTER_LINE_WIDTH = 240;

    private final LivingEntity mannequin;
    private final Function<net.minestom.server.instance.Instance, SpaceId> spaceResolver;
    private final Map<UUID, NpcRenderFrame> overlays = new ConcurrentHashMap<>();
    private final Map<UUID, NpcRenderFrame> deliveredOverlays = new ConcurrentHashMap<>();
    private final Map<UUID, VirtualBubble> virtualBubbles = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> viewerArmOwnershipTicks = new ConcurrentHashMap<>();
    private final Set<UUID> pendingReapply = ConcurrentHashMap.newKeySet();
    private final ThreadLocal<Boolean> directPacket = ThreadLocal.withInitial(() -> false);
    private volatile NpcRenderFrame baseFrame;
    private NpcRenderFrame nativeFrame;
    private volatile Entity sharedBubble;
    private volatile long sharedBubbleId = -1L;
    private volatile long sharedBubbleGeneration;
    private volatile double reservedOverheadHeight;
    private volatile boolean closed;

    MinestomNpcBehaviorRenderPort(
            LivingEntity mannequin,
            Function<net.minestom.server.instance.Instance, SpaceId> spaceResolver
    ) {
        this.mannequin = Objects.requireNonNull(mannequin, "mannequin");
        this.spaceResolver = Objects.requireNonNull(spaceResolver, "spaceResolver");
        Pos position = mannequin.getPosition();
        this.baseFrame = NpcRenderFrame.standing(position.yaw(), position.pitch());
        this.nativeFrame = baseFrame;
    }

    void updateBaseFrame(NpcRenderFrame frame) {
        baseFrame = Objects.requireNonNull(frame, "frame");
    }

    void reserveOverheadHeight(double height) {
        if (!Double.isFinite(height) || height < 0.0) {
            throw new IllegalArgumentException("height must be finite and non-negative");
        }
        reservedOverheadHeight = Math.max(reservedOverheadHeight, height);
        synchronizeSharedBubblePosition();
    }

    NpcRenderFrame nativeFrame() {
        return nativeFrame;
    }

    boolean hasOverlay(UUID viewerId) {
        return overlays.containsKey(viewerId);
    }

    @Override
    public CompletionStage<Void> restoreNativePresentation() {
        if (closed || mannequin.isRemoved()) {
            return CompletableFuture.completedFuture(null);
        }
        clearAllVirtualBubbles();
        clearSharedBubbleInternal();
        Set<UUID> affectedViewers = Set.copyOf(overlays.keySet());
        overlays.clear();
        deliveredOverlays.clear();
        pendingReapply.clear();
        viewerArmOwnershipTicks.clear();
        applyNativeFrame(baseFrame);
        for (UUID viewerId : affectedViewers) {
            Player player = onlinePlayer(viewerId);
            if (player != null) {
                sendCompleteFrame(player, baseFrame);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Optional<AnchorSnapshot> resolveAnchor(AnchorRef anchor) {
        Objects.requireNonNull(anchor, "anchor");
        if (mannequin.getInstance() == null) {
            return Optional.empty();
        }
        if (anchor instanceof AnchorRef.Fixed fixed) {
            return sameSpace(fixed.snapshot().spaceId()) ? Optional.of(fixed.snapshot()) : Optional.empty();
        }
        if (anchor instanceof AnchorRef.Entity entityRef) {
            if (!sameSpace(entityRef.spaceId())) {
                return Optional.empty();
            }
            Entity target = mannequin.getInstance().getEntityByUuid(entityRef.entityId());
            return target == null ? Optional.empty() : Optional.of(snapshot(target));
        }
        AnchorRef.Offset offset = (AnchorRef.Offset) anchor;
        return resolveAnchor(offset.base()).map(snapshot -> snapshot.translated(offset.localOffset()));
    }

    @Override
    public void renderSharedFrame(NpcRenderFrame frame) {
        requireOpen();
        applyNativeFrame(Objects.requireNonNull(frame, "frame"));
    }

    @Override
    public void renderViewerOverlay(UUID viewerId, NpcRenderFrame frame) {
        requireOpen();
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(frame, "frame");
        overlays.put(viewerId, frame);
        Player player = eligibleViewer(viewerId);
        if (player != null) {
            NpcRenderFrame previous = deliveredOverlays.get(viewerId);
            sendFrameDelta(player, previous == null ? nativeFrame : previous, frame);
            deliveredOverlays.put(viewerId, frame);
        }
    }

    @Override
    public void clearViewerOverlay(UUID viewerId, NpcRenderFrame currentNativeFrame) {
        Objects.requireNonNull(viewerId, "viewerId");
        NpcRenderFrame removed = overlays.remove(viewerId);
        deliveredOverlays.remove(viewerId);
        viewerArmOwnershipTicks.remove(viewerId);
        clearVirtualBubbleFor(viewerId, -1L);
        if (removed == null || closed) {
            return;
        }
        Player player = eligibleViewer(viewerId);
        if (player != null) {
            sendCompleteFrame(player, Objects.requireNonNull(currentNativeFrame, "currentNativeFrame"));
        }
    }

    @Override
    public void showSharedBubble(NpcBubbleFrame bubble) {
        requireOpen();
        Objects.requireNonNull(bubble, "bubble");
        Entity existing = sharedBubble;
        if (existing != null && !existing.isRemoved() && sharedBubbleId == bubble.id()) {
            existing.editEntityMeta(TextDisplayMeta.class, meta -> meta.setText(bubble.text()));
            applyBubbleVisibility(existing, bubble);
            return;
        }
        clearSharedBubbleInternal();
        net.minestom.server.instance.Instance instance = mannequin.getInstance();
        if (instance == null) {
            return;
        }

        long generation = ++sharedBubbleGeneration;
        Entity display = configuredTextDisplay(bubble.text());
        applyBubbleVisibility(display, bubble);
        sharedBubble = display;
        sharedBubbleId = bubble.id();
        display.setInstance(instance, bubblePosition()).whenComplete((ignored, failure) -> {
            if (failure != null || closed || generation != sharedBubbleGeneration || sharedBubble != display) {
                display.remove();
            }
        });
    }

    @Override
    public void clearSharedBubble(long bubbleId) {
        if (sharedBubble != null && (bubbleId < 0L || sharedBubbleId == bubbleId)) {
            clearSharedBubbleInternal();
        }
    }

    @Override
    public void showVirtualBubble(UUID viewerId, NpcBubbleFrame bubble) {
        requireOpen();
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(bubble, "bubble");
        Player player = eligibleViewer(viewerId);
        if (player == null) {
            return;
        }
        clearVirtualBubbleFor(viewerId, -1L);

        int virtualId = Entity.generateId();
        Entity template = configuredTextDisplay(bubble.text());
        Pos position = bubblePosition();
        UUID virtualUuid = UUID.nameUUIDFromBytes(
                (mannequin.getUuid() + ":" + viewerId + ":" + virtualId).getBytes(StandardCharsets.UTF_8)
        );
        sendDirect(player, new SpawnEntityPacket(
                virtualId,
                virtualUuid,
                EntityType.TEXT_DISPLAY,
                position,
                0.0f,
                0,
                Vec.ZERO
        ));
        sendDirect(player, new EntityMetaDataPacket(virtualId, template.getMetadataPacket().entries()));
        virtualBubbles.put(viewerId, new VirtualBubble(virtualId, bubble.id(), position));
    }

    @Override
    public void clearVirtualBubble(UUID viewerId, long bubbleId) {
        clearVirtualBubbleFor(Objects.requireNonNull(viewerId, "viewerId"), bubbleId);
    }

    @Override
    public void animateShared(NpcRenderAnimation animation) {
        requireOpen();
        Objects.requireNonNull(animation, "animation");
        switch (animation.type()) {
            case SWING_OFF_HAND, USE_OFF_HAND -> mannequin.swingOffHand();
            case SWING_MAIN_HAND, USE_MAIN_HAND, WAVE -> mannequin.swingMainHand();
            default -> {
                // Head/body/stance gesture motion arrives through renderSharedFrame.
            }
        }
    }

    @Override
    public void animateAttention(NpcRenderAnimation animation, Set<UUID> excludedViewers) {
        requireOpen();
        Objects.requireNonNull(animation, "animation");
        Set<UUID> excluded = Set.copyOf(Objects.requireNonNull(excludedViewers, "excludedViewers"));
        for (Player viewer : mannequin.getViewers()) {
            if (!excluded.contains(viewer.getUuid())) {
                animateViewer(viewer.getUuid(), animation);
            }
        }
    }

    @Override
    public void animateViewer(UUID viewerId, NpcRenderAnimation animation) {
        requireOpen();
        Objects.requireNonNull(animation, "animation");
        Player player = eligibleViewer(Objects.requireNonNull(viewerId, "viewerId"));
        if (player == null) {
            return;
        }
        EntityAnimationPacket.Animation packetAnimation = switch (animation.type()) {
            case SWING_OFF_HAND, USE_OFF_HAND -> EntityAnimationPacket.Animation.SWING_OFF_HAND;
            case SWING_MAIN_HAND, USE_MAIN_HAND, WAVE -> EntityAnimationPacket.Animation.SWING_MAIN_ARM;
            default -> null;
        };
        if (packetAnimation != null) {
            viewerArmOwnershipTicks.put(viewerId, animation.durationTicks());
            sendDirect(player, new EntityAnimationPacket(mannequin.getEntityId(), packetAnimation));
        }
    }

    @Override
    public void playSound(NpcRenderedSound renderedSound) {
        requireOpen();
        Objects.requireNonNull(renderedSound, "renderedSound");
        Sound sound = Sound.sound(
                renderedSound.key(),
                renderedSound.source(),
                renderedSound.volume(),
                renderedSound.pitch()
        );
        if (renderedSound.recipient().isPresent()) {
            Player player = eligibleViewer(renderedSound.recipient().orElseThrow());
            if (player != null) {
                player.playSound(sound, mannequin.getPosition());
            }
            return;
        }
        for (Player viewer : mannequin.getViewers()) {
            viewer.playSound(sound, mannequin.getPosition());
        }
    }

    /** Keeps real text anchored without allocating a separate display task. */
    void tick() {
        if (!closed) {
            viewerArmOwnershipTicks.replaceAll((viewerId, remaining) -> remaining - 1);
            viewerArmOwnershipTicks.values().removeIf(remaining -> remaining <= 0);
            synchronizeSharedBubblePosition();
            synchronizeVirtualBubblePositions();
        }
    }

    /** Reapplies composed state after Minestom sends a fresh spawn frame. */
    void reapplyAfterTrack(UUID viewerId) {
        if (closed) {
            return;
        }
        if (!overlays.containsKey(viewerId)) {
            deliveredOverlays.remove(viewerId);
            return;
        }
        pendingReapply.add(viewerId);
    }

    /** Drains retrack composition from the platform's existing per-player tick. */
    void flushViewer(Player player) {
        UUID viewerId = player.getUuid();
        if (closed || !pendingReapply.remove(viewerId)) {
            return;
        }
        NpcRenderFrame latest = overlays.get(viewerId);
        if (latest != null && eligibleViewer(viewerId) == player) {
            sendCompleteFrame(player, latest);
            deliveredOverlays.put(viewerId, latest);
        }
    }

    /** Removes every client-only allocation owned by a departing viewer. */
    void retireViewer(UUID viewerId) {
        overlays.remove(viewerId);
        deliveredOverlays.remove(viewerId);
        pendingReapply.remove(viewerId);
        viewerArmOwnershipTicks.remove(viewerId);
        clearVirtualBubbleFor(viewerId, -1L);
    }

    /**
     * Returns true when the native packet was replaced or suppressed for this
     * viewer. Direct replacement packets are guarded against recursion.
     */
    boolean intercept(Player player, SendablePacket packet) {
        if (closed || directPacket.get()) {
            return false;
        }
        NpcRenderFrame overlay = overlays.get(player.getUuid());
        if (overlay == null) {
            return false;
        }
        int entityId = mannequin.getEntityId();
        if (packet instanceof EntityRotationPacket rotation && rotation.entityId() == entityId) {
            sendDirect(player, new EntityRotationPacket(entityId, overlay.bodyYaw(), overlay.pitch(), rotation.onGround()));
            return true;
        }
        if (packet instanceof EntityHeadLookPacket headLook && headLook.entityId() == entityId) {
            sendDirect(player, new EntityHeadLookPacket(entityId, overlay.headYaw()));
            return true;
        }
        if (packet instanceof EntityPositionAndRotationPacket movement && movement.entityId() == entityId) {
            sendDirect(player, new EntityPositionAndRotationPacket(
                    entityId,
                    movement.deltaX(),
                    movement.deltaY(),
                    movement.deltaZ(),
                    overlay.bodyYaw(),
                    overlay.pitch(),
                    movement.onGround()
            ));
            return true;
        }
        if (packet instanceof EntityPositionSyncPacket sync && sync.entityId() == entityId) {
            sendDirect(player, new EntityPositionSyncPacket(
                    entityId,
                    sync.position(),
                    sync.delta(),
                    overlay.bodyYaw(),
                    overlay.pitch(),
                    sync.onGround()
            ));
            return true;
        }
        if (packet instanceof EntityTeleportPacket teleport && teleport.entityId() == entityId) {
            sendDirect(player, new EntityTeleportPacket(
                    entityId,
                    teleport.position().withView(overlay.bodyYaw(), overlay.pitch()),
                    teleport.delta(),
                    teleport.flags(),
                    teleport.onGround()
            ));
            return true;
        }
        if (packet instanceof EntityMetaDataPacket metadata && metadata.entityId() == entityId) {
            Map<Integer, Metadata.Entry<?>> entries = metadata.entries();
            int poseIndex = MetadataDef.POSE.index();
            if (!entries.containsKey(poseIndex)) {
                return false;
            }
            Map<Integer, Metadata.Entry<?>> composed = new HashMap<>(entries);
            composed.put(poseIndex, Metadata.Pose(toMinestomPose(overlay.pose())));
            sendDirect(player, new EntityMetaDataPacket(entityId, Map.copyOf(composed)));
            return true;
        }
        if (packet instanceof EntityAnimationPacket animation
                && animation.entityId() == entityId
                && viewerArmOwnershipTicks.containsKey(player.getUuid())) {
            // Suppress a shared arm beat only while this viewer's branch owns the arm channel.
            return true;
        }
        if (packet instanceof SpawnEntityPacket spawn && spawn.entityId() == entityId) {
            reapplyAfterTrack(player.getUuid());
        }
        return false;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        clearAllVirtualBubbles();
        overlays.clear();
        deliveredOverlays.clear();
        pendingReapply.clear();
        viewerArmOwnershipTicks.clear();
        clearSharedBubbleInternal();
    }

    private void applyNativeFrame(NpcRenderFrame frame) {
        nativeFrame = frame;
        mannequin.setView(frame.bodyYaw(), frame.pitch(), frame.headYaw());
        applyPose(frame.pose());
        for (sh.harold.library.entity.EquipmentSlot slot : sh.harold.library.entity.EquipmentSlot.values()) {
            ItemDescriptor item = frame.equipment().get(slot);
            mannequin.setEquipment(toMinestomSlot(slot), item == null ? ItemStack.AIR : toItemStack(item));
        }
        if (frame.usingMainHand() || frame.usingOffHand()) {
            mannequin.refreshActiveHand(true, frame.usingOffHand(), false);
        } else {
            mannequin.refreshActiveHand(false, false, false);
        }
        synchronizeSharedBubblePosition();
    }

    private void sendCompleteFrame(Player player, NpcRenderFrame frame) {
        int entityId = mannequin.getEntityId();
        sendDirect(player, new EntityRotationPacket(entityId, frame.bodyYaw(), frame.pitch(), mannequin.isOnGround()));
        sendDirect(player, new EntityHeadLookPacket(entityId, frame.headYaw()));
        sendDirect(player, new EntityMetaDataPacket(entityId, Map.of(
                MetadataDef.POSE.index(), Metadata.Pose(toMinestomPose(frame.pose())),
                MetadataDef.LivingEntity.LIVING_ENTITY_FLAGS.index(), Metadata.Byte(activeHandFlags(frame))
        )));

        Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        for (sh.harold.library.entity.EquipmentSlot slot : sh.harold.library.entity.EquipmentSlot.values()) {
            ItemDescriptor item = frame.equipment().get(slot);
            equipment.put(toMinestomSlot(slot), item == null ? ItemStack.AIR : toItemStack(item));
        }
        sendDirect(player, new EntityEquipmentPacket(entityId, Map.copyOf(equipment)));
    }

    /** Sends only attention-owned channels whose composed values changed. */
    private void sendFrameDelta(Player player, NpcRenderFrame previous, NpcRenderFrame next) {
        int entityId = mannequin.getEntityId();
        if (Float.compare(previous.bodyYaw(), next.bodyYaw()) != 0
                || Float.compare(previous.pitch(), next.pitch()) != 0) {
            sendDirect(player, new EntityRotationPacket(
                    entityId,
                    next.bodyYaw(),
                    next.pitch(),
                    mannequin.isOnGround()
            ));
        }
        if (Float.compare(previous.headYaw(), next.headYaw()) != 0) {
            sendDirect(player, new EntityHeadLookPacket(entityId, next.headYaw()));
        }
        if (previous.pose() != next.pose()) {
            sendDirect(player, new EntityMetaDataPacket(entityId, Map.of(
                    MetadataDef.POSE.index(), Metadata.Pose(toMinestomPose(next.pose()))
            )));
        }
    }

    private void synchronizeSharedBubblePosition() {
        Entity bubble = sharedBubble;
        if (bubble == null || bubble.isRemoved() || bubble.getInstance() == null) {
            return;
        }
        Pos wanted = bubblePosition();
        Pos current = bubble.getPosition();
        if (current.distanceSquared(wanted) > 1.0e-6) {
            bubble.refreshPosition(wanted);
        }
    }

    private void synchronizeVirtualBubblePositions() {
        Pos wanted = bubblePosition();
        for (Map.Entry<UUID, VirtualBubble> entry : virtualBubbles.entrySet()) {
            UUID viewerId = entry.getKey();
            VirtualBubble bubble = entry.getValue();
            Player player = eligibleViewer(viewerId);
            if (player == null) {
                if (virtualBubbles.remove(viewerId, bubble)) {
                    Player online = onlinePlayer(viewerId);
                    if (online != null) {
                        sendDirect(online, new DestroyEntitiesPacket(bubble.entityId()));
                    }
                }
                continue;
            }
            if (bubble.position().distanceSquared(wanted) <= 1.0e-6) {
                continue;
            }
            sendDirect(player, new EntityTeleportPacket(
                    bubble.entityId(),
                    wanted,
                    Vec.ZERO,
                    0,
                    false
            ));
            virtualBubbles.replace(
                    viewerId,
                    bubble,
                    new VirtualBubble(bubble.entityId(), bubble.bubbleId(), wanted)
            );
        }
    }

    private Pos bubblePosition() {
        Pos position = mannequin.getPosition();
        return new Pos(
                position.x(),
                position.y() + DEFAULT_BUBBLE_HEIGHT + reservedOverheadHeight,
                position.z(),
                0.0f,
                0.0f
        );
    }

    private void clearSharedBubbleInternal() {
        sharedBubbleGeneration++;
        Entity bubble = sharedBubble;
        sharedBubble = null;
        sharedBubbleId = -1L;
        if (bubble != null && !bubble.isRemoved()) {
            bubble.remove();
        }
    }

    private void clearAllVirtualBubbles() {
        for (UUID viewerId : Map.copyOf(virtualBubbles).keySet()) {
            clearVirtualBubbleFor(viewerId, -1L);
        }
    }

    private void clearVirtualBubbleFor(UUID viewerId, long bubbleId) {
        VirtualBubble bubble = virtualBubbles.get(viewerId);
        if (bubble == null || (bubbleId >= 0L && bubble.bubbleId() != bubbleId)) {
            return;
        }
        virtualBubbles.remove(viewerId);
        Player player = onlinePlayer(viewerId);
        if (player != null) {
            sendDirect(player, new DestroyEntitiesPacket(bubble.entityId()));
        }
    }

    private Entity configuredTextDisplay(net.kyori.adventure.text.Component text) {
        Entity display = new Entity(EntityType.TEXT_DISPLAY);
        display.editEntityMeta(TextDisplayMeta.class, meta -> {
            meta.setText(text);
            meta.setLineWidth(APPROXIMATE_FORTY_CHARACTER_LINE_WIDTH);
            meta.setShadow(true);
            meta.setBackgroundColor(0x00000000);
            meta.setUseDefaultBackground(false);
            meta.setSeeThrough(false);
            meta.setAlignment(TextDisplayMeta.Alignment.CENTER);
            meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER);
        });
        display.setNoGravity(true);
        display.setHasPhysics(false);
        display.setSilent(true);
        return display;
    }

    private static void applyBubbleVisibility(Entity display, NpcBubbleFrame bubble) {
        display.updateViewableRule(player -> !bubble.excludedViewers().contains(player.getUuid()));
    }

    private Player eligibleViewer(UUID viewerId) {
        Player player = onlinePlayer(viewerId);
        if (player == null || player.getInstance() != mannequin.getInstance() || !mannequin.getViewers().contains(player)) {
            return null;
        }
        return player;
    }

    private static Player onlinePlayer(UUID viewerId) {
        return MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(viewerId);
    }

    private boolean sameSpace(SpaceId spaceId) {
        return spaceResolver.apply(mannequin.getInstance()).equals(spaceId);
    }

    private AnchorSnapshot snapshot(Entity target) {
        Pos position = target.getPosition();
        double yaw = Math.toRadians(position.yaw());
        double pitch = Math.toRadians(position.pitch());
        Vec3 forward = new Vec3(
                -Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch)
        );
        return new AnchorSnapshot(
                spaceResolver.apply(target.getInstance()),
                Frame3.of(new Vec3(position.x(), position.y(), position.z()), forward, Vec3.UNIT_Y)
        );
    }

    private void sendDirect(Player player, SendablePacket packet) {
        boolean previous = directPacket.get();
        directPacket.set(true);
        try {
            player.sendPacket(packet);
        } finally {
            directPacket.set(previous);
        }
    }

    private void requireOpen() {
        if (closed || mannequin.isRemoved()) {
            throw new IllegalStateException("NPC behavior renderer is closed");
        }
    }

    private static byte activeHandFlags(NpcRenderFrame frame) {
        if (!frame.usingMainHand() && !frame.usingOffHand()) {
            return 0;
        }
        return (byte) (1 | (frame.usingOffHand() ? 2 : 0));
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

    private void applyPose(sh.harold.library.entity.EntityPose pose) {
        mannequin.setSneaking(pose == sh.harold.library.entity.EntityPose.CROUCHING);
        if (pose != sh.harold.library.entity.EntityPose.CROUCHING) {
            mannequin.setPose(toMinestomPose(pose));
        }
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

    private static ItemStack toItemStack(ItemDescriptor descriptor) {
        Material material = Material.fromKey(descriptor.key());
        if (material == null) {
            throw new IllegalArgumentException("Unsupported item key " + descriptor.key());
        }
        return ItemStack.of(material, descriptor.amount());
    }

    private record VirtualBubble(int entityId, long bubbleId, Pos position) {
    }
}
