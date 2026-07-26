package sh.harold.library.npc.behavior.core;

import sh.harold.library.entity.EntityPose;
import sh.harold.library.entity.EquipmentSlot;
import sh.harold.library.entity.ItemDescriptor;

import java.util.Map;
import java.util.Objects;

/** A complete composable mannequin frame. */
public record NpcRenderFrame(
        float bodyYaw,
        float headYaw,
        float pitch,
        EntityPose pose,
        Map<EquipmentSlot, ItemDescriptor> equipment,
        boolean usingMainHand,
        boolean usingOffHand
) {
    public NpcRenderFrame {
        requireFinite(bodyYaw, "bodyYaw");
        requireFinite(headYaw, "headYaw");
        requireFinite(pitch, "pitch");
        pose = Objects.requireNonNull(pose, "pose");
        equipment = Map.copyOf(Objects.requireNonNull(equipment, "equipment"));
    }

    public static NpcRenderFrame standing(float yaw, float pitch) {
        return new NpcRenderFrame(yaw, yaw, pitch, EntityPose.STANDING, Map.of(), false, false);
    }

    public NpcRenderFrame withLook(float bodyYaw, float headYaw, float pitch) {
        return new NpcRenderFrame(
                bodyYaw,
                headYaw,
                pitch,
                pose,
                equipment,
                usingMainHand,
                usingOffHand
        );
    }

    private static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
