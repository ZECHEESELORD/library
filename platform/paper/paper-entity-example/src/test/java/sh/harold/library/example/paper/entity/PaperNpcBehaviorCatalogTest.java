package sh.harold.library.example.paper.entity;

import org.junit.jupiter.api.Test;
import sh.harold.library.npc.behavior.NpcAttentionResponse;
import sh.harold.library.npc.behavior.NpcBehaviorProfile;
import sh.harold.library.npc.behavior.NpcPersonalityPreset;
import sh.harold.library.npc.behavior.NpcRoutineStep;
import sh.harold.library.spatial.AnchorRef;
import sh.harold.library.spatial.AnchorSnapshot;
import sh.harold.library.spatial.Frame3;
import sh.harold.library.spatial.SpaceId;
import sh.harold.library.spatial.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperNpcBehaviorCatalogTest {

    @Test
    void scenesCoverEveryPersonalityWithAuthoredWorldbuilding() {
        PaperNpcBehaviorCatalog.AuthoredBehaviors authored = authored();
        List<NpcBehaviorProfile> profiles = profiles(authored);

        Set<NpcPersonalityPreset> personalities = profiles.stream()
                .map(NpcBehaviorProfile::personality)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(NpcPersonalityPreset.class)));
        assertEquals(EnumSet.allOf(NpcPersonalityPreset.class), personalities);
        profiles.forEach(profile -> {
            assertFalse(profile.idleEntries().isEmpty());
            assertFalse(profile.interactionLines().isEmpty());
            assertFalse(profile.propCompletionLines().isEmpty());
        });
        assertTrue(authored.scribeProfile().conversationInterruptionLines().isEmpty());
        assertTrue(authored.apprenticeProfile().conversationInterruptionLines().isEmpty());
        assertTrue(profiles.stream().filter(profile -> !profile.conversationInterruptionLines().isEmpty()).count() >= 6L);
    }

    @Test
    void customRoutineCompletesTheBuilderPrimitiveShowcase() {
        PaperNpcBehaviorCatalog.AuthoredBehaviors authored = authored();
        List<NpcRoutineStep> steps = authored.reshelveNotes().steps();

        assertTrue(steps.stream().anyMatch(NpcRoutineStep.Clear.class::isInstance));
        assertTrue(steps.stream().anyMatch(NpcRoutineStep.EquipOneOf.class::isInstance));
        assertTrue(steps.stream().anyMatch(NpcRoutineStep.Stance.class::isInstance));
        assertTrue(steps.stream().anyMatch(NpcRoutineStep.Sweep.class::isInstance));
        assertTrue(steps.stream().anyMatch(NpcRoutineStep.UseItem.class::isInstance));
        assertTrue(steps.stream().anyMatch(NpcRoutineStep.Sound.class::isInstance));
        assertTrue(steps.stream()
                .filter(NpcRoutineStep.Gesture.class::isInstance)
                .map(NpcRoutineStep.Gesture.class::cast)
                .anyMatch(gesture -> gesture.sound().isPresent()));
    }

    @Test
    void librarianUsesWeightedCooldownsAndAttentionBarks() {
        NpcBehaviorProfile librarian = authored().librarianProfile();

        assertEquals(List.of(4, 1), librarian.idleEntries().stream().map(entry -> entry.weight()).toList());
        assertTrue(librarian.idleEntries().stream().allMatch(entry -> entry.cooldown().maximumTicks() > 0L));

        NpcAttentionResponse.Sustain idle = (NpcAttentionResponse.Sustain) librarian.attention().idleResponse();
        assertTrue(idle.acquisitionAct().isPresent());
        assertFalse(idle.acquisitionAct().orElseThrow().barkLines().isEmpty());
        NpcAttentionResponse.Acknowledge routine =
                (NpcAttentionResponse.Acknowledge) librarian.attention().routineResponse();
        assertFalse(routine.acknowledgement().gestures().isEmpty());
        assertFalse(routine.acknowledgement().barkLines().isEmpty());
    }

    @Test
    void conversationPoolsSupportLongExchangesAndGenericFallbacks() {
        assertTrue(PaperNpcBehaviorCatalog.libraryCatalogueTopic().lines().size() >= 8);
        assertTrue(PaperNpcBehaviorCatalog.libraryClosingTopic().lines().size() >= 5);
        assertTrue(PaperNpcBehaviorCatalog.forgeOrdersTopic().lines().size() >= 5);
        assertFalse(PaperNpcBehaviorCatalog.libraryCatalogueTopic().interruptionLines().isEmpty());
    }

    private static PaperNpcBehaviorCatalog.AuthoredBehaviors authored() {
        AnchorRef anchor = new AnchorRef.Fixed(new AnchorSnapshot(
                SpaceId.of("test", "npc-diorama"),
                Frame3.world(new Vec3(0.0, 64.0, 0.0))
        ));
        return PaperNpcBehaviorCatalog.author(new PaperNpcBehaviorCatalog.Anchors(
                anchor, anchor, anchor, anchor, anchor, anchor, anchor, anchor
        ));
    }

    private static List<NpcBehaviorProfile> profiles(PaperNpcBehaviorCatalog.AuthoredBehaviors authored) {
        return List.of(
                authored.librarianProfile(),
                authored.archivistProfile(),
                authored.scribeProfile(),
                authored.researcherProfile(),
                authored.nightClerkProfile(),
                authored.blacksmithProfile(),
                authored.apprenticeProfile(),
                authored.quartermasterProfile()
        );
    }
}
