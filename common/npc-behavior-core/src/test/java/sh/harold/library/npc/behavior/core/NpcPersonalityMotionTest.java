package sh.harold.library.npc.behavior.core;

import org.junit.jupiter.api.Test;
import sh.harold.library.npc.behavior.NpcPersonalityPreset;
import sh.harold.library.npc.behavior.NpcPersonalityTuning;
import sh.harold.library.npc.behavior.NpcSustainMode;
import sh.harold.library.npc.behavior.NpcTimingBand;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcPersonalityMotionTest {

    @Test
    void allEightVanillaActingSignaturesRemainGoldenAndDistinct() {
        Map<NpcPersonalityPreset, String> actual = new LinkedHashMap<>();
        for (NpcPersonalityPreset preset : NpcPersonalityPreset.values()) {
            NpcPersonalityMotion.Signature value = NpcPersonalityMotion.signature(preset);
            actual.put(preset, value.headDegreesPerTick() + "/" + value.bodyDegreesPerTick()
                    + "/" + value.bodyFollowDelayTicks() + "/" + value.naturalMicroGlanceDegrees()
                    + "/" + value.acquisitionOvershootDegrees() + "/" + value.pitchBiasDegrees());
        }

        assertEquals(Map.of(
                NpcPersonalityPreset.NEUTRAL, "5.0/2.4/2/0.8/0.0/0.0",
                NpcPersonalityPreset.WARM, "8.0/3.4/0/1.4/0.0/2.0",
                NpcPersonalityPreset.CONFIDENT, "6.0/4.5/0/0.2/0.0/0.0",
                NpcPersonalityPreset.CURIOUS, "8.5/2.8/1/1.8/8.0/-2.0",
                NpcPersonalityPreset.CONFUSED, "7.5/2.0/5/4.5/11.0/0.0",
                NpcPersonalityPreset.NERVOUS, "10.0/1.8/4/3.6/3.5/1.0",
                NpcPersonalityPreset.DISTRACTED, "8.0/2.7/8/1.8/0.0/0.0",
                NpcPersonalityPreset.SLEEPY, "2.6/1.3/6/0.4/0.0/8.0"
        ), actual);
        assertEquals(8, actual.values().stream().distinct().count());
    }

    @Test
    void headBodyAndPitchAlwaysStayInsideVanillaLimits() {
        for (NpcPersonalityPreset preset : NpcPersonalityPreset.values()) {
            NpcGazeController controller = new NpcGazeController(0.0f, 0.0f);
            controller.target(new NpcAttentionStack.GazeTarget(150.0f, 90.0f), 0);
            for (int tick = 0; tick < 160; tick++) {
                NpcGazeController.State state = controller.tick(
                        tick,
                        preset,
                        new NpcPersonalityTuning(1.0, 2.0, 2.0),
                        NpcSustainMode.NATURAL
                );
                float relative = Math.abs(NpcGazeController.shortestDelta(state.bodyYaw(), state.headYaw()));
                assertTrue(relative <= NpcPersonalityMotion.MAXIMUM_HEAD_YAW_FROM_BODY + 0.001f, preset.name());
                assertTrue(state.pitch() >= NpcPersonalityMotion.MAXIMUM_UP_PITCH, preset.name());
                assertTrue(state.pitch() <= NpcPersonalityMotion.MAXIMUM_DOWN_PITCH, preset.name());
            }
        }
    }

    @Test
    void bodyBeginsFollowingOnlyBeyondThreshold() {
        NpcGazeController controller = new NpcGazeController(0.0f, 0.0f);
        controller.target(new NpcAttentionStack.GazeTarget(30.0f, 0.0f), 0);
        for (int tick = 0; tick < 20; tick++) {
            controller.tick(tick, NpcPersonalityPreset.CONFIDENT, NpcPersonalityTuning.DEFAULT, NpcSustainMode.STEADY);
        }
        assertEquals(0.0f, controller.state().bodyYaw(), 0.001f);

        controller.target(new NpcAttentionStack.GazeTarget(90.0f, 0.0f), 20);
        controller.tick(20, NpcPersonalityPreset.CONFIDENT, NpcPersonalityTuning.DEFAULT, NpcSustainMode.STEADY);
        assertNotEquals(0.0f, controller.state().bodyYaw());
    }

    @Test
    void personalityBodyLagDoesNotDelayTheHeadLedGlance() {
        NpcGazeController controller = new NpcGazeController(0.0f, 0.0f);
        controller.target(new NpcAttentionStack.GazeTarget(90.0f, 0.0f), 0);

        controller.tick(0, NpcPersonalityPreset.NERVOUS,
                NpcPersonalityTuning.DEFAULT, NpcSustainMode.STEADY);

        assertEquals(0.0f, controller.state().bodyYaw(), 0.001f,
                "the nervous body deliberately lags behind");
        assertNotEquals(0.0f, controller.state().headYaw(),
                "the glance itself begins immediately");
    }

    @Test
    void namedTimingBandsAreRandomizedThenTempoScaled() {
        NpcBehaviorRandom minimum = new FixedRandom();
        assertEquals(4, NpcPersonalityMotion.timingTicks(
                NpcTimingBand.QUICK,
                NpcPersonalityTuning.DEFAULT,
                minimum
        ));
        assertEquals(2, NpcPersonalityMotion.timingTicks(
                NpcTimingBand.QUICK,
                new NpcPersonalityTuning(1.0, 2.0, 1.0),
                minimum
        ));
        assertEquals(8, NpcPersonalityMotion.timingTicks(
                NpcTimingBand.QUICK,
                new NpcPersonalityTuning(1.0, 0.5, 1.0),
                minimum
        ));
    }

    private static final class FixedRandom implements NpcBehaviorRandom {
        @Override
        public int nextInt(int originInclusive, int boundExclusive) {
            return originInclusive;
        }

        @Override
        public double nextDouble() {
            return 0.0;
        }
    }
}
