package sh.harold.library.npc.behavior.core;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import sh.harold.library.entity.EntityInteractionAction;
import sh.harold.library.npc.behavior.NpcAcknowledgementSpec;
import sh.harold.library.npc.behavior.NpcAttentionResponse;
import sh.harold.library.npc.behavior.NpcAttentionSpec;
import sh.harold.library.npc.behavior.NpcBehaviorProfile;
import sh.harold.library.npc.behavior.NpcGesturePreset;
import sh.harold.library.npc.behavior.NpcPersonalityPreset;
import sh.harold.library.npc.behavior.NpcRoutine;
import sh.harold.library.npc.behavior.NpcSustainMode;
import sh.harold.library.npc.behavior.NpcTimingBand;
import sh.harold.library.npc.behavior.NpcVoiceProfiles;
import sh.harold.library.spatial.SpaceId;
import sh.harold.library.spatial.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcBehaviorActorTest {

    private static final UUID NPC = uuid(100);
    private static final UUID P1 = uuid(1);
    private static final UUID P2 = uuid(2);

    @Test
    void unconfiguredActorIsInertAndExplicitCommandsFailFast() {
        NpcBehaviorActor actor = new NpcBehaviorActor(NPC, 0.0f, 0.0f, new RecordingPort());

        actor.tick(0);

        assertFalse(actor.snapshot().configured());
        assertThrows(IllegalStateException.class, () -> actor.speak(Component.text("no")));
        assertThrows(IllegalStateException.class, () -> actor.attendTo(P1));
    }

    @Test
    void configurationAcquisitionOverlayFallbackAndDisableAreAtomicOnActorTicks() {
        RecordingPort port = new RecordingPort();
        NpcBehaviorActor actor = actor(port);
        NpcBehaviorProfile profile = profile();

        var configured = actor.configure(profile);
        assertFalse(actor.profile().isPresent());
        actor.tick(0);
        assertTrue(configured.toCompletableFuture().isDone());
        assertEquals(profile, actor.profile().orElseThrow());

        actor.observeViewer(visible(P1, 0.0f));
        actor.tick(1);
        assertEquals(P1, actor.snapshot().canonicalTarget().orElseThrow());
        actor.observeViewer(visible(P2, 90.0f));
        actor.tick(2);

        assertEquals(P2, actor.snapshot().canonicalTarget().orElseThrow());
        assertEquals(List.of(P1, P2), actor.snapshot().acquisitionStack());
        assertTrue(port.viewerFrames.containsKey(P1));
        assertTrue(port.viewerFrames.containsKey(P2));

        actor.removeViewer(P2, NpcAttentionStack.ReleaseReason.UNTRACKED);
        actor.tick(3);
        assertEquals(P1, actor.snapshot().canonicalTarget().orElseThrow());
        assertTrue(port.clearedOverlays.contains(P2));
        assertTrue(port.viewerFrames.containsKey(P1));

        var disabled = actor.disable();
        actor.tick(4);
        assertTrue(disabled.toCompletableFuture().isDone());
        assertFalse(actor.snapshot().configured());
        assertTrue(port.restoreCount >= 2);
    }

    @Test
    void directUseAndAttackShareBehaviorWhileActionRemainsAvailableToPlatform() {
        RecordingPort port = new RecordingPort();
        NpcBehaviorActor actor = actor(port);
        actor.configure(profile()).toCompletableFuture();
        actor.tick(0);
        actor.observeViewer(visible(P1, 30.0f));
        actor.tick(1);

        actor.observeInteraction(P1, EntityInteractionAction.USE);
        actor.tick(2);
        actor.observeInteraction(P1, EntityInteractionAction.ATTACK);
        actor.tick(3);

        assertEquals(P1, actor.snapshot().canonicalTarget().orElseThrow());
        assertEquals(2, port.sharedBubbles.stream()
                .filter(bubble -> bubble.kind() == NpcBubbleFrame.Kind.ATTENTION)
                .count());
        assertTrue(actor.snapshot().visibleSpeech().isPresent(),
                "the public snapshot includes the active attention bark");
    }

    @Test
    void everyAcquiredViewerOwnsAnIndependentFocusAndAcknowledgementBranch() {
        RecordingPort port = new RecordingPort();
        NpcBehaviorActor actor = actor(port);
        NpcAttentionSpec attention = NpcAttentionSpec.builder()
                .idleResponse(NpcAttentionResponse.acknowledge(
                        NpcAcknowledgementSpec.gestures(NpcGesturePreset.WAVE)
                ))
                .build();
        actor.configure(NpcBehaviorProfile.builder().attention(attention).build());
        actor.tick(0);

        actor.observeViewer(visible(P1, 0.0f));
        actor.tick(1);
        actor.observeViewer(visible(P2, 45.0f));
        actor.tick(2);

        assertEquals(List.of(P1, P2), port.viewerAnimations);
        assertTrue(port.viewerFrames.containsKey(P1));
        assertTrue(port.viewerFrames.containsKey(P2));
        actor.tick(8);
        assertTrue(port.viewerFrames.containsKey(P1));
        assertTrue(port.viewerFrames.containsKey(P2));
    }

    @Test
    void proximityFocusStartsWhileAnIgnoredRoutineIsStillActive() {
        RecordingPort port = new RecordingPort();
        NpcBehaviorActor actor = actor(port);
        NpcAttentionSpec attention = NpcAttentionSpec.builder()
                .idleResponse(NpcAttentionResponse.sustain(NpcSustainMode.STEADY))
                .routineResponse(NpcAttentionResponse.ignore())
                .build();
        actor.configure(NpcBehaviorProfile.builder().attention(attention).build());
        actor.tick(0);
        NpcRoutine routine = NpcRoutine.builder(Key.key("test", "long_work"))
                .wait(NpcTimingBand.LONG)
                .build();
        actor.perform(routine);
        actor.tick(1);

        actor.observeViewer(visible(P1, 60.0f));
        actor.tick(2);

        assertEquals(routine.key(), actor.snapshot().activeRoutine().orElseThrow());
        assertTrue(port.viewerFrames.containsKey(P1),
                "the player-specific gaze must not wait for the shared routine to complete");
    }

    @Test
    void movingViewerFocusIsImmediateAndRateLimited() {
        RecordingPort port = new RecordingPort();
        NpcBehaviorActor actor = actor(port);
        actor.configure(profile());
        actor.tick(0);

        for (int tick = 1; tick <= 200; tick++) {
            actor.observeViewer(visible(P1, 90.0f + tick * 3.0f));
            actor.tick(tick);
            if (tick == 1) {
                assertEquals(List.of(P1), port.viewerFrameEvents,
                        "the first per-player focus frame must not be throttled");
            }
        }

        assertTrue(port.viewerFrameEvents.size() > 1);
        assertTrue(port.viewerFrameEvents.size() <= 67,
                "moving focus should emit at most once every three actor ticks");
    }

    @Test
    void directInteractionPairsThePersonalityGestureWithItsBark() {
        RecordingPort warmPort = new RecordingPort();
        NpcBehaviorActor warm = actor(warmPort);
        warm.configure(NpcBehaviorProfile.builder(NpcPersonalityPreset.WARM)
                .interactionLine(Component.text("welcome"))
                .build());
        warm.tick(0);
        warm.observeViewer(visible(P1, 15.0f));
        warm.observeInteraction(P1, EntityInteractionAction.USE);
        warm.tick(1);

        assertTrue(warmPort.viewerAnimationTypes.contains(NpcRenderAnimation.Type.WAVE));
        assertTrue(warmPort.sharedBubbles.stream().anyMatch(
                bubble -> bubble.kind() == NpcBubbleFrame.Kind.ATTENTION
        ));

        RecordingPort nervousPort = new RecordingPort();
        NpcBehaviorActor nervous = actor(nervousPort);
        nervous.configure(NpcBehaviorProfile.builder(NpcPersonalityPreset.NERVOUS)
                .interactionLine(Component.text("oh!"))
                .build());
        nervous.tick(0);
        nervous.observeViewer(visible(P1, 15.0f));
        nervous.observeInteraction(P1, EntityInteractionAction.ATTACK);
        nervous.tick(1);

        assertTrue(nervousPort.viewerAnimationTypes.contains(NpcRenderAnimation.Type.CROUCH_PULSE));
    }

    @Test
    void directInteractionVoiceIncludesTargetBeforeItsFirstViewerSample() {
        RecordingPort port = new RecordingPort();
        NpcBehaviorActor actor = actor(port);
        actor.configure(NpcBehaviorProfile.builder()
                .voice(NpcVoiceProfiles.WARM_VILLAGER)
                .interactionLine(Component.text("hello"))
                .build());
        actor.tick(0);

        actor.observeInteraction(P1, EntityInteractionAction.USE);
        actor.tick(1);

        assertTrue(port.sounds.stream().anyMatch(sound -> sound.recipient().filter(P1::equals).isPresent()));
    }

    @Test
    void stableComposedFramesEmitNoPacketsAndSnapshotsAreDefensive() {
        RecordingPort port = new RecordingPort();
        NpcBehaviorActor actor = actor(port);
        actor.configure(profile()).toCompletableFuture();
        actor.tick(0);
        actor.observeViewer(visible(P1, 0.0f));
        for (int tick = 1; tick < 80; tick++) {
            actor.tick(tick);
        }
        int settledCount = port.sharedFrames.size();
        for (int tick = 80; tick < 120; tick++) {
            actor.tick(tick);
        }

        assertEquals(settledCount, port.sharedFrames.size());
        assertThrows(UnsupportedOperationException.class, () -> actor.snapshot().acquisitionStack().add(P2));
    }

    @Test
    void closeCompletesQueuedOperationsAndRejectsLateViewerCallbacks() {
        RecordingPort port = new RecordingPort();
        NpcBehaviorActor actor = actor(port);
        actor.configure(profile());
        actor.tick(0);
        var speech = actor.speak(Component.text("queued"));
        var replacement = actor.configure(profile());

        actor.close();
        actor.observeViewer(visible(P1, 30.0f));

        assertTrue(speech.completion().toCompletableFuture().isDone());
        assertTrue(replacement.toCompletableFuture().isCompletedExceptionally());
        assertFalse(actor.evaluationRequired());
        assertFalse(actor.snapshot().configured());
        assertTrue(actor.snapshot().acquisitionStack().isEmpty());
    }

    @Test
    void profileReplacementRequiresAFreshViewerObservationBeforeReacquiring() {
        RecordingPort port = new RecordingPort();
        NpcBehaviorActor actor = actor(port);
        UUID viewer = new UUID(0L, 77L);
        actor.configure(profile());
        actor.tick(0);
        actor.observeViewer(visible(viewer, 25.0f));
        actor.tick(1);
        assertEquals(viewer, actor.snapshot().canonicalTarget().orElseThrow());

        actor.configure(profile());
        actor.tick(2);
        actor.tick(3);

        assertTrue(actor.snapshot().canonicalTarget().isEmpty(),
                "configure clears cached platform observations as part of the old behavior state");
        actor.observeViewer(visible(viewer, 25.0f));
        actor.tick(4);
        assertEquals(viewer, actor.snapshot().canonicalTarget().orElseThrow());
    }

    @Test
    void explicitRoutineWaitsBehindAnActiveConversationLock() {
        RecordingPort port = new RecordingPort();
        NpcBehaviorActor actor = actor(port);
        actor.configure(profile());
        actor.tick(0);
        UUID registration = new UUID(0L, 900L);
        assertTrue(actor.tryReserveConversation(registration));
        NpcRoutine routine = NpcRoutine.builder(Key.key("test", "after_conversation"))
                .wait(NpcTimingBand.QUICK)
                .build();
        var playback = actor.perform(routine);

        actor.tick(1);
        assertTrue(actor.snapshot().activeRoutine().isEmpty());
        assertFalse(playback.completion().toCompletableFuture().isDone());

        actor.releaseConversation(registration);
        actor.tick(2);
        assertEquals(routine.key(), actor.snapshot().activeRoutine().orElseThrow());
    }

    private static NpcBehaviorActor actor(RecordingPort port) {
        NpcBehaviorActor actor = new NpcBehaviorActor(
                NPC,
                0.0f,
                0.0f,
                port,
                () -> 0L,
                new MinimumRandom()
        );
        actor.updateActorView(Vec3.ZERO, Optional.of(SpaceId.of("test", "world")), 2);
        return actor;
    }

    private static NpcBehaviorProfile profile() {
        NpcAttentionSpec attention = NpcAttentionSpec.builder()
                .idleResponse(NpcAttentionResponse.sustain(NpcSustainMode.STEADY))
                .build();
        return NpcBehaviorProfile.builder()
                .attention(attention)
                .interactionLine(Component.text("hello"))
                .build();
    }

    private static NpcAttentionStack.Observation visible(UUID viewer, float yaw) {
        return new NpcAttentionStack.Observation(
                viewer,
                true,
                true,
                4.0,
                0.0,
                true,
                new NpcAttentionStack.GazeTarget(yaw, 0.0f)
        );
    }

    private static UUID uuid(long value) {
        return new UUID(0L, value);
    }

    private static final class RecordingPort implements NpcBehaviorRenderPort {
        private final List<NpcRenderFrame> sharedFrames = new ArrayList<>();
        private final Map<UUID, NpcRenderFrame> viewerFrames = new LinkedHashMap<>();
        private final List<UUID> viewerFrameEvents = new ArrayList<>();
        private final List<UUID> clearedOverlays = new ArrayList<>();
        private final List<NpcBubbleFrame> sharedBubbles = new ArrayList<>();
        private final List<UUID> viewerAnimations = new ArrayList<>();
        private final List<NpcRenderAnimation.Type> viewerAnimationTypes = new ArrayList<>();
        private final List<NpcRenderedSound> sounds = new ArrayList<>();
        private int restoreCount;

        @Override
        public java.util.concurrent.CompletionStage<Void> restoreNativePresentation() {
            restoreCount++;
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public void renderSharedFrame(NpcRenderFrame frame) {
            sharedFrames.add(frame);
        }

        @Override
        public void renderViewerOverlay(UUID viewerId, NpcRenderFrame frame) {
            viewerFrames.put(viewerId, frame);
            viewerFrameEvents.add(viewerId);
        }

        @Override
        public void clearViewerOverlay(UUID viewerId, NpcRenderFrame nativeFrame) {
            viewerFrames.remove(viewerId);
            clearedOverlays.add(viewerId);
        }

        @Override
        public void showSharedBubble(NpcBubbleFrame bubble) {
            sharedBubbles.add(bubble);
        }

        @Override
        public void animateViewer(UUID viewerId, NpcRenderAnimation animation) {
            viewerAnimations.add(viewerId);
            viewerAnimationTypes.add(animation.type());
        }

        @Override
        public void playSound(NpcRenderedSound sound) {
            sounds.add(sound);
        }
    }

    private static final class MinimumRandom implements NpcBehaviorRandom {
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
