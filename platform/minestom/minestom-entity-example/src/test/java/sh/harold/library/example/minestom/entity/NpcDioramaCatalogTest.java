package sh.harold.library.example.minestom.entity;

import org.junit.jupiter.api.Test;
import sh.harold.library.npc.behavior.NpcAttentionResponse;
import sh.harold.library.npc.behavior.NpcBehaviorProfile;
import sh.harold.library.npc.behavior.NpcPersonalityPreset;
import sh.harold.library.npc.behavior.NpcRoutineStep;
import sh.harold.library.npc.behavior.NpcRoutines;
import sh.harold.library.npc.behavior.NpcVoiceDeliveryStyle;
import sh.harold.library.spatial.SpaceId;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcDioramaCatalogTest {

    private final NpcDioramaCatalog.Catalog catalog = NpcDioramaCatalog.create(
            SpaceId.of("test", "npc-dioramas")
    );

    @Test
    void coversEveryPersonalityAndAllFourShippedRoutines() {
        Set<NpcPersonalityPreset> personalities = catalog.profiles().values().stream()
                .map(NpcBehaviorProfile::personality)
                .collect(Collectors.toSet());
        assertEquals(EnumSet.allOf(NpcPersonalityPreset.class), personalities);

        Set<?> routineKeys = catalog.routines().values().stream()
                .map(routine -> routine.key())
                .collect(Collectors.toSet());
        assertTrue(routineKeys.contains(NpcRoutines.LECTERN_STUDY));
        assertTrue(routineKeys.contains(NpcRoutines.ANVIL_FORGING));
        assertTrue(routineKeys.contains(NpcRoutines.SHELF_DISTRACTION));
        assertTrue(routineKeys.contains(NpcRoutines.TABLE_CRAFTING));
    }

    @Test
    void everyActorAuthorsAmbientInteractionAndPropCompletionContent() {
        assertEquals(8, catalog.profiles().size());
        for (NpcBehaviorProfile profile : catalog.profiles().values()) {
            assertFalse(profile.idleEntries().isEmpty(), profile.personality() + " needs an idle");
            assertFalse(profile.interactionLines().isEmpty(), profile.personality() + " needs an interaction line");
            assertFalse(profile.propCompletionLines().isEmpty(), profile.personality() + " needs a prop line");
        }

        NpcBehaviorProfile librarian = catalog.profile(NpcDioramaCatalog.LIBRARIAN);
        assertEquals(2, librarian.idleEntries().size());
        assertTrue(librarian.idleEntries().stream().mapToInt(entry -> entry.weight()).distinct().count() > 1);
        assertTrue(librarian.idleEntries().stream().allMatch(entry -> entry.cooldown().maximumTicks() > 0));
    }

    @Test
    void tunedAttentionIncludesAcquisitionAndRoutineBarks() {
        NpcBehaviorProfile librarian = catalog.profile(NpcDioramaCatalog.LIBRARIAN);
        NpcAttentionResponse.Sustain idle = assertInstanceOf(
                NpcAttentionResponse.Sustain.class,
                librarian.attention().idleResponse()
        );
        assertTrue(idle.acquisitionAct().isPresent());
        assertFalse(idle.acquisitionAct().orElseThrow().gestures().isEmpty());
        assertFalse(idle.acquisitionAct().orElseThrow().barkLines().isEmpty());

        NpcAttentionResponse.Acknowledge routine = assertInstanceOf(
                NpcAttentionResponse.Acknowledge.class,
                librarian.attention().routineResponse()
        );
        assertFalse(routine.acknowledgement().barkLines().isEmpty());
        assertInstanceOf(NpcAttentionResponse.Ignore.class, librarian.attention().conversationResponse());
    }

    @Test
    void routineCatalogUsesEveryDeclarativePrimitiveIncludingSoundGestureAndClear() {
        var steps = catalog.routines().values().stream()
                .flatMap(routine -> routine.steps().stream())
                .toList();
        assertTrue(steps.stream().anyMatch(NpcRoutineStep.LookAt.class::isInstance));
        assertTrue(steps.stream().anyMatch(NpcRoutineStep.Sweep.class::isInstance));
        assertTrue(steps.stream().anyMatch(NpcRoutineStep.Stance.class::isInstance));
        assertTrue(steps.stream().anyMatch(NpcRoutineStep.Equip.class::isInstance));
        assertTrue(steps.stream().anyMatch(NpcRoutineStep.EquipOneOf.class::isInstance));
        assertTrue(steps.stream().anyMatch(NpcRoutineStep.Clear.class::isInstance));
        assertTrue(steps.stream().anyMatch(NpcRoutineStep.Swing.class::isInstance));
        assertTrue(steps.stream().anyMatch(NpcRoutineStep.UseItem.class::isInstance));
        assertTrue(steps.stream().anyMatch(NpcRoutineStep.Sound.class::isInstance));
        assertTrue(steps.stream().anyMatch(NpcRoutineStep.Wait.class::isInstance));
        assertTrue(steps.stream()
                .filter(NpcRoutineStep.Gesture.class::isInstance)
                .map(NpcRoutineStep.Gesture.class::cast)
                .anyMatch(gesture -> gesture.sound().isPresent()));
    }

    @Test
    void voicesIncludeSilentCustomAndVariedDeliveryStyles() {
        assertTrue(catalog.profile(NpcDioramaCatalog.SHELVER).voice().sounds().silent());
        assertTrue(catalog.profile(NpcDioramaCatalog.CATALOGUER).voice().sounds().variants().size() > 1);
        Set<NpcVoiceDeliveryStyle> styles = catalog.profiles().values().stream()
                .map(profile -> profile.voice().deliveryStyle())
                .collect(Collectors.toSet());
        assertTrue(styles.containsAll(Set.of(
                NpcVoiceDeliveryStyle.BRIGHT,
                NpcVoiceDeliveryStyle.SOFT,
                NpcVoiceDeliveryStyle.GRUFF,
                NpcVoiceDeliveryStyle.HESITANT,
                NpcVoiceDeliveryStyle.SLEEPY
        )));
    }
}
