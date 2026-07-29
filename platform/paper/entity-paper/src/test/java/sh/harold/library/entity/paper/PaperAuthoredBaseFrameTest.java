package sh.harold.library.entity.paper;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import sh.harold.library.entity.EntityPose;
import sh.harold.library.entity.EntityFamily;
import sh.harold.library.entity.EntityTypes;
import sh.harold.library.entity.EquipmentSlot;
import sh.harold.library.entity.ItemDescriptor;
import sh.harold.library.npc.behavior.core.NpcRenderFrame;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperAuthoredBaseFrameTest {

    private static final ItemDescriptor BOOK = new ItemDescriptor(Key.key("minecraft:book"), 1);
    private static final ItemDescriptor FEATHER = new ItemDescriptor(Key.key("minecraft:feather"), 1);
    private static final ItemDescriptor HAMMER = new ItemDescriptor(Key.key("minecraft:iron_pickaxe"), 1);

    @Test
    void poseUpdatePreservesLookEquipmentAndActiveHand() {
        NpcRenderFrame base = baseFrame();

        NpcRenderFrame updated = PaperEntityPlatform.withAuthoredPose(base, EntityPose.STANDING);

        assertEquals(EntityPose.STANDING, updated.pose());
        assertEquals(base.bodyYaw(), updated.bodyYaw());
        assertEquals(base.headYaw(), updated.headYaw());
        assertEquals(base.pitch(), updated.pitch());
        assertEquals(base.equipment(), updated.equipment());
        assertEquals(base.usingMainHand(), updated.usingMainHand());
        assertEquals(base.usingOffHand(), updated.usingOffHand());
    }

    @Test
    void equipmentUpdateChangesOnlyRequestedSlot() {
        NpcRenderFrame base = baseFrame();

        NpcRenderFrame updated = PaperEntityPlatform.withAuthoredEquipment(
                base,
                EquipmentSlot.MAIN_HAND,
                Optional.of(HAMMER)
        );

        assertEquals(HAMMER, updated.equipment().get(EquipmentSlot.MAIN_HAND));
        assertEquals(FEATHER, updated.equipment().get(EquipmentSlot.OFF_HAND));
        assertEquals(base.bodyYaw(), updated.bodyYaw());
        assertEquals(base.headYaw(), updated.headYaw());
        assertEquals(base.pitch(), updated.pitch());
        assertEquals(base.pose(), updated.pose());
        assertEquals(base.usingMainHand(), updated.usingMainHand());
        assertEquals(base.usingOffHand(), updated.usingOffHand());

        NpcRenderFrame cleared = PaperEntityPlatform.withAuthoredEquipment(
                updated,
                EquipmentSlot.MAIN_HAND,
                Optional.empty()
        );
        assertEquals(Map.of(EquipmentSlot.OFF_HAND, FEATHER), cleared.equipment());
    }

    @Test
    void lookUpdatePreservesPoseEquipmentAndActiveHand() {
        NpcRenderFrame base = baseFrame();

        NpcRenderFrame updated = PaperEntityPlatform.withAuthoredLook(base, -80.0f, 18.0f);

        assertEquals(-80.0f, updated.bodyYaw());
        assertEquals(-80.0f, updated.headYaw());
        assertEquals(18.0f, updated.pitch());
        assertEquals(base.pose(), updated.pose());
        assertEquals(base.equipment(), updated.equipment());
        assertEquals(base.usingMainHand(), updated.usingMainHand());
        assertEquals(base.usingOffHand(), updated.usingOffHand());
    }

    @Test
    void behaviorCapabilityIsReservedForTheCanonicalHumanoidType() {
        assertTrue(PaperEntityPlatform.supportsHumanoidBehavior(EntityTypes.PLAYER_LIKE_HUMANOID));
        assertFalse(PaperEntityPlatform.supportsHumanoidBehavior(
                EntityTypes.minecraft("mannequin", EntityFamily.HUMANOID)
        ));
    }

    private static NpcRenderFrame baseFrame() {
        return new NpcRenderFrame(
                10.0f,
                15.0f,
                5.0f,
                EntityPose.CROUCHING,
                Map.of(
                        EquipmentSlot.MAIN_HAND, BOOK,
                        EquipmentSlot.OFF_HAND, FEATHER
                ),
                false,
                true
        );
    }
}
