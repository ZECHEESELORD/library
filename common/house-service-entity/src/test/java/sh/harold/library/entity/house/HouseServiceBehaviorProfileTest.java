package sh.harold.library.entity.house;

import org.junit.jupiter.api.Test;
import sh.harold.library.entity.EntitySpec;
import sh.harold.library.entity.EntityTypes;
import sh.harold.library.npc.behavior.NpcBehaviorProfile;
import sh.harold.library.npc.behavior.NpcPersonalityPreset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseServiceBehaviorProfileTest {

    @Test
    void omissionPreservesMotionlessServices() {
        HouseServiceSpec spec = HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.VILLAGER).build()).build();

        assertTrue(spec.behaviorProfile().isEmpty());
    }

    @Test
    void playerLikeHumanoidMayDeclareBehavior() {
        NpcBehaviorProfile profile = NpcBehaviorProfile.builder(NpcPersonalityPreset.CURIOUS).build();

        HouseServiceSpec spec = HouseServiceSpec.builder(
                        EntitySpec.builder(EntityTypes.PLAYER_LIKE_HUMANOID).build()
                )
                .behaviorProfile(profile)
                .build();

        assertEquals(profile, spec.behaviorProfile().orElseThrow());
    }

    @Test
    void behaviorOnNonHumanoidFailsAtAuthoringTime() {
        NpcBehaviorProfile profile = NpcBehaviorProfile.builder().build();

        assertThrows(IllegalArgumentException.class, () -> HouseServiceSpec.builder(
                        EntitySpec.builder(EntityTypes.VILLAGER).build()
                )
                .behaviorProfile(profile)
                .build());
    }
}
