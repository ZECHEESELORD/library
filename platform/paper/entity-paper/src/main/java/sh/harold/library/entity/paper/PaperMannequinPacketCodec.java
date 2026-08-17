package sh.harold.library.entity.paper;

import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import net.kyori.adventure.text.Component;
import sh.harold.library.entity.EquipmentSlot;
import sh.harold.library.entity.ItemDescriptor;
import sh.harold.library.npc.behavior.core.NpcRenderAnimation;
import sh.harold.library.npc.behavior.core.NpcRenderFrame;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The only class that knows PacketEvents wrappers and 26.2 metadata indices.
 * Platform code composes semantic frames; protocol details remain contained
 * here and are guarded by {@link PaperPacketEventsRequirement}.
 */
final class PaperMannequinPacketCodec extends SimplePacketListenerAbstract implements AutoCloseable {
    private static final int ENTITY_POSE_INDEX = 6;
    private static final int LIVING_ACTIVE_HAND_INDEX = 8;

    // TextDisplay indices for the one supported server protocol (26.2).
    private static final int DISPLAY_BILLBOARD_INDEX = 15;
    private static final int DISPLAY_VIEW_RANGE_INDEX = 17;
    private static final int TEXT_INDEX = 23;
    private static final int TEXT_LINE_WIDTH_INDEX = 24;
    private static final int TEXT_BACKGROUND_INDEX = 25;
    private static final int TEXT_OPACITY_INDEX = 26;
    private static final int TEXT_STYLE_FLAGS_INDEX = 27;
    private static final byte BILLBOARD_CENTER = 3;
    private static final byte TEXT_SHADOW = 0x01;
    private static final int VIRTUAL_ID_FLOOR = 1_500_000_000;

    private final PacketEventsAPI<?> api;
    private final Map<ViewerEntityKey, Overlay> overlays = new ConcurrentHashMap<>();
    private final Set<ViewerEntityKey> hiddenEntities = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ConnectionIds> connectionIds = new ConcurrentHashMap<>();
    private volatile boolean closed;

    PaperMannequinPacketCodec(PacketEventsAPI<?> api) {
        super(PacketListenerPriority.HIGHEST);
        this.api = Objects.requireNonNull(api, "api");
        api.getEventManager().registerListener(this);
    }

    void beginConnection(UUID viewerId) {
        requireOpen();
        connectionIds.put(Objects.requireNonNull(viewerId, "viewerId"), new ConnectionIds());
    }

    void endConnection(UUID viewerId) {
        Objects.requireNonNull(viewerId, "viewerId");
        overlays.keySet().removeIf(key -> key.viewerId().equals(viewerId));
        hiddenEntities.removeIf(key -> key.viewerId().equals(viewerId));
        connectionIds.remove(viewerId);
    }

    void compose(UUID viewerId, int entityId, NpcRenderFrame frame, Set<Channel> channels) {
        requireOpen();
        Objects.requireNonNull(frame, "frame");
        EnumSet<Channel> owned = channels.isEmpty()
                ? EnumSet.noneOf(Channel.class)
                : EnumSet.copyOf(channels);
        if (owned.isEmpty()) {
            overlays.remove(new ViewerEntityKey(viewerId, entityId));
        } else {
            overlays.put(new ViewerEntityKey(viewerId, entityId), new Overlay(frame, owned));
        }
    }

    void clearComposition(User viewer, int entityId, NpcRenderFrame currentNativeFrame) {
        Objects.requireNonNull(viewer, "viewer");
        overlays.remove(new ViewerEntityKey(viewer.getUUID(), entityId));
        sendCompleteFrame(viewer, entityId, currentNativeFrame);
    }

    void clearComposition(UUID viewerId, int entityId) {
        overlays.remove(new ViewerEntityKey(
                Objects.requireNonNull(viewerId, "viewerId"),
                entityId
        ));
    }

    void sendCompleteFrame(User viewer, int entityId, NpcRenderFrame frame) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(frame, "frame");
        viewer.sendPacketSilently(new WrapperPlayServerEntityRotation(
                entityId, frame.bodyYaw(), frame.pitch(), true
        ));
        viewer.sendPacketSilently(new WrapperPlayServerEntityHeadLook(entityId, frame.headYaw()));
        viewer.sendPacketSilently(new WrapperPlayServerEntityMetadata(entityId, List.of(
                new EntityData<>(ENTITY_POSE_INDEX, EntityDataTypes.ENTITY_POSE, protocolPose(frame.pose())),
                new EntityData<>(LIVING_ACTIVE_HAND_INDEX, EntityDataTypes.BYTE, activeHandFlags(frame))
        )));
        List<Equipment> equipment = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            equipment.add(new Equipment(protocolSlot(slot), protocolItem(frame.equipment().get(slot), viewer)));
        }
        viewer.sendPacketSilently(new WrapperPlayServerEntityEquipment(entityId, equipment));
    }

    /**
     * Sends only viewer-owned channels that changed between two composed frames.
     * Equipment is deliberately absent: attention overlays never own it, and
     * shared native equipment packets must continue to pass through unchanged.
     */
    void sendFrameDelta(
            User viewer,
            int entityId,
            NpcRenderFrame previous,
            NpcRenderFrame next,
            Set<Channel> channels
    ) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(channels, "channels");

        boolean bodyChanged = channels.contains(Channel.BODY)
                && Float.compare(previous.bodyYaw(), next.bodyYaw()) != 0;
        boolean pitchChanged = channels.contains(Channel.HEAD)
                && Float.compare(previous.pitch(), next.pitch()) != 0;
        if (bodyChanged || pitchChanged) {
            viewer.sendPacketSilently(new WrapperPlayServerEntityRotation(
                    entityId,
                    bodyChanged ? next.bodyYaw() : previous.bodyYaw(),
                    pitchChanged ? next.pitch() : previous.pitch(),
                    true
            ));
        }

        if (channels.contains(Channel.HEAD)
                && Float.compare(previous.headYaw(), next.headYaw()) != 0) {
            viewer.sendPacketSilently(new WrapperPlayServerEntityHeadLook(entityId, next.headYaw()));
        }

        List<EntityData<?>> metadata = new ArrayList<>(2);
        if (channels.contains(Channel.POSE) && previous.pose() != next.pose()) {
            metadata.add(new EntityData<>(
                    ENTITY_POSE_INDEX,
                    EntityDataTypes.ENTITY_POSE,
                    protocolPose(next.pose())
            ));
        }
        if (channels.contains(Channel.ARMS) && activeHandFlags(previous) != activeHandFlags(next)) {
            metadata.add(new EntityData<>(
                    LIVING_ACTIVE_HAND_INDEX,
                    EntityDataTypes.BYTE,
                    activeHandFlags(next)
            ));
        }
        if (!metadata.isEmpty()) {
            viewer.sendPacketSilently(new WrapperPlayServerEntityMetadata(entityId, metadata));
        }
    }

    int showVirtualBubble(User viewer, Component text, Vector3d position) {
        requireOpen();
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(position, "position");
        ConnectionIds ids = connectionIds.computeIfAbsent(viewer.getUUID(), ignored -> new ConnectionIds());
        int entityId = ids.next();
        sendTextDisplay(viewer, entityId, UUID.randomUUID(), text, position);
        return entityId;
    }

    void sendTextDisplay(User viewer, int entityId, UUID entityUuid, Component text, Vector3d position) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(entityUuid, "entityUuid");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(position, "position");
        viewer.sendPacketSilently(new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(entityUuid),
                EntityTypes.TEXT_DISPLAY,
                position,
                0.0f,
                0.0f,
                0.0f,
                0,
                Optional.empty()
        ));
        viewer.sendPacketSilently(new WrapperPlayServerEntityMetadata(entityId, virtualBubbleMetadata(text)));
    }

    void destroyVirtualBubble(User viewer, int entityId) {
        if (viewer != null) {
            viewer.sendPacketSilently(new WrapperPlayServerDestroyEntities(entityId));
        }
    }

    void hideEntity(UUID viewerId, int entityId) {
        hiddenEntities.add(new ViewerEntityKey(
                Objects.requireNonNull(viewerId, "viewerId"),
                entityId
        ));
    }

    void revealEntity(UUID viewerId, int entityId) {
        hiddenEntities.remove(new ViewerEntityKey(
                Objects.requireNonNull(viewerId, "viewerId"),
                entityId
        ));
    }

    void sendAnimation(User viewer, int entityId, NpcRenderAnimation animation) {
        WrapperPlayServerEntityAnimation.EntityAnimationType type = switch (animation.type()) {
            case SWING_OFF_HAND, USE_OFF_HAND ->
                    WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_OFF_HAND;
            case SWING_MAIN_HAND, USE_MAIN_HAND, WAVE ->
                    WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM;
            default -> null;
        };
        if (type != null) {
            viewer.sendPacketSilently(new WrapperPlayServerEntityAnimation(entityId, type));
        }
    }

    void removeEntity(int entityId) {
        overlays.keySet().removeIf(key -> key.entityId() == entityId);
        hiddenEntities.removeIf(key -> key.entityId() == entityId);
    }

    int overlayCount() {
        return overlays.size();
    }

    int connectionCount() {
        return connectionIds.size();
    }

    @Override
    public void onPacketPlaySend(PacketPlaySendEvent event) {
        if (closed) {
            return;
        }
        UUID viewerId = event.getUser().getUUID();
        if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
            ViewerEntityKey key = new ViewerEntityKey(viewerId, packet.getEntityId());
            if (hiddenEntities.contains(key)) {
                event.setCancelled(true);
                return;
            }
            Overlay overlay = overlays.get(key);
            if (overlay != null) {
                if (overlay.channels().contains(Channel.BODY)) {
                    packet.setYaw(overlay.frame().bodyYaw());
                }
                if (overlay.channels().contains(Channel.HEAD)) {
                    packet.setHeadYaw(overlay.frame().headYaw());
                    packet.setPitch(overlay.frame().pitch());
                }
                event.markForReEncode(true);
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_HEAD_LOOK) {
            WrapperPlayServerEntityHeadLook packet = new WrapperPlayServerEntityHeadLook(event);
            Overlay overlay = overlays.get(new ViewerEntityKey(viewerId, packet.getEntityId()));
            if (overlay != null && overlay.channels().contains(Channel.HEAD)) {
                packet.setHeadYaw(overlay.frame().headYaw());
                event.markForReEncode(true);
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_ROTATION) {
            WrapperPlayServerEntityRotation packet = new WrapperPlayServerEntityRotation(event);
            Overlay overlay = overlays.get(new ViewerEntityKey(viewerId, packet.getEntityId()));
            if (overlay != null) {
                boolean changed = false;
                if (overlay.channels().contains(Channel.BODY)) {
                    packet.setYaw(overlay.frame().bodyYaw());
                    changed = true;
                }
                if (overlay.channels().contains(Channel.HEAD)) {
                    packet.setPitch(overlay.frame().pitch());
                    changed = true;
                }
                if (changed) {
                    event.markForReEncode(true);
                }
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
            WrapperPlayServerEntityRelativeMoveAndRotation packet =
                    new WrapperPlayServerEntityRelativeMoveAndRotation(event);
            Overlay overlay = overlays.get(new ViewerEntityKey(viewerId, packet.getEntityId()));
            if (overlay != null) {
                boolean changed = false;
                if (overlay.channels().contains(Channel.BODY)) {
                    packet.setYaw(overlay.frame().bodyYaw());
                    changed = true;
                }
                if (overlay.channels().contains(Channel.HEAD)) {
                    packet.setPitch(overlay.frame().pitch());
                    changed = true;
                }
                if (changed) {
                    event.markForReEncode(true);
                }
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_TELEPORT) {
            WrapperPlayServerEntityTeleport packet = new WrapperPlayServerEntityTeleport(event);
            Overlay overlay = overlays.get(new ViewerEntityKey(viewerId, packet.getEntityId()));
            if (overlay != null) {
                boolean changed = false;
                if (overlay.channels().contains(Channel.BODY)) {
                    packet.setYaw(overlay.frame().bodyYaw());
                    changed = true;
                }
                if (overlay.channels().contains(Channel.HEAD)) {
                    packet.setPitch(overlay.frame().pitch());
                    changed = true;
                }
                if (changed) {
                    event.markForReEncode(true);
                }
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_POSITION_SYNC) {
            WrapperPlayServerEntityPositionSync packet = new WrapperPlayServerEntityPositionSync(event);
            Overlay overlay = overlays.get(new ViewerEntityKey(viewerId, packet.getId()));
            if (overlay != null) {
                boolean changed = false;
                if (overlay.channels().contains(Channel.BODY)) {
                    packet.getValues().setYaw(overlay.frame().bodyYaw());
                    changed = true;
                }
                if (overlay.channels().contains(Channel.HEAD)) {
                    packet.getValues().setPitch(overlay.frame().pitch());
                    changed = true;
                }
                if (changed) {
                    event.markForReEncode(true);
                }
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
            if (hiddenEntities.contains(new ViewerEntityKey(viewerId, packet.getEntityId()))) {
                event.setCancelled(true);
                return;
            }
            Overlay overlay = overlays.get(new ViewerEntityKey(viewerId, packet.getEntityId()));
            if (overlay != null) {
                List<EntityData<?>> data = new ArrayList<>(packet.getEntityMetadata());
                boolean changed = false;
                if (overlay.channels().contains(Channel.POSE)
                        && data.stream().anyMatch(entry -> entry.getIndex() == ENTITY_POSE_INDEX)) {
                    data.removeIf(entry -> entry.getIndex() == ENTITY_POSE_INDEX);
                    data.add(new EntityData<>(
                            ENTITY_POSE_INDEX,
                            EntityDataTypes.ENTITY_POSE,
                            protocolPose(overlay.frame().pose())
                    ));
                    changed = true;
                }
                if (overlay.channels().contains(Channel.ARMS)
                        && data.stream().anyMatch(entry -> entry.getIndex() == LIVING_ACTIVE_HAND_INDEX)) {
                    data.removeIf(entry -> entry.getIndex() == LIVING_ACTIVE_HAND_INDEX);
                    data.add(new EntityData<>(
                            LIVING_ACTIVE_HAND_INDEX,
                            EntityDataTypes.BYTE,
                            activeHandFlags(overlay.frame())
                    ));
                    changed = true;
                }
                if (changed) {
                    packet.setEntityMetadata(data);
                    event.markForReEncode(true);
                }
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_ANIMATION) {
            WrapperPlayServerEntityAnimation packet = new WrapperPlayServerEntityAnimation(event);
            Overlay overlay = overlays.get(new ViewerEntityKey(viewerId, packet.getEntityId()));
            if (overlay != null && overlay.channels().contains(Channel.ARMS)) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        api.getEventManager().unregisterListener(this);
        overlays.clear();
        hiddenEntities.clear();
        connectionIds.clear();
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Paper mannequin packet codec is closed");
        }
    }

    private static List<EntityData<?>> virtualBubbleMetadata(Component text) {
        return List.of(
                new EntityData<>(DISPLAY_BILLBOARD_INDEX, EntityDataTypes.BYTE, BILLBOARD_CENTER),
                new EntityData<>(DISPLAY_VIEW_RANGE_INDEX, EntityDataTypes.FLOAT, 1.0f),
                new EntityData<>(TEXT_INDEX, EntityDataTypes.ADV_COMPONENT, text),
                new EntityData<>(TEXT_LINE_WIDTH_INDEX, EntityDataTypes.INT, 240),
                new EntityData<>(TEXT_BACKGROUND_INDEX, EntityDataTypes.INT, 0),
                new EntityData<>(TEXT_OPACITY_INDEX, EntityDataTypes.BYTE, (byte) -1),
                new EntityData<>(TEXT_STYLE_FLAGS_INDEX, EntityDataTypes.BYTE, TEXT_SHADOW)
        );
    }

    private static EntityPose protocolPose(sh.harold.library.entity.EntityPose pose) {
        return switch (pose) {
            case CROUCHING -> EntityPose.CROUCHING;
            case SLEEPING -> EntityPose.SLEEPING;
            case SWIMMING -> EntityPose.SWIMMING;
            case SPIN_ATTACK -> EntityPose.SPIN_ATTACK;
            case SITTING -> EntityPose.SITTING;
            default -> EntityPose.STANDING;
        };
    }

    private static byte activeHandFlags(NpcRenderFrame frame) {
        if (!frame.usingMainHand() && !frame.usingOffHand()) {
            return 0;
        }
        return (byte) (1 | (frame.usingOffHand() ? 2 : 0));
    }

    private static com.github.retrooper.packetevents.protocol.player.EquipmentSlot protocolSlot(
            EquipmentSlot slot
    ) {
        return switch (slot) {
            case MAIN_HAND -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.MAIN_HAND;
            case OFF_HAND -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.OFF_HAND;
            case FEET -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.BOOTS;
            case LEGS -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.LEGGINGS;
            case CHEST -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.CHEST_PLATE;
            case HEAD -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.HELMET;
            case BODY -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.BODY;
        };
    }

    private static ItemStack protocolItem(ItemDescriptor descriptor, User viewer) {
        if (descriptor == null) {
            return ItemStack.EMPTY;
        }
        ItemType itemType = ItemTypes.getByName(descriptor.key().asString());
        if (itemType == null) {
            throw new IllegalArgumentException("Unsupported item key " + descriptor.key());
        }
        return ItemStack.builder()
                .type(itemType)
                .amount(descriptor.amount())
                .user(viewer)
                .build();
    }

    enum Channel {
        HEAD,
        BODY,
        POSE,
        ARMS
    }

    private record ViewerEntityKey(UUID viewerId, int entityId) {
        private ViewerEntityKey {
            Objects.requireNonNull(viewerId, "viewerId");
        }
    }

    private record Overlay(NpcRenderFrame frame, EnumSet<Channel> channels) {
        private Overlay {
            Objects.requireNonNull(frame, "frame");
            channels = channels.clone();
        }
    }

    private static final class ConnectionIds {
        private final AtomicInteger next = new AtomicInteger(VIRTUAL_ID_FLOOR);

        private int next() {
            return next.getAndIncrement();
        }
    }
}
