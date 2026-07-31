package sh.harold.library.npc.behavior.core;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import sh.harold.library.entity.EntitySpec;
import sh.harold.library.entity.EntityTransform;
import sh.harold.library.entity.EntityTypes;
import sh.harold.library.entity.core.AbstractManagedEntity;
import sh.harold.library.npc.behavior.HumanoidBehaviorCapable;
import sh.harold.library.npc.behavior.NpcBehaviorProfile;
import sh.harold.library.npc.behavior.NpcBehaviorSnapshot;
import sh.harold.library.npc.behavior.NpcAttentionLease;
import sh.harold.library.npc.behavior.NpcConversationRegistration;
import sh.harold.library.npc.behavior.NpcConversationStagingMode;
import sh.harold.library.npc.behavior.NpcConversationState;
import sh.harold.library.npc.behavior.NpcConversationTopic;
import sh.harold.library.npc.behavior.NpcPlayback;
import sh.harold.library.npc.behavior.NpcRoutine;
import sh.harold.library.spatial.SpaceId;
import sh.harold.library.spatial.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardNpcConversationRegistryTest {

    private static final SpaceId SPACE = SpaceId.of("test", "world");

    @Test
    void reservesCompleteCastAndAllowsDisjointConversations() {
        Fixture fixture = new Fixture(4);
        StandardNpcConversationRegistry registry = fixture.registry;
        NpcConversationRegistration first = registry.register(topic(), List.of(fixture.entity(0), fixture.entity(1)));
        NpcConversationRegistration overlapping = registry.register(topic(), List.of(fixture.entity(1), fixture.entity(2)));
        NpcConversationRegistration disjoint = registry.register(topic(), List.of(fixture.entity(2), fixture.entity(3)));

        fixture.tick(100);

        assertEquals(NpcConversationState.ACTIVE, first.snapshot().state());
        assertEquals(NpcConversationState.WAITING, overlapping.snapshot().state());
        assertEquals(NpcConversationState.ACTIVE, disjoint.snapshot().state());
        assertEquals(4, registry.activeLockCount());
    }

    @Test
    void fiveTurnConversationUsesShuffleBagAndCoolsDownAfterBubbleBreaths() {
        Fixture fixture = new Fixture(2);
        NpcConversationRegistration registration = fixture.registry.register(
                new NpcConversationTopic(
                        Key.key("test", "lines"),
                        List.of(Component.text("a"), Component.text("b"), Component.text("c")),
                        List.of()
                ),
                fixture.entities
        );

        fixture.tick(100);
        List<Component> completedLines = new ArrayList<>();
        List<UUID> speakers = new ArrayList<>();
        List<NpcConversationStagingMode> stagingModes = new ArrayList<>();
        Component last = null;
        for (int tick = 101; tick < 1_000; tick++) {
            fixture.tick(tick);
            Component current = registration.snapshot().currentLine().orElse(null);
            if (current != null && current != last) {
                completedLines.add(current);
                speakers.add(registration.snapshot().speaker().orElseThrow());
                stagingModes.add(registration.snapshot().stagingMode().orElseThrow());
                last = current;
            }
            if (registration.snapshot().state() == NpcConversationState.COOLDOWN) {
                break;
            }
        }

        assertEquals(NpcConversationState.COOLDOWN, registration.snapshot().state());
        assertEquals(5, registration.snapshot().completedTurns());
        assertEquals(5, registration.snapshot().plannedTurns());
        assertEquals(5, completedLines.size());
        assertEquals(3, completedLines.subList(0, 3).stream().distinct().count(),
                "no topic line repeats before the shuffle bag is exhausted");
        assertEquals(1, speakers.stream().distinct().count(),
                "uniform selection permits the same speaker on consecutive turns");
        assertEquals(4, stagingModes.subList(0, 4).stream().distinct().count(),
                "the staging shuffle bag exercises every mode before reuse");
        assertEquals(0, fixture.registry.activeLockCount());
    }

    @Test
    void activeConversationFinishesAfterEveryTrackingViewerLeaves() {
        Fixture fixture = new Fixture(2);
        NpcConversationRegistration registration = fixture.registry.register(topic(), fixture.entities);
        fixture.tick(100);
        assertEquals(NpcConversationState.ACTIVE, registration.snapshot().state());

        fixture.actors.forEach(actor -> actor.updateActorView(actor.position(), Optional.of(SPACE), 0));
        for (int tick = 101; tick < 1_000 && registration.snapshot().state() != NpcConversationState.COOLDOWN;
             tick++) {
            fixture.tick(tick);
        }

        assertEquals(NpcConversationState.COOLDOWN, registration.snapshot().state());
        assertEquals(registration.snapshot().plannedTurns(), registration.snapshot().completedTurns());
        assertEquals(0, fixture.registry.activeLockCount());
    }

    @Test
    void directInteractionKeepsSharedConversationActiveAndRepliesToOnlyThatViewer() {
        Fixture fixture = new Fixture(3);
        NpcConversationRegistration registration = fixture.registry.register(topic(), fixture.entities);
        fixture.tick(100);
        fixture.tick(101);
        UUID viewer = new UUID(0L, 999L);
        NpcBehaviorActor clicked = fixture.actors.get(0);
        clicked.observeViewer(new NpcAttentionStack.Observation(
                viewer,
                true,
                true,
                1.0,
                0.0,
                true,
                new NpcAttentionStack.GazeTarget(0.0f, 0.0f)
        ));
        clicked.observeInteraction(viewer, sh.harold.library.entity.EntityInteractionAction.USE);

        fixture.tick(102);

        assertEquals(NpcConversationState.ACTIVE, registration.snapshot().state());
        assertEquals(3, fixture.registry.activeLockCount());
        assertTrue(fixture.ports.get(0).virtualBubbleViewers.contains(viewer));
        assertTrue(fixture.ports.get(0).sharedBubbles.stream().anyMatch(
                bubble -> bubble.kind() == NpcBubbleFrame.Kind.CONVERSATION
                        && bubble.excludedViewers().contains(viewer)
        ));
        assertEquals(0, fixture.ports.get(1).interruptionBubbles);
        assertEquals(0, fixture.ports.get(2).interruptionBubbles);
    }

    @Test
    void validatesUniqueConfiguredMannequinsWithoutMaximumCastSize() {
        Fixture fixture = new Fixture(20);
        assertThrows(IllegalArgumentException.class, () -> fixture.registry.register(
                topic(),
                List.of(fixture.entity(0), fixture.entity(0))
        ));
        NpcConversationRegistration registration = fixture.registry.register(topic(), fixture.entities);
        fixture.tick(100);
        assertEquals(20, registration.cast().size());
        assertEquals(NpcConversationState.ACTIVE, registration.snapshot().state());
    }

    @Test
    void platformCapabilityWrapperImplementingBothContractsIsAccepted() {
        Fixture fixture = new Fixture(2);
        List<TestEntity> wrapped = fixture.actors.stream()
                .map(actor -> new TestEntity(new DelegatingCapability(actor)))
                .toList();

        NpcConversationRegistration registration = fixture.registry.register(topic(), wrapped);
        fixture.tick(100);

        assertEquals(NpcConversationState.ACTIVE, registration.snapshot().state());
        assertEquals(2, fixture.registry.activeLockCount());
    }

    @Test
    void profileReplacementEndsActiveConversationAndReleasesEveryLock() {
        Fixture fixture = new Fixture(3);
        NpcConversationRegistration registration = fixture.registry.register(topic(), fixture.entities);
        fixture.tick(100);
        assertEquals(NpcConversationState.ACTIVE, registration.snapshot().state());

        fixture.actors.get(1).configure(NpcBehaviorProfile.builder()
                .interactionLine(Component.text("replacement"))
                .build());
        fixture.tick(101);

        assertEquals(NpcConversationState.COOLDOWN, registration.snapshot().state());
        assertEquals(0, fixture.registry.activeLockCount());
        fixture.tick(102);
    }

    @Test
    void unregisterAfterInteractionDoesNotDelayThePrivateReply() {
        Fixture fixture = new Fixture(2);
        NpcConversationRegistration registration = fixture.registry.register(topic(), fixture.entities);
        fixture.tick(100);
        UUID viewer = new UUID(0L, 404L);
        NpcBehaviorActor clicked = fixture.actors.get(0);
        clicked.observeViewer(new NpcAttentionStack.Observation(
                viewer, true, true, 1.0, 0.0, true,
                new NpcAttentionStack.GazeTarget(0.0f, 0.0f)
        ));
        clicked.observeInteraction(viewer, sh.harold.library.entity.EntityInteractionAction.USE);
        clicked.tick(101);

        assertTrue(fixture.ports.get(0).virtualBubbleViewers.contains(viewer));
        assertEquals(NpcConversationState.ACTIVE, registration.snapshot().state());
        registration.unregister();
        fixture.registry.tick(101);
        clicked.tick(102);

        assertEquals(0, fixture.registry.activeLockCount());
    }

    @Test
    void passiveListenersRemainStagedTowardTheSpeakerForTheWholeTurn() {
        Fixture fixture = new Fixture(3);
        List<DelegatingCapability> capabilities = fixture.actors.stream()
                .map(DelegatingCapability::new)
                .toList();
        List<TestEntity> wrapped = capabilities.stream()
                .map(TestEntity::new)
                .toList();
        NpcConversationRegistration registration = fixture.registry.register(topic(), wrapped);

        boolean reachedPassiveTurn = false;
        for (int tick = 100; tick < 1_000; tick++) {
            fixture.tick(tick);
            if (registration.snapshot().stagingMode()
                    .filter(mode -> mode == NpcConversationStagingMode.SPEAKER_FOCUSED_PASSIVE_LISTENERS)
                    .isPresent()) {
                reachedPassiveTurn = true;
                break;
            }
        }

        assertTrue(reachedPassiveTurn);
        assertEquals(
                NpcConversationStagingMode.SPEAKER_FOCUSED_PASSIVE_LISTENERS,
                capabilities.get(1).conversationStages.getLast()
        );
        assertEquals(
                NpcConversationStagingMode.SPEAKER_FOCUSED_PASSIVE_LISTENERS,
                capabilities.get(2).conversationStages.getLast()
        );
    }

    @Test
    void directInteractionDoesNotTriggerAConversationInterruptionCascade() {
        Fixture fixture = new Fixture(2);
        NpcConversationRegistration registration = fixture.registry.register(topic(), fixture.entities);
        fixture.tick(101);

        UUID viewer = new UUID(0L, 505L);
        NpcBehaviorActor clicked = fixture.actors.get(0);
        clicked.observeViewer(observation(viewer));
        clicked.observeInteraction(viewer, sh.harold.library.entity.EntityInteractionAction.USE);
        fixture.tick(102);

        assertEquals(NpcConversationState.ACTIVE, registration.snapshot().state());
        assertEquals(2, fixture.registry.activeLockCount());
        assertEquals(0, fixture.ports.get(0).interruptionBubbles);
        assertEquals(0, fixture.ports.get(1).interruptionBubbles);
    }

    @Test
    void concurrentPlayerInteractionsRemainIndependent() {
        Fixture fixture = new Fixture(3);
        NpcConversationRegistration registration = fixture.registry.register(topic(), fixture.entities);
        fixture.tick(100);
        UUID firstViewer = new UUID(0L, 601L);
        UUID secondViewer = new UUID(0L, 602L);
        fixture.actors.get(0).observeViewer(observation(firstViewer));
        fixture.actors.get(0).observeInteraction(firstViewer, sh.harold.library.entity.EntityInteractionAction.USE);
        fixture.actors.get(1).observeViewer(observation(secondViewer));
        fixture.actors.get(1).observeInteraction(secondViewer, sh.harold.library.entity.EntityInteractionAction.ATTACK);

        fixture.tick(101);

        assertEquals(NpcConversationState.ACTIVE, registration.snapshot().state());
        assertEquals(3, fixture.registry.activeLockCount());
        assertTrue(fixture.ports.get(0).virtualBubbleViewers.contains(firstViewer));
        assertTrue(fixture.ports.get(1).virtualBubbleViewers.contains(secondViewer));
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

    private static NpcConversationTopic topic() {
        return new NpcConversationTopic(
                Key.key("test", "topic"),
                List.of(Component.text("one"), Component.text("two")),
                List.of(Component.text("hey!"))
        );
    }

    private static final class Fixture {
        private final StandardNpcConversationRegistry registry = new StandardNpcConversationRegistry(
                () -> 0L,
                new MinimumRandom()
        );
        private final List<NpcBehaviorActor> actors = new ArrayList<>();
        private final List<RecordingPort> ports = new ArrayList<>();
        private final List<TestEntity> entities = new ArrayList<>();

        private Fixture(int count) {
            for (int index = 0; index < count; index++) {
                RecordingPort port = new RecordingPort();
                NpcBehaviorActor actor = new NpcBehaviorActor(
                        new UUID(1L, index + 1L),
                        0.0f,
                        0.0f,
                        port,
                        () -> 0L,
                        new MinimumRandom()
                );
                actor.updateActorView(new Vec3(index * 2.0, 0.0, 0.0), Optional.of(SPACE), 1);
                actor.configure(NpcBehaviorProfile.builder()
                        .interactionLine(Component.text("hello"))
                        .conversationInterruptionLine(Component.text("what?"))
                        .build());
                actor.tick(0);
                actors.add(actor);
                ports.add(port);
                entities.add(new TestEntity(actor));
            }
        }

        private TestEntity entity(int index) {
            return entities.get(index);
        }

        private void tick(int tick) {
            registry.tick(tick);
            actors.forEach(actor -> actor.tick(tick));
            // Completion callbacks enqueue coordinator work from actor ticks.
            registry.tick(tick);
        }
    }

    private static final class TestEntity extends AbstractManagedEntity {
        private TestEntity(NpcBehaviorActor actor) {
            this(actor.actorId(), actor);
        }

        private TestEntity(DelegatingCapability capability) {
            this(capability.actorId(), capability);
        }

        private TestEntity(UUID id, HumanoidBehaviorCapable capability) {
            super(id, EntitySpec.builder(EntityTypes.PLAYER_LIKE_HUMANOID).build());
            registerCapability(HumanoidBehaviorCapable.class, capability);
        }

        @Override protected void doTeleport(EntityTransform transform) { }
        @Override protected void doCustomName(Component customName) { }
        @Override protected void doClearCustomName() { }
        @Override protected void doCustomNameVisible(boolean visible) { }
        @Override protected void doGlowing(boolean glowing) { }
        @Override protected void doSilent(boolean silent) { }
        @Override protected void doGravity(boolean gravity) { }
        @Override protected void doInvulnerable(boolean invulnerable) { }
        @Override protected void doDespawn() { }
    }

    private static final class DelegatingCapability implements HumanoidBehaviorCapable, NpcConversationParticipant {
        private final NpcBehaviorActor actor;
        private final List<NpcConversationStagingMode> conversationStages = new ArrayList<>();

        private DelegatingCapability(NpcBehaviorActor actor) {
            this.actor = actor;
        }

        @Override public Optional<NpcBehaviorProfile> profile() { return actor.profile(); }
        @Override public CompletionStage<Void> configure(NpcBehaviorProfile profile) { return actor.configure(profile); }
        @Override public CompletionStage<Void> disable() { return actor.disable(); }
        @Override public NpcPlayback speak(Component text) { return actor.speak(text); }
        @Override public NpcPlayback speakNow(Component text) { return actor.speakNow(text); }
        @Override public void clearSpeech() { actor.clearSpeech(); }
        @Override public NpcPlayback perform(NpcRoutine routine) { return actor.perform(routine); }
        @Override public NpcAttentionLease attendTo(UUID viewerId) { return actor.attendTo(viewerId); }
        @Override public NpcBehaviorSnapshot snapshot() { return actor.snapshot(); }
        @Override public UUID actorId() { return actor.actorId(); }
        @Override public boolean configured() { return actor.configured(); }
        @Override public boolean atCleanupCheckpoint() { return actor.atCleanupCheckpoint(); }
        @Override public int trackingViewerCount() { return actor.trackingViewerCount(); }
        @Override public Optional<SpaceId> spaceId() { return actor.spaceId(); }
        @Override public Vec3 position() { return actor.position(); }
        @Override public boolean tryReserveConversation(UUID registrationId) {
            return actor.tryReserveConversation(registrationId);
        }
        @Override public boolean conversationReservedBy(UUID registrationId) {
            return actor.conversationReservedBy(registrationId);
        }
        @Override public void conversationInterruption(boolean active) { actor.conversationInterruption(active); }
        @Override public void releaseConversation(UUID registrationId) { actor.releaseConversation(registrationId); }
        @Override public NpcPlayback speakConversation(Component line, boolean interruption) {
            return actor.speakConversation(line, interruption);
        }
        @Override public void clearConversationSpeech() { actor.clearConversationSpeech(); }
        @Override public List<Component> interruptionLines(List<Component> generic) {
            return actor.interruptionLines(generic);
        }
        @Override public void finishDeferredInteraction(UUID viewerId) { actor.finishDeferredInteraction(viewerId); }
        @Override public AutoCloseable beginInterruptionBarrier() { return actor.beginInterruptionBarrier(); }
        @Override public void stageConversation(NpcConversationStagingMode mode, Vec3 focus, boolean selected) {
            conversationStages.add(mode);
            actor.stageConversation(mode, focus, selected);
        }
        @Override public void clearConversationStage() { actor.clearConversationStage(); }
        @Override public void reactToInterruption() { actor.reactToInterruption(); }
        @Override public void interactionRouter(NpcBehaviorActor.InteractionRouter router) {
            actor.interactionRouter(router);
        }
    }

    private static final class RecordingPort implements NpcBehaviorRenderPort {
        private int interruptionBubbles;
        private final List<NpcBubbleFrame> sharedBubbles = new ArrayList<>();
        private final List<UUID> virtualBubbleViewers = new ArrayList<>();

        @Override
        public void showSharedBubble(NpcBubbleFrame bubble) {
            sharedBubbles.add(bubble);
            if (bubble.kind() == NpcBubbleFrame.Kind.INTERRUPTION) {
                interruptionBubbles++;
            }
        }

        @Override
        public void showVirtualBubble(UUID viewerId, NpcBubbleFrame bubble) {
            virtualBubbleViewers.add(viewerId);
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
