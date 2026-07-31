package sh.harold.library.npc.behavior.core;

import net.kyori.adventure.text.Component;
import sh.harold.library.entity.EntityInteractionAction;
import sh.harold.library.npc.behavior.HumanoidBehaviorCapable;
import sh.harold.library.npc.behavior.NpcAcknowledgementSpec;
import sh.harold.library.npc.behavior.NpcAttentionActivity;
import sh.harold.library.npc.behavior.NpcAttentionLease;
import sh.harold.library.npc.behavior.NpcAttentionResponse;
import sh.harold.library.npc.behavior.NpcBehaviorActivity;
import sh.harold.library.npc.behavior.NpcBehaviorProfile;
import sh.harold.library.npc.behavior.NpcBehaviorSnapshot;
import sh.harold.library.npc.behavior.NpcConversationStagingMode;
import sh.harold.library.npc.behavior.NpcGesturePreset;
import sh.harold.library.npc.behavior.NpcIdleEntry;
import sh.harold.library.npc.behavior.NpcPlayback;
import sh.harold.library.npc.behavior.NpcRoutine;
import sh.harold.library.npc.behavior.NpcSustainMode;
import sh.harold.library.npc.behavior.NpcVoiceProfile;
import sh.harold.library.spatial.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Platform-neutral actor runtime and the implementation of
 * {@link HumanoidBehaviorCapable}. All public operations are thread-safe;
 * rendering occurs only from {@link #tick(long)}.
 */
public final class NpcBehaviorActor implements HumanoidBehaviorCapable, NpcConversationParticipant, AutoCloseable {

    private static final long VIEWER_OVERLAY_INTERVAL_TICKS = 3L;

    private final UUID actorId;
    private final float homeYaw;
    private final float homePitch;
    private final NpcBehaviorRenderPort renderer;
    private final NpcBehaviorClock clock;
    private final NpcBehaviorRandom random;
    private final ConcurrentLinkedQueue<Runnable> commands = new ConcurrentLinkedQueue<>();
    private final Map<UUID, NpcAttentionStack.Observation> observations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> observationLosEpochs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> evaluatedLosEpochs = new LinkedHashMap<>();
    private final Map<UUID, ViewerGaze> viewerGazes = new LinkedHashMap<>();
    private final Map<UUID, NpcGestureComposer> viewerGestures = new LinkedHashMap<>();
    private final Map<UUID, NpcRenderFrame> lastViewerFrames = new LinkedHashMap<>();
    private final Map<UUID, Long> lastViewerRenderTicks = new LinkedHashMap<>();
    private final Set<UUID> renderedOverlays = new LinkedHashSet<>();
    private final AtomicLong configurationGeneration = new AtomicLong();
    private final AtomicLong implicitObservationEpoch = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ConcurrentLinkedQueue<IdleCompletion> idleCompletions = new ConcurrentLinkedQueue<>();
    private final NpcGazeController conversationGaze;
    private final NpcGestureComposer sharedGesture = new NpcGestureComposer();
    private final NpcIdleSelector idleSelector;
    private final NpcAttentionBubbles attentionBubbles;
    private final NpcSpeechQueue speech;
    private final NpcRoutinePlayer routines;

    private volatile NpcBehaviorProfile profile;
    private volatile NpcBehaviorSnapshot snapshot = NpcBehaviorSnapshot.inert();
    private volatile NpcNativeSnapshot nativeSnapshot;
    private volatile UUID conversationLock;
    private volatile boolean conversationInterruption;
    private volatile InteractionRouter interactionRouter = InteractionRouter.NONE;
    private NpcAttentionStack attention = new NpcAttentionStack(NpcAttentionStack.Policy.defaults());
    private NpcRenderFrame lastSharedFrame;
    private NpcAttentionStack.GazeTarget conversationTarget;
    private PendingConfiguration pendingConfiguration;
    private NpcIdleEntry activeIdle;
    private NpcRoutinePlayer.Ticket activeIdleTicket;
    private long nextIdleEvaluation;
    private long revision;
    private long lastTick;

    public NpcBehaviorActor(
            UUID actorId,
            float homeYaw,
            float homePitch,
            NpcBehaviorRenderPort renderer
    ) {
        this(
                actorId,
                homeYaw,
                homePitch,
                renderer,
                new IncrementingClock(),
                NpcBehaviorRandom.from(ThreadLocalRandom.current())
        );
    }

    public NpcBehaviorActor(
            UUID actorId,
            float homeYaw,
            float homePitch,
            NpcBehaviorRenderPort renderer,
            NpcBehaviorClock clock,
            NpcBehaviorRandom random
    ) {
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        if (!Float.isFinite(homeYaw) || !Float.isFinite(homePitch)) {
            throw new IllegalArgumentException("home orientation must be finite");
        }
        this.homeYaw = homeYaw;
        this.homePitch = homePitch;
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
        this.conversationGaze = new NpcGazeController(homeYaw, homePitch);
        this.idleSelector = new NpcIdleSelector(random);
        this.attentionBubbles = new NpcAttentionBubbles(renderer, random);
        this.speech = new NpcSpeechQueue(this::showWorldBubble, renderer::clearSharedBubble);
        NpcRenderFrame home = NpcRenderFrame.standing(homeYaw, homePitch);
        this.nativeSnapshot = new NpcNativeSnapshot(Vec3.ZERO, Optional.empty(), home, 0);
        this.routines = new NpcRoutinePlayer(
                renderer,
                random,
                () -> nativeSnapshot.frame(),
                () -> nativeSnapshot.position(),
                () -> nativeSnapshot.spaceId()
        );
    }

    public UUID actorId() {
        return actorId;
    }

    @Override
    public Optional<NpcBehaviorProfile> profile() {
        return Optional.ofNullable(profile);
    }

    @Override
    public CompletionStage<Void> configure(NpcBehaviorProfile newProfile) {
        Objects.requireNonNull(newProfile, "profile");
        requireOpen();
        long generation = configurationGeneration.incrementAndGet();
        CompletableFuture<Void> completion = new CompletableFuture<>();
        if (!enqueueIfOpen(() -> beginConfiguration(generation, newProfile, completion))) {
            completion.completeExceptionally(closedFailure());
        }
        return completion;
    }

    @Override
    public CompletionStage<Void> disable() {
        requireOpen();
        long generation = configurationGeneration.incrementAndGet();
        CompletableFuture<Void> completion = new CompletableFuture<>();
        if (!enqueueIfOpen(() -> beginConfiguration(generation, null, completion))) {
            completion.completeExceptionally(closedFailure());
        }
        return completion;
    }

    @Override
    public NpcPlayback speak(Component text) {
        Objects.requireNonNull(text, "text");
        requireConfigured();
        AtomicReference<NpcSpeechQueue.Ticket> ticket = new AtomicReference<>();
        StandardNpcPlayback playback = new StandardNpcPlayback(() -> enqueueIfOpen(() -> {
            NpcSpeechQueue.Ticket current = ticket.get();
            if (current != null) {
                speech.cancel(current);
            }
        }));
        if (!enqueueIfOpen(() -> {
            if (profile == null) {
                playback.completeExceptionally(notConfigured());
                return;
            }
            NpcSpeechQueue.Ticket created = speech.append(text, NpcBubbleFrame.Kind.WORLD);
            ticket.set(created);
            playback.bind(created.completion());
        })) {
            playback.completeExceptionally(closedFailure());
        }
        return playback;
    }

    @Override
    public NpcPlayback speakNow(Component text) {
        Objects.requireNonNull(text, "text");
        requireConfigured();
        AtomicReference<NpcSpeechQueue.Ticket> ticket = new AtomicReference<>();
        StandardNpcPlayback playback = new StandardNpcPlayback(() -> enqueueIfOpen(() -> {
            NpcSpeechQueue.Ticket current = ticket.get();
            if (current != null) {
                speech.cancel(current);
            }
        }));
        if (!enqueueIfOpen(() -> {
            if (profile == null) {
                playback.completeExceptionally(notConfigured());
                return;
            }
            NpcSpeechQueue.Ticket created = speech.now(text, NpcBubbleFrame.Kind.WORLD, lastTick);
            ticket.set(created);
            playback.bind(created.completion());
        })) {
            playback.completeExceptionally(closedFailure());
        }
        return playback;
    }

    @Override
    public void clearSpeech() {
        requireConfigured();
        if (!enqueueIfOpen(() -> speech.clear(lastTick))) {
            throw closedFailure();
        }
    }

    @Override
    public NpcPlayback perform(NpcRoutine routine) {
        Objects.requireNonNull(routine, "routine");
        requireConfigured();
        AtomicReference<NpcRoutinePlayer.Ticket> ticket = new AtomicReference<>();
        StandardNpcPlayback playback = new StandardNpcPlayback(() -> enqueueIfOpen(() -> {
            NpcRoutinePlayer.Ticket current = ticket.get();
            if (current != null) {
                routines.cancel(current);
            }
        }));
        if (!enqueueIfOpen(() -> {
            if (profile == null) {
                playback.completeExceptionally(notConfigured());
                return;
            }
            NpcRoutinePlayer.Ticket created = routines.perform(routine);
            ticket.set(created);
            playback.bind(created.completion());
        })) {
            playback.completeExceptionally(closedFailure());
        }
        return playback;
    }

    @Override
    public NpcAttentionLease attendTo(UUID viewerId) {
        Objects.requireNonNull(viewerId, "viewerId");
        requireConfigured();
        AtomicReference<NpcAttentionStack.Lease> internal = new AtomicReference<>();
        AtomicBoolean requestedClose = new AtomicBoolean();
        StandardNpcAttentionLease lease = new StandardNpcAttentionLease(viewerId, () -> {
            requestedClose.set(true);
            enqueueIfOpen(() -> {
                NpcAttentionStack.Lease current = internal.getAndSet(null);
                if (current != null) {
                    current.close();
                }
            });
        });
        if (!enqueueIfOpen(() -> {
            if (profile == null || requestedClose.get()) {
                return;
            }
            NpcAttentionStack.GazeTarget target = Optional.ofNullable(observations.get(viewerId))
                    .map(NpcAttentionStack.Observation::target)
                    .orElse(new NpcAttentionStack.GazeTarget(homeYaw, homePitch));
            NpcAttentionStack.Lease created = attention.lease(viewerId, target);
            internal.set(created);
            if (requestedClose.get() && internal.compareAndSet(created, null)) {
                created.close();
            }
        })) {
            lease.close();
        }
        return lease;
    }

    @Override
    public NpcBehaviorSnapshot snapshot() {
        return snapshot;
    }

    /**
     * Publishes the latest <em>underlying authored</em> native state. The frame
     * must exclude behavior-owned routine/attention overlays.
     */
    public synchronized void updateNativeSnapshot(NpcNativeSnapshot snapshot) {
        if (closed.get()) {
            return;
        }
        this.nativeSnapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    /** Updates position/tracking every actor tick without feeding rendered overrides back as base state. */
    public synchronized void updateActorView(
            Vec3 position,
            Optional<sh.harold.library.spatial.SpaceId> spaceId,
            int trackingViewerCount
    ) {
        if (closed.get()) {
            return;
        }
        NpcNativeSnapshot current = nativeSnapshot;
        this.nativeSnapshot = new NpcNativeSnapshot(
                Objects.requireNonNull(position, "position"),
                Objects.requireNonNull(spaceId, "spaceId"),
                current.frame(),
                trackingViewerCount
        );
    }

    /** Called whenever a native equipment/pose/orientation capability changes the authored base. */
    public synchronized void updateBaseFrame(NpcRenderFrame baseFrame) {
        if (closed.get()) {
            return;
        }
        NpcNativeSnapshot current = nativeSnapshot;
        this.nativeSnapshot = new NpcNativeSnapshot(
                current.position(),
                current.spaceId(),
                Objects.requireNonNull(baseFrame, "baseFrame"),
                current.trackingViewerCount()
        );
    }

    /** Replaces the latest immutable viewer observation without allocating a task. */
    public void observeViewer(NpcAttentionStack.Observation observation) {
        observeViewer(observation, implicitObservationEpoch.incrementAndGet());
    }

    /** Replaces the viewer observation and tags its most recently completed LOS probe. */
    public void observeViewer(NpcAttentionStack.Observation observation, long lineOfSightProbeEpoch) {
        Objects.requireNonNull(observation, "observation");
        if (closed.get()) {
            return;
        }
        observations.put(observation.viewerId(), observation);
        observationLosEpochs.put(observation.viewerId(), lineOfSightProbeEpoch);
        if (closed.get()) {
            observations.remove(observation.viewerId(), observation);
            observationLosEpochs.remove(observation.viewerId(), lineOfSightProbeEpoch);
        }
    }

    public void removeViewer(UUID viewerId, NpcAttentionStack.ReleaseReason reason) {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(reason, "reason");
        observations.remove(viewerId);
        observationLosEpochs.remove(viewerId);
        enqueueIfOpen(() -> {
            evaluatedLosEpochs.remove(viewerId);
            attention.retire(viewerId, reason);
        });
    }

    /** Called before the application interaction handler. */
    public void observeInteraction(UUID viewerId, EntityInteractionAction action) {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(action, "action");
        if (profile == null || closed.get()) {
            return;
        }
        enqueueIfOpen(() -> handleInteraction(viewerId));
    }

    public void interactionRouter(InteractionRouter router) {
        this.interactionRouter = Objects.requireNonNull(router, "router");
    }

    public void tick() {
        tick(clock.tick());
    }

    /** Must be invoked from the platform's native actor ownership lane. */
    public synchronized void tick(long tick) {
        if (closed.get()) {
            return;
        }
        if (tick < lastTick) {
            throw new IllegalArgumentException("actor ticks must be monotonic");
        }
        lastTick = tick;
        drainCommands();
        if (profile == null) {
            publishSnapshot(NpcBehaviorActivity.INERT);
            return;
        }

        evaluateAttention();
        processAttentionEvents(tick);
        if (conversationLock == null) {
            routines.tick(tick);
        }
        drainIdleCompletions(tick);
        speech.tick(tick);
        attentionBubbles.tick(tick);
        maybeStartIdle(tick);
        composeFrames(tick);
        publishSnapshot(currentActivity());
    }

    public boolean configured() {
        return profile != null;
    }

    /**
     * Lets an integrated platform entity tick avoid all behavior observation
     * work while remaining able to drain a newly queued configure/disable
     * command. This does not allocate or schedule work.
     */
    public boolean evaluationRequired() {
        return !closed.get()
                && (profile != null || pendingConfiguration != null || !commands.isEmpty());
    }

    public boolean atCleanupCheckpoint() {
        return routines.atCleanupCheckpoint();
    }

    public int trackingViewerCount() {
        return nativeSnapshot.trackingViewerCount();
    }

    public Optional<sh.harold.library.spatial.SpaceId> spaceId() {
        return nativeSnapshot.spaceId();
    }

    public boolean conversationLocked() {
        return conversationLock != null;
    }

    public boolean tryReserveConversation(UUID registrationId) {
        Objects.requireNonNull(registrationId, "registrationId");
        synchronized (this) {
            if (conversationLock != null || !configured() || !atCleanupCheckpoint()) {
                return false;
            }
            conversationLock = registrationId;
            enqueueIfOpen(routines::cancel);
            return true;
        }
    }

    @Override
    public boolean conversationReservedBy(UUID registrationId) {
        return Objects.requireNonNull(registrationId, "registrationId").equals(conversationLock);
    }

    public void releaseConversation(UUID registrationId) {
        Objects.requireNonNull(registrationId, "registrationId");
        synchronized (this) {
            if (registrationId.equals(conversationLock)) {
                conversationLock = null;
                conversationInterruption = false;
                enqueueIfOpen(() -> conversationTarget = null);
            }
        }
    }

    @Override
    public void conversationInterruption(boolean active) {
        if (conversationLock != null) {
            conversationInterruption = active;
        }
    }

    public NpcPlayback speakConversation(Component line, boolean interruption) {
        Objects.requireNonNull(line, "line");
        if (!configured()) {
            throw notConfigured();
        }
        AtomicReference<NpcSpeechQueue.Ticket> ticket = new AtomicReference<>();
        StandardNpcPlayback playback = new StandardNpcPlayback(() -> enqueueIfOpen(() -> {
            NpcSpeechQueue.Ticket current = ticket.get();
            if (current != null) {
                speech.cancel(current);
            }
        }));
        if (!enqueueIfOpen(() -> {
            NpcSpeechQueue.Ticket created = interruption
                    ? speech.nowInsideBarrier(line, NpcBubbleFrame.Kind.INTERRUPTION, lastTick)
                    : speech.now(line, NpcBubbleFrame.Kind.CONVERSATION, lastTick);
            ticket.set(created);
            playback.bind(created.completion());
        })) {
            playback.completeExceptionally(closedFailure());
        }
        return playback;
    }

    public void clearConversationSpeech() {
        enqueueIfOpen(() -> speech.clear(lastTick));
    }

    public List<Component> interruptionLines(List<Component> generic) {
        NpcBehaviorProfile current = profile;
        if (current == null) {
            return List.of();
        }
        return current.conversationInterruptionLines().isEmpty()
                ? List.copyOf(generic)
                : current.conversationInterruptionLines();
    }

    public void finishDeferredInteraction(UUID viewerId) {
        enqueueIfOpen(() -> showInteractionBark(viewerId, lastTick));
    }

    public NpcSpeechQueue.Barrier beginInterruptionBarrier() {
        return speech.beginInterruptionBarrier();
    }

    public Vec3 position() {
        return nativeSnapshot.position();
    }

    public void stageConversation(
            NpcConversationStagingMode mode,
            Vec3 focus,
            boolean selectedToReact
    ) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(focus, "focus");
        enqueueIfOpen(() -> {
            conversationTarget = anglesTo(focus);
            if (selectedToReact) {
                startSharedGesture(new NpcRenderAnimation(
                        mode == NpcConversationStagingMode.CASUAL_RANDOM_SUBSET
                                ? NpcRenderAnimation.Type.HEAD_FLICK_UP
                                : NpcRenderAnimation.Type.NOD,
                        6
                ));
            }
        });
    }

    public void clearConversationStage() {
        enqueueIfOpen(() -> conversationTarget = null);
    }

    public void reactToInterruption() {
        enqueueIfOpen(() -> startSharedGesture(new NpcRenderAnimation(
                NpcRenderAnimation.Type.DOUBLE_TAKE,
                8
        )));
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        synchronized (this) {
            drainCommands();
            resetBehavior(NpcAttentionStack.ReleaseReason.PLATFORM_CLOSED);
            if (pendingConfiguration != null) {
                pendingConfiguration.completion.completeExceptionally(
                        new IllegalStateException("NPC behavior actor closed during configuration")
                );
                pendingConfiguration = null;
            }
            profile = null;
            observations.clear();
            observationLosEpochs.clear();
            evaluatedLosEpochs.clear();
            idleCompletions.clear();
            commands.clear();
            snapshot = NpcBehaviorSnapshot.inert();
        }
    }

    private void beginConfiguration(
            long generation,
            NpcBehaviorProfile requestedProfile,
            CompletableFuture<Void> completion
    ) {
        if (closed.get()) {
            completion.completeExceptionally(new IllegalStateException("NPC behavior actor is closed"));
            return;
        }
        if (pendingConfiguration != null) {
            pendingConfiguration.completion.completeExceptionally(
                    new IllegalStateException("NPC behavior configuration was superseded")
            );
        }
        resetBehavior(NpcAttentionStack.ReleaseReason.PROFILE_REPLACED);
        profile = null;
        pendingConfiguration = new PendingConfiguration(generation, requestedProfile, completion);
        renderer.restoreNativePresentation().whenComplete((ignored, failure) ->
                enqueueIfOpen(() -> finishConfiguration(generation, failure))
        );
    }

    private void finishConfiguration(long generation, Throwable failure) {
        PendingConfiguration pending = pendingConfiguration;
        if (pending == null || pending.generation != generation) {
            return;
        }
        pendingConfiguration = null;
        if (closed.get()) {
            pending.completion.completeExceptionally(closedFailure());
            return;
        }
        if (failure != null) {
            pending.completion.completeExceptionally(failure);
            return;
        }
        if (pending.profile != null) {
            profile = pending.profile;
            attention = new NpcAttentionStack(policy(pending.profile));
            routines.tuning(pending.profile.tuning());
            long hashOffset = Math.floorMod(actorId.getLeastSignificantBits(), 20L);
            nextIdleEvaluation = lastTick + hashOffset;
        }
        pending.completion.complete(null);
    }

    private void resetBehavior(NpcAttentionStack.ReleaseReason reason) {
        for (NpcAttentionStack.Session session : attention.snapshot().sessions()) {
            attention.retire(session.viewerId(), reason);
            renderer.clearViewerOverlay(session.viewerId(), nativeSnapshot.frame());
        }
        renderedOverlays.clear();
        observations.clear();
        observationLosEpochs.clear();
        viewerGazes.clear();
        viewerGestures.clear();
        lastViewerFrames.clear();
        lastViewerRenderTicks.clear();
        evaluatedLosEpochs.clear();
        sharedGesture.clear();
        attentionBubbles.clear();
        speech.clear(lastTick);
        routines.cancel();
        idleSelector.reset();
        activeIdle = null;
        activeIdleTicket = null;
        lastSharedFrame = null;
        conversationLock = null;
        conversationInterruption = false;
        conversationTarget = null;
    }

    private void drainCommands() {
        Runnable command;
        while ((command = commands.poll()) != null) {
            command.run();
        }
    }

    private synchronized boolean enqueueIfOpen(Runnable command) {
        Objects.requireNonNull(command, "command");
        if (closed.get()) {
            return false;
        }
        commands.add(command);
        return true;
    }

    private IllegalStateException closedFailure() {
        return new IllegalStateException("NPC behavior actor " + actorId + " is closed");
    }

    private void evaluateAttention() {
        for (NpcAttentionStack.Observation observation : observations.values()) {
            long epoch = observationLosEpochs.getOrDefault(observation.viewerId(), Long.MIN_VALUE);
            Long previous = evaluatedLosEpochs.put(observation.viewerId(), epoch);
            attention.observe(observation, previous == null || previous.longValue() != epoch);
        }
    }

    private void processAttentionEvents(long tick) {
        for (NpcAttentionStack.Event event : attention.drainEvents()) {
            if (event.type() == NpcAttentionStack.EventType.RELEASED) {
                attentionBubbles.release(event.viewerId());
                viewerGazes.remove(event.viewerId());
                viewerGestures.remove(event.viewerId());
                lastViewerFrames.remove(event.viewerId());
                lastViewerRenderTicks.remove(event.viewerId());
                if (renderedOverlays.remove(event.viewerId())) {
                    renderer.clearViewerOverlay(event.viewerId(), currentSharedFrame());
                }
                continue;
            }
            if ((event.type() == NpcAttentionStack.EventType.ACQUIRED
                    || event.type() == NpcAttentionStack.EventType.REFRESHED)
                    && event.acquisitionReason() != NpcAttentionStack.AcquisitionReason.INTERACTION) {
                performAcquisitionAct(event.viewerId(), tick);
            }
        }
    }

    private void performAcquisitionAct(UUID viewerId, long tick) {
        NpcAttentionResponse response = attentionResponse();
        if (response instanceof NpcAttentionResponse.Sustain sustain) {
            sustain.acquisitionAct().ifPresent(act -> performAcknowledgement(viewerId, act, tick));
        } else if (response instanceof NpcAttentionResponse.Acknowledge acknowledge
                && attention.latchAcknowledgement(viewerId)) {
            performAcknowledgement(viewerId, acknowledge.acknowledgement(), tick);
        }
    }

    private void performAcknowledgement(UUID viewerId, NpcAcknowledgementSpec act, long tick) {
        if (!act.gestures().isEmpty()) {
            NpcGesturePreset gesture = act.gestures().get(random.nextInt(0, act.gestures().size()));
            NpcRenderAnimation animation = new NpcRenderAnimation(toAnimation(gesture), 6);
            startViewerGesture(viewerId, animation);
        }
        if (!act.barkLines().isEmpty()) {
            showAttentionBark(
                    viewerId,
                    act.barkLines().get(random.nextInt(0, act.barkLines().size())),
                    tick
            );
        }
    }

    private void handleInteraction(UUID viewerId) {
        NpcAttentionStack.GazeTarget target = Optional.ofNullable(observations.get(viewerId))
                .map(NpcAttentionStack.Observation::target)
                .orElse(new NpcAttentionStack.GazeTarget(homeYaw, homePitch));
        attention.interaction(viewerId, target);
        if (!interactionRouter.route(actorId, viewerId)) {
            showInteractionBark(viewerId, lastTick);
        }
    }

    private void showInteractionBark(UUID viewerId, long tick) {
        NpcBehaviorProfile current = profile;
        if (current == null) {
            return;
        }
        startViewerGesture(viewerId, new NpcRenderAnimation(
                toAnimation(defaultInteractionGesture(current)),
                6
        ));
        if (current.interactionLines().isEmpty()) {
            return;
        }
        Component line = current.interactionLines().get(random.nextInt(0, current.interactionLines().size()));
        showAttentionBark(viewerId, line, tick);
    }

    private void showAttentionBark(UUID viewerId, Component line, long tick) {
        Set<UUID> tracked = new LinkedHashSet<>(observations.keySet());
        // A direct interaction can arrive one viewer tick before the first
        // spatial observation. The interaction itself proves that this target
        // can receive the response, so do not drop its matching voice cue.
        tracked.add(viewerId);
        Set<UUID> engaged = new LinkedHashSet<>();
        for (NpcAttentionStack.Session session : attention.snapshot().sessions()) {
            engaged.add(session.viewerId());
        }
        attentionBubbles.show(viewerId, line, profile.voice(), tracked, engaged, tick);
    }

    private void drainIdleCompletions(long tick) {
        IdleCompletion completion;
        while ((completion = idleCompletions.poll()) != null) {
            if (completion.entry == activeIdle) {
                if (completion.naturally) {
                    idleSelector.completed(completion.entry, tick);
                    maybePropCompletionLine();
                }
                activeIdle = null;
                activeIdleTicket = null;
            }
        }
    }

    private void maybeStartIdle(long tick) {
        if (conversationLock != null || routines.active() || activeIdle != null || tick < nextIdleEvaluation) {
            return;
        }
        nextIdleEvaluation = tick + 20L;
        idleSelector.select(profile.idleEntries(), tick, entry -> routines.eligible(entry.routine()))
                .ifPresent(entry -> {
                    NpcRoutinePlayer.Ticket ticket = routines.startAmbient(entry.routine());
                    if (!ticket.done()) {
                        activeIdle = entry;
                        activeIdleTicket = ticket;
                        ticket.completion().whenComplete((naturally, failure) -> idleCompletions.add(
                                new IdleCompletion(entry, failure == null && Boolean.TRUE.equals(naturally))
                        ));
                    }
                });
    }

    private void maybePropCompletionLine() {
        if (profile.propCompletionLines().isEmpty()) {
            return;
        }
        Component line = profile.propCompletionLines().get(random.nextInt(0, profile.propCompletionLines().size()));
        speech.append(line, NpcBubbleFrame.Kind.WORLD);
    }

    private void composeFrames(long tick) {
        NpcRenderFrame sharedBase = routines.composedFrame();
        NpcAttentionStack.Snapshot attentionSnapshot = attention.snapshot();
        NpcRenderFrame shared = sharedBase;
        Map<UUID, NpcRenderFrame> desiredOverlays = new LinkedHashMap<>();

        if (conversationLock != null && conversationTarget != null) {
            conversationGaze.retarget(conversationTarget);
            NpcGazeController.State gaze = conversationGaze.tick(
                    tick,
                    profile.personality(),
                    profile.tuning(),
                    NpcSustainMode.STEADY
            );
            shared = sharedBase.withLook(gaze.bodyYaw(), gaze.headYaw(), gaze.pitch());
        }

        NpcSustainMode viewerFocusMode = viewerFocusMode();
        for (NpcAttentionStack.Session session : attentionSnapshot.sessions()) {
            UUID viewerId = session.viewerId();
            ViewerGaze viewer = viewerGazes.computeIfAbsent(viewerId, ignored -> new ViewerGaze(
                    new NpcGazeController(sharedBase.bodyYaw(), sharedBase.pitch()),
                    session.target(),
                    tick
            ));
            viewer.controller.retarget(session.target());
            NpcGazeController.State gaze = viewer.controller.tick(
                    tick,
                    profile.personality(),
                    profile.tuning(),
                    viewerFocusMode
            );
            NpcRenderFrame overlay = sharedBase.withLook(gaze.bodyYaw(), gaze.headYaw(), gaze.pitch());
            desiredOverlays.put(viewerId, overlay);
        }

        shared = sharedGesture.compose(shared, tick);

        List<UUID> completedGestures = new ArrayList<>();
        for (Map.Entry<UUID, NpcGestureComposer> entry : viewerGestures.entrySet()) {
            UUID viewerId = entry.getKey();
            NpcGestureComposer gesture = entry.getValue();
            NpcRenderFrame viewerBase = desiredOverlays.getOrDefault(viewerId, shared);
            NpcRenderFrame composed = gesture.compose(viewerBase, tick);
            if (gesture.active()) {
                desiredOverlays.put(viewerId, composed);
            } else {
                completedGestures.add(viewerId);
            }
        }
        completedGestures.forEach(viewerGestures::remove);

        for (Map.Entry<UUID, NpcRenderFrame> entry : desiredOverlays.entrySet()) {
            UUID viewerId = entry.getKey();
            NpcRenderFrame overlay = entry.getValue();
            Long lastRenderedAt = lastViewerRenderTicks.get(viewerId);
            boolean renderDue = lastRenderedAt == null
                    || tick - lastRenderedAt >= VIEWER_OVERLAY_INTERVAL_TICKS;
            if (renderDue && materiallyDifferent(lastViewerFrames.get(viewerId), overlay)) {
                renderer.renderViewerOverlay(viewerId, overlay);
                lastViewerFrames.put(viewerId, overlay);
                lastViewerRenderTicks.put(viewerId, tick);
            }
            renderedOverlays.add(viewerId);
        }
        clearUndesiredOverlays(desiredOverlays.keySet(), shared);

        if (materiallyDifferent(lastSharedFrame, shared)) {
            renderer.renderSharedFrame(shared);
            lastSharedFrame = shared;
        }
    }

    private void clearUndesiredOverlays(Set<UUID> desired, NpcRenderFrame shared) {
        List<UUID> removed = new ArrayList<>();
        for (UUID viewerId : renderedOverlays) {
            if (!desired.contains(viewerId)) {
                renderer.clearViewerOverlay(viewerId, shared);
                removed.add(viewerId);
            }
        }
        renderedOverlays.removeAll(removed);
        removed.forEach(lastViewerFrames::remove);
        removed.forEach(lastViewerRenderTicks::remove);
        viewerGazes.keySet().removeIf(viewer -> !desired.contains(viewer));
    }

    private NpcRenderFrame currentSharedFrame() {
        return lastSharedFrame == null ? routines.composedFrame() : lastSharedFrame;
    }

    private NpcAttentionResponse attentionResponse() {
        return profile.attention().responseFor(switch (currentAttentionActivity()) {
            case IDLE -> NpcAttentionActivity.IDLE;
            case ROUTINE -> NpcAttentionActivity.ROUTINE;
            case CONVERSATION -> NpcAttentionActivity.CONVERSATION;
        });
    }

    private NpcSustainMode viewerFocusMode() {
        NpcAttentionResponse idle = profile.attention().responseFor(NpcAttentionActivity.IDLE);
        return idle instanceof NpcAttentionResponse.Sustain sustain
                ? sustain.mode()
                : NpcSustainMode.NATURAL;
    }

    private AttentionActivity currentAttentionActivity() {
        if (conversationLock != null) {
            return AttentionActivity.CONVERSATION;
        }
        if (routines.active()) {
            return AttentionActivity.ROUTINE;
        }
        return AttentionActivity.IDLE;
    }

    private NpcBehaviorActivity currentActivity() {
        if (conversationLock != null) {
            return conversationInterruption
                    ? NpcBehaviorActivity.CONVERSATION_INTERRUPTION
                    : NpcBehaviorActivity.CONVERSATION;
        }
        if (routines.active()) {
            return NpcBehaviorActivity.ROUTINE;
        }
        NpcSpeechQueue.Snapshot speechSnapshot = speech.snapshot();
        if (speechSnapshot.phase() != NpcSpeechQueue.Phase.IDLE
                || attentionBubbles.snapshot().realBubble().isPresent()) {
            return NpcBehaviorActivity.SPEECH;
        }
        if (attention.snapshot().sessions().isEmpty()
                && lastSharedFrame != null
                && (Math.abs(NpcGazeController.shortestDelta(lastSharedFrame.bodyYaw(), homeYaw)) > 0.01f
                || Math.abs(NpcGazeController.shortestDelta(lastSharedFrame.headYaw(), homeYaw)) > 0.01f)) {
            return NpcBehaviorActivity.RETURNING_HOME;
        }
        return NpcBehaviorActivity.AMBIENT_IDLE;
    }

    private void publishSnapshot(NpcBehaviorActivity activity) {
        NpcAttentionStack.Snapshot attentionSnapshot = attention.snapshot();
        NpcRoutinePlayer.Snapshot routineSnapshot = routines.snapshot();
        NpcSpeechQueue.Snapshot speechSnapshot = speech.snapshot();
        Optional<Component> visibleSpeech = speechSnapshot.visibleText().or(() ->
                attentionBubbles.snapshot().realBubble().map(NpcBubbleFrame::text)
        );
        List<UUID> stack = attentionSnapshot.sessions().stream()
                .map(NpcAttentionStack.Session::viewerId)
                .toList();
        int queued = speechSnapshot.pending().size() + (speechSnapshot.urgentBehindBarrier().isPresent() ? 1 : 0);
        snapshot = new NpcBehaviorSnapshot(
                profile != null,
                activity,
                attentionSnapshot.canonicalViewer(),
                stack,
                routineSnapshot.activeRoutine().map(NpcRoutine::key),
                visibleSpeech,
                queued,
                conversationLock != null,
                ++revision
        );
    }

    private void showWorldBubble(NpcBubbleFrame bubble) {
        attentionBubbles.clear();
        renderer.showSharedBubble(bubble);
        NpcBehaviorProfile current = profile;
        if (current != null) {
            playSharedVoice(current.voice());
        }
    }

    private void playSharedVoice(NpcVoiceProfile voice) {
        NpcVoiceDelivery.select(voice, random).ifPresent(cue -> renderer.playSound(cue.shared()));
    }

    private static boolean materiallyDifferent(NpcRenderFrame previous, NpcRenderFrame next) {
        if (previous == null) {
            return true;
        }
        return previous.pose() != next.pose()
                || previous.usingMainHand() != next.usingMainHand()
                || previous.usingOffHand() != next.usingOffHand()
                || !previous.equipment().equals(next.equipment())
                || Math.abs(NpcGazeController.shortestDelta(previous.headYaw(), next.headYaw())) >= 1.5f
                || Math.abs(NpcGazeController.shortestDelta(previous.bodyYaw(), next.bodyYaw())) >= 2.0f
                || Math.abs(previous.pitch() - next.pitch()) >= 1.5f;
    }

    private NpcAttentionStack.GazeTarget anglesTo(Vec3 focus) {
        Vec3 delta = focus.subtract(nativeSnapshot.position());
        double horizontal = Math.sqrt(delta.x() * delta.x() + delta.z() * delta.z());
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x(), delta.z()));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y(), Math.max(1.0e-9, horizontal)));
        pitch = Math.max(
                NpcPersonalityMotion.MAXIMUM_UP_PITCH,
                Math.min(NpcPersonalityMotion.MAXIMUM_DOWN_PITCH, pitch)
        );
        return new NpcAttentionStack.GazeTarget(yaw, pitch);
    }

    private NpcAttentionStack.Policy policy(NpcBehaviorProfile configuredProfile) {
        double radius = configuredProfile.tuning().radiusMultiplier();
        return new NpcAttentionStack.Policy(
                configuredProfile.attention().enterRadius() * radius,
                configuredProfile.attention().exitRadius() * radius,
                configuredProfile.attention().maximumVerticalDifference(),
                configuredProfile.attention().sameSpaceRequired(),
                configuredProfile.attention().lineOfSightRequired(),
                configuredProfile.attention().lineOfSightFailuresBeforeRelease()
        );
    }

    private NpcGesturePreset defaultInteractionGesture(NpcBehaviorProfile current) {
        return switch (current.personality()) {
            case WARM -> NpcGesturePreset.WAVE;
            case CURIOUS -> NpcGesturePreset.DOUBLE_TAKE;
            case CONFUSED -> NpcGesturePreset.LOOK_AROUND;
            case NERVOUS -> NpcGesturePreset.CROUCH_PULSE;
            case DISTRACTED -> NpcGesturePreset.HEAD_FLICK_DOWN;
            case SLEEPY -> NpcGesturePreset.NOD;
            case NEUTRAL, CONFIDENT -> NpcGesturePreset.NOD;
        };
    }

    private static NpcRenderAnimation.Type toAnimation(NpcGesturePreset gesture) {
        return switch (gesture) {
            case NOD -> NpcRenderAnimation.Type.NOD;
            case HEAD_FLICK_UP -> NpcRenderAnimation.Type.HEAD_FLICK_UP;
            case HEAD_FLICK_DOWN -> NpcRenderAnimation.Type.HEAD_FLICK_DOWN;
            case WAVE -> NpcRenderAnimation.Type.WAVE;
            case DOUBLE_TAKE -> NpcRenderAnimation.Type.DOUBLE_TAKE;
            case LOOK_AROUND -> NpcRenderAnimation.Type.LOOK_AROUND;
            case CROUCH_PULSE -> NpcRenderAnimation.Type.CROUCH_PULSE;
            case LEAN_FORWARD_PROXY -> NpcRenderAnimation.Type.LEAN_FORWARD_PROXY;
            case LEAN_BACK_PROXY -> NpcRenderAnimation.Type.LEAN_BACK_PROXY;
        };
    }

    private void startSharedGesture(NpcRenderAnimation animation) {
        sharedGesture.start(animation, lastTick);
        renderer.animateShared(animation);
    }

    private void startViewerGesture(UUID viewerId, NpcRenderAnimation animation) {
        viewerGestures.computeIfAbsent(viewerId, ignored -> new NpcGestureComposer())
                .start(animation, lastTick);
        lastViewerRenderTicks.remove(viewerId);
        renderer.animateViewer(viewerId, animation);
    }

    private void requireConfigured() {
        requireOpen();
        if (profile == null) {
            throw notConfigured();
        }
    }

    private void requireOpen() {
        if (closed.get()) {
            throw new IllegalStateException("NPC behavior actor is closed");
        }
    }

    private IllegalStateException notConfigured() {
        return new IllegalStateException("NPC " + actorId + " has no behavior profile configured");
    }

    public interface InteractionRouter {
        InteractionRouter NONE = (actorId, viewerId) -> false;

        /** @return true when a conversation owns and defers the attention bark. */
        boolean route(UUID actorId, UUID viewerId);
    }

    private enum AttentionActivity {
        IDLE,
        ROUTINE,
        CONVERSATION
    }

    private record PendingConfiguration(
            long generation,
            NpcBehaviorProfile profile,
            CompletableFuture<Void> completion
    ) {
    }

    private record IdleCompletion(NpcIdleEntry entry, boolean naturally) {
    }

    private static final class ViewerGaze {
        private final NpcGazeController controller;

        private ViewerGaze(
                NpcGazeController controller,
                NpcAttentionStack.GazeTarget target,
                long tick
        ) {
            this.controller = controller;
            controller.target(target, tick);
        }
    }

    private static final class IncrementingClock implements NpcBehaviorClock {
        private final AtomicLong tick = new AtomicLong();

        @Override
        public long tick() {
            return tick.getAndIncrement();
        }
    }
}
