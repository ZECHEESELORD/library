package sh.harold.library.npc.behavior.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcAttentionStackTest {

    private static final UUID P1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID P2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID P3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void zeroVerticalToleranceIsAValidExactHeightPolicy() {
        assertDoesNotThrow(() -> new NpcAttentionStack.Policy(6.0, 8.0, 0.0, true, true, 3));
    }

    @Test
    void p1P2P3AcquireOverlayAndFallBackInPromotionOrder() {
        NpcAttentionStack stack = new NpcAttentionStack(NpcAttentionStack.Policy.defaults());

        stack.observe(visible(P1, 1.0, 10.0f));
        stack.observe(visible(P2, 2.0, 20.0f));
        stack.observe(visible(P3, 3.0, 30.0f));

        assertEquals(P3, stack.snapshot().canonicalViewer().orElseThrow());
        assertEquals(List.of(P1, P2, P3), stack.snapshot().sessions().stream()
                .map(NpcAttentionStack.Session::viewerId)
                .toList());
        assertEquals(Set.of(P1, P2), stack.snapshot().overlayViewers());

        stack.retire(P3, NpcAttentionStack.ReleaseReason.EXIT_BOUNDARY);

        assertEquals(P2, stack.snapshot().canonicalViewer().orElseThrow());
        assertEquals(Set.of(P1), stack.snapshot().overlayViewers());
        assertTrue(stack.drainEvents().stream().anyMatch(event ->
                event.type() == NpcAttentionStack.EventType.CANONICAL_CHANGED
                        && P2.equals(event.viewerId())
                        && event.acquisitionReason() == NpcAttentionStack.AcquisitionReason.FALLBACK
        ));
    }

    @Test
    void lineOfSightUsesThreeFailureHysteresisAndRadiusUsesEnterExitHysteresis() {
        NpcAttentionStack stack = new NpcAttentionStack(NpcAttentionStack.Policy.defaults());
        stack.observe(visible(P1, 5.9, 0.0f));
        assertEquals(1, stack.size());

        stack.observe(observation(P1, 7.5, true));
        assertEquals(1, stack.size(), "session remains between enter and exit radii");
        stack.observe(observation(P1, 7.5, false));
        stack.observe(observation(P1, 7.5, false));
        assertEquals(1, stack.size());
        stack.observe(observation(P1, 7.5, false));
        assertEquals(0, stack.size());

        stack.observe(visible(P1, 6.5, 0.0f));
        assertEquals(0, stack.size(), "new sessions require the enter radius");
    }

    @Test
    void cachedLineOfSightResultsNeitherAcquireNorAdvanceFailureHysteresis() {
        NpcAttentionStack stack = new NpcAttentionStack(NpcAttentionStack.Policy.defaults());

        stack.observe(visible(P1, 2.0, 0.0f), false);
        assertEquals(0, stack.size(), "acquisition waits for a newly completed successful probe");
        stack.observe(visible(P1, 2.0, 0.0f), true);
        assertEquals(1, stack.size());

        stack.observe(observation(P1, 2.0, false), true);
        for (int tick = 0; tick < 20; tick++) {
            stack.observe(observation(P1, 2.0, false), false);
        }
        assertEquals(1, stack.size(), "a cached failure is counted exactly once");

        stack.observe(observation(P1, 2.0, false), true);
        assertEquals(1, stack.size());
        stack.observe(observation(P1, 2.0, false), true);
        assertEquals(0, stack.size());
    }

    @Test
    void acknowledgementLatchClearsOnlyAfterExitAndReentry() {
        NpcAttentionStack stack = new NpcAttentionStack(NpcAttentionStack.Policy.defaults());
        stack.observe(visible(P1, 2.0, 0.0f));
        assertTrue(stack.latchAcknowledgement(P1));
        assertFalse(stack.latchAcknowledgement(P1));

        stack.retire(P1, NpcAttentionStack.ReleaseReason.EXIT_BOUNDARY);
        stack.observe(visible(P1, 2.0, 0.0f));
        assertTrue(stack.latchAcknowledgement(P1));
    }

    @Test
    void leasesAreTokenSafeAndPromoteLikeNaturalAttention() {
        NpcAttentionStack stack = new NpcAttentionStack(NpcAttentionStack.Policy.defaults());
        stack.observe(visible(P1, 2.0, 0.0f));
        NpcAttentionStack.Lease first = stack.lease(P2, target(90.0f));
        NpcAttentionStack.Lease second = stack.lease(P2, target(90.0f));

        assertEquals(P2, stack.snapshot().canonicalViewer().orElseThrow());
        assertEquals(2, stack.snapshot().session(P2).orElseThrow().leaseCount());
        first.close();
        first.close();
        assertEquals(1, stack.snapshot().session(P2).orElseThrow().leaseCount());
        second.close();

        assertEquals(P1, stack.snapshot().canonicalViewer().orElseThrow());
    }

    @Test
    void interactionRefreshPromotesWithoutReorderingOlderSnapshotEntries() {
        NpcAttentionStack stack = new NpcAttentionStack(NpcAttentionStack.Policy.defaults());
        stack.observe(visible(P1, 2.0, 0.0f));
        stack.observe(visible(P2, 2.0, 30.0f));
        stack.interaction(P1, target(45.0f));

        assertEquals(P1, stack.snapshot().canonicalViewer().orElseThrow());
        assertEquals(List.of(P2, P1), stack.snapshot().sessions().stream()
                .map(NpcAttentionStack.Session::viewerId)
                .toList(), "snapshot stack is promotion order, oldest to newest");
    }

    private static NpcAttentionStack.Observation visible(UUID viewer, double distance, float yaw) {
        return new NpcAttentionStack.Observation(viewer, true, true, distance * distance, 0.0, true, target(yaw));
    }

    private static NpcAttentionStack.Observation observation(UUID viewer, double distance, boolean lineOfSight) {
        return new NpcAttentionStack.Observation(
                viewer,
                true,
                true,
                distance * distance,
                0.0,
                lineOfSight,
                target(0.0f)
        );
    }

    private static NpcAttentionStack.GazeTarget target(float yaw) {
        return new NpcAttentionStack.GazeTarget(yaw, 0.0f);
    }
}
