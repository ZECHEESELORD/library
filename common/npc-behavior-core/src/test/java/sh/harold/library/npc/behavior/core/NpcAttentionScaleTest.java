package sh.harold.library.npc.behavior.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcAttentionScaleTest {

    @Test
    void tenThousandDenseSessionsRemainBoundedForTwelveThousandTicksAndRetireToZero() {
        List<NpcAttentionStack> actors = new ArrayList<>(100);
        List<List<UUID>> viewers = new ArrayList<>(100);
        List<List<NpcAttentionStack.Observation>> observations = new ArrayList<>(100);
        for (int npc = 0; npc < 100; npc++) {
            NpcAttentionStack stack = new NpcAttentionStack(NpcAttentionStack.Policy.defaults());
            List<UUID> actorViewers = new ArrayList<>(100);
            List<NpcAttentionStack.Observation> actorObservations = new ArrayList<>(100);
            for (int player = 0; player < 100; player++) {
                UUID viewer = new UUID(npc + 1L, player + 1L);
                actorViewers.add(viewer);
                NpcAttentionStack.Observation observation = observation(viewer);
                actorObservations.add(observation);
                stack.observe(observation);
            }
            actors.add(stack);
            viewers.add(actorViewers);
            observations.add(actorObservations);
        }
        actors.forEach(NpcAttentionStack::drainEvents);

        for (int tick = 0; tick < 12_000; tick++) {
            for (int actorIndex = 0; actorIndex < actors.size(); actorIndex++) {
                NpcAttentionStack stack = actors.get(actorIndex);
                for (NpcAttentionStack.Observation observation : observations.get(actorIndex)) {
                    stack.observe(observation);
                }
            }
        }

        assertEquals(10_000, actors.stream().mapToInt(NpcAttentionStack::size).sum());
        for (int actor = 0; actor < actors.size(); actor++) {
            NpcAttentionStack stack = actors.get(actor);
            for (UUID viewer : viewers.get(actor)) {
                stack.retire(viewer, NpcAttentionStack.ReleaseReason.UNTRACKED);
            }
        }
        assertEquals(0, actors.stream().mapToInt(NpcAttentionStack::size).sum());
        actors.forEach(stack -> {
            stack.drainEvents();
            assertEquals(List.of(), stack.drainEvents(), "retirement leaves no queued attention events");
        });
    }

    private static NpcAttentionStack.Observation observation(UUID viewer) {
        return new NpcAttentionStack.Observation(
                viewer,
                true,
                true,
                1.0,
                0.0,
                true,
                new NpcAttentionStack.GazeTarget(0.0f, 0.0f)
        );
    }
}
