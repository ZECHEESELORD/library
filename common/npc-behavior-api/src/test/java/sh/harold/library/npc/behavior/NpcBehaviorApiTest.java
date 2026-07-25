package sh.harold.library.npc.behavior;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import sh.harold.library.entity.EquipmentSlot;
import sh.harold.library.entity.InteractionHand;
import sh.harold.library.entity.ItemDescriptor;
import sh.harold.library.spatial.AnchorRef;
import sh.harold.library.spatial.AnchorSnapshot;
import sh.harold.library.spatial.Frame3;
import sh.harold.library.spatial.SpaceId;
import sh.harold.library.spatial.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcBehaviorApiTest {

    @Test
    void attentionDefaultsMatchTheAuthoredContract() {
        NpcAttentionSpec spec = NpcAttentionSpec.defaults();

        assertEquals(6.0, spec.enterRadius());
        assertEquals(8.0, spec.exitRadius());
        assertEquals(4.0, spec.maximumVerticalDifference());
        assertTrue(spec.sameSpaceRequired());
        assertTrue(spec.lineOfSightRequired());
        assertEquals(4, spec.lineOfSightProbeIntervalTicks());
        assertEquals(3, spec.lineOfSightFailuresBeforeRelease());
        assertInstanceOf(NpcAttentionResponse.Sustain.class, spec.idleResponse());
        assertInstanceOf(NpcAttentionResponse.Acknowledge.class, spec.routineResponse());
        assertInstanceOf(NpcAttentionResponse.Ignore.class, spec.conversationResponse());
    }

    @Test
    void personalityTuningRejectsValuesOutsideDocumentedRanges() {
        assertThrows(IllegalArgumentException.class, () -> new NpcPersonalityTuning(0.49, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new NpcPersonalityTuning(1.0, 2.01, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new NpcPersonalityTuning(1.0, 1.0, 2.01));
    }

    @Test
    void routineIsAnImmutableDeclarativeSequence() {
        AnchorRef anchor = anchor();
        List<ItemDescriptor> items = new ArrayList<>();
        items.add(new ItemDescriptor(Key.key("minecraft", "stick"), 1));

        NpcRoutine routine = NpcRoutine.builder(Key.key("test", "craft"))
                .lookAt(anchor, NpcTimingBand.SHORT)
                .equipOneOf(EquipmentSlot.MAIN_HAND, items)
                .swing(InteractionHand.MAIN_HAND)
                .wait(NpcTimingBand.QUICK)
                .build();
        items.clear();

        assertEquals(4, routine.steps().size());
        NpcRoutineStep.EquipOneOf equip = assertInstanceOf(
                NpcRoutineStep.EquipOneOf.class,
                routine.steps().get(1)
        );
        assertEquals(1, equip.items().size());
        assertThrows(UnsupportedOperationException.class, () -> routine.steps().clear());
    }

    @Test
    void profileDefensivelyCopiesRichComponentPools() {
        List<Component> lines = new ArrayList<>();
        Component authored = Component.text("Styled").color(net.kyori.adventure.text.format.NamedTextColor.GOLD);
        lines.add(authored);

        NpcBehaviorProfile profile = NpcBehaviorProfile.builder(NpcPersonalityPreset.WARM)
                .interactionLines(lines)
                .build();
        lines.clear();

        assertEquals(List.of(authored), profile.interactionLines());
        assertThrows(UnsupportedOperationException.class, () -> profile.interactionLines().clear());
    }

    @Test
    void builtInRoutinesUseTheSamePublicPrimitives() {
        AnchorRef anchor = anchor();
        ItemDescriptor tool = new ItemDescriptor(Key.key("minecraft", "iron_pickaxe"), 1);
        ItemDescriptor planks = new ItemDescriptor(Key.key("minecraft", "oak_planks"), 1);
        List<NpcRoutine> routines = List.of(
                NpcRoutines.LECTERN_STUDY(anchor),
                NpcRoutines.ANVIL_FORGING(anchor, tool),
                NpcRoutines.SHELF_DISTRACTION(anchor),
                NpcRoutines.TABLE_CRAFTING(anchor, List.of(planks))
        );

        assertEquals(
                List.of(
                        NpcRoutines.LECTERN_STUDY,
                        NpcRoutines.ANVIL_FORGING,
                        NpcRoutines.SHELF_DISTRACTION,
                        NpcRoutines.TABLE_CRAFTING
                ),
                routines.stream().map(NpcRoutine::key).toList()
        );
        assertInstanceOf(NpcRoutineStep.Equip.class, routines.getFirst().steps().getFirst());
        assertTrue(routines.stream().allMatch(routine -> !routine.steps().isEmpty()));
    }

    @Test
    void timingBandsExposeTheAuthoredTickRanges() {
        assertEquals(List.of(4, 8, 18, 36),
                List.of(NpcTimingBand.values()).stream().map(NpcTimingBand::minimumTicks).toList());
        assertEquals(List.of(7, 14, 30, 60),
                List.of(NpcTimingBand.values()).stream().map(NpcTimingBand::maximumTicks).toList());
    }

    @Test
    void customSoundProfilesDefensivelyKeepEqualProbabilityVariants() {
        NpcSoundProfile.Variant soft = new NpcSoundProfile.Variant(
                Key.key("example", "soft"), Sound.Source.NEUTRAL, 0.5f, 0.9f, 1.1f);
        NpcSoundProfile.Variant bright = new NpcSoundProfile.Variant(
                Key.key("example", "bright"), Sound.Source.NEUTRAL, 0.7f, 1.1f, 1.3f);

        NpcSoundProfile profile = NpcSoundProfile.builder().variant(soft).variant(bright).build();

        assertEquals(List.of(soft, bright), profile.variants());
        assertFalse(profile.silent());
        assertThrows(UnsupportedOperationException.class, () -> profile.variants().clear());
        assertTrue(NpcSoundProfiles.SILENT.silent());
    }

    @Test
    void conversationTopicAndSnapshotAreDeeplyImmutable() {
        List<Component> lines = new ArrayList<>(List.of(Component.text("First"), Component.text("Second")));
        NpcConversationTopic topic = NpcConversationTopic.of(
                Key.key("example", "chat"), lines, List.of(Component.text("Interrupted")));
        lines.clear();

        UUID viewer = UUID.randomUUID();
        List<UUID> acquisitionStack = new ArrayList<>(List.of(viewer));
        NpcBehaviorSnapshot snapshot = new NpcBehaviorSnapshot(
                true,
                NpcBehaviorActivity.AMBIENT_IDLE,
                Optional.of(viewer),
                acquisitionStack,
                Optional.empty(),
                Optional.of(Component.text("Visible")),
                2,
                false,
                4L
        );
        acquisitionStack.clear();

        assertEquals(2, topic.lines().size());
        assertEquals(List.of(viewer), snapshot.acquisitionStack());
        assertThrows(UnsupportedOperationException.class, () -> topic.lines().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.acquisitionStack().clear());
        assertThrows(IllegalArgumentException.class, () -> NpcConversationTopic.of(
                Key.key("example", "empty"), List.of()));
    }

    private static AnchorRef anchor() {
        return new AnchorRef.Fixed(new AnchorSnapshot(
                SpaceId.of("test", "world"),
                Frame3.world(Vec3.ZERO)
        ));
    }
}
