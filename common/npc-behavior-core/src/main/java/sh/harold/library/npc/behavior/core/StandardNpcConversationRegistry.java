package sh.harold.library.npc.behavior.core;

import net.kyori.adventure.text.Component;
import sh.harold.library.entity.ManagedEntity;
import sh.harold.library.npc.behavior.HumanoidBehaviorCapable;
import sh.harold.library.npc.behavior.NpcConversationRegistration;
import sh.harold.library.npc.behavior.NpcConversationRegistry;
import sh.harold.library.npc.behavior.NpcConversationSnapshot;
import sh.harold.library.npc.behavior.NpcConversationStagingMode;
import sh.harold.library.npc.behavior.NpcConversationState;
import sh.harold.library.npc.behavior.NpcConversationTopic;
import sh.harold.library.npc.behavior.NpcPlayback;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Pure ID-based global conversation coordinator. */
public final class StandardNpcConversationRegistry implements
        NpcConversationRegistry,
        NpcBehaviorActor.InteractionRouter,
        AutoCloseable {

    private static final int INITIAL_MINIMUM_TICKS = 100;
    private static final int INITIAL_MAXIMUM_TICKS = 300;
    private static final int MINIMUM_TURNS = 5;
    private static final int MAXIMUM_TURNS = 9;
    private static final int GAP_MINIMUM_TICKS = 16;
    private static final int GAP_MAXIMUM_TICKS = 40;
    private static final int COOLDOWN_MINIMUM_TICKS = 500;
    private static final int COOLDOWN_MAXIMUM_TICKS = 1_000;

    private final NpcBehaviorClock clock;
    private final NpcBehaviorRandom random;
    private final Map<UUID, Registration> registrations = new LinkedHashMap<>();
    private final Map<UUID, Registration> locks = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Runnable> events = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private long lastTick;

    public StandardNpcConversationRegistry() {
        this(new IncrementingClock(), NpcBehaviorRandom.from(ThreadLocalRandom.current()));
    }

    public StandardNpcConversationRegistry(NpcBehaviorClock clock, NpcBehaviorRandom random) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    public synchronized NpcConversationRegistration register(
            NpcConversationTopic topic,
            Collection<? extends ManagedEntity> cast
    ) {
        requireOpen();
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(cast, "cast");
        Map<UUID, NpcConversationParticipant> unique = new LinkedHashMap<>();
        for (ManagedEntity entity : cast) {
            Objects.requireNonNull(entity, "cast contains null");
            HumanoidBehaviorCapable capability = entity.requireCapability(HumanoidBehaviorCapable.class);
            if (!(capability instanceof NpcConversationParticipant participant)) {
                throw new IllegalArgumentException(
                        "NPC " + entity.id() + " uses a behavior implementation that cannot join platform conversations"
                );
            }
            if (!participant.configured()) {
                throw new IllegalArgumentException("NPC " + entity.id() + " has no behavior profile configured");
            }
            if (unique.putIfAbsent(entity.id(), participant) != null) {
                throw new IllegalArgumentException("Conversation cast contains duplicate NPC " + entity.id());
            }
        }
        if (unique.size() < 2) {
            throw new IllegalArgumentException("Conversation casts need at least two unique configured mannequins");
        }
        Registration registration = new Registration(UUID.randomUUID(), topic, List.copyOf(unique.values()));
        registration.nextActionAt = lastTick + between(INITIAL_MINIMUM_TICKS, INITIAL_MAXIMUM_TICKS);
        registrations.put(registration.id, registration);
        registration.cast.forEach(participant -> participant.interactionRouter(this));
        return registration;
    }

    @Override
    public boolean route(UUID actorId, UUID viewerId) {
        Registration registration = locks.get(Objects.requireNonNull(actorId, "actorId"));
        if (registration == null || registration.closed) {
            return false;
        }
        NpcConversationState state = registration.state;
        if (state != NpcConversationState.ACTIVE && state != NpcConversationState.INTERRUPTING) {
            return false;
        }
        events.add(() -> interrupt(registration, actorId, Objects.requireNonNull(viewerId, "viewerId")));
        return true;
    }

    public void tick() {
        tick(clock.tick());
    }

    public synchronized void tick(long tick) {
        if (closed.get()) {
            return;
        }
        if (tick < lastTick) {
            throw new IllegalArgumentException("conversation ticks must be monotonic");
        }
        lastTick = tick;
        drainEvents();
        for (Registration registration : List.copyOf(registrations.values())) {
            advance(registration, tick);
        }
    }

    public synchronized int activeLockCount() {
        return locks.size();
    }

    public synchronized int registrationCount() {
        return registrations.size();
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (Registration registration : List.copyOf(registrations.values())) {
            unregister(registration);
        }
        registrations.clear();
        locks.clear();
        events.clear();
    }

    private void advance(Registration registration, long tick) {
        if (registration.closed) {
            return;
        }
        if ((registration.state == NpcConversationState.ACTIVE
                || registration.state == NpcConversationState.INTERRUPTING)
                && registration.cast.stream().anyMatch(participant ->
                        !participant.configured() || !participant.conversationReservedBy(registration.id))) {
            abortToCooldown(registration, tick);
            return;
        }
        switch (registration.state) {
            case WAITING -> {
                if (tick >= registration.nextActionAt && eligible(registration)) {
                    start(registration, tick);
                }
            }
            case ACTIVE -> advanceActive(registration, tick);
            case INTERRUPTING -> advanceInterruption(registration, tick);
            case COOLDOWN -> {
                if (tick >= registration.nextActionAt) {
                    registration.state = NpcConversationState.WAITING;
                    registration.completedTurns = 0;
                    registration.plannedTurns = 0;
                    registration.revision++;
                }
            }
            case CLOSED -> {
            }
        }
    }

    private boolean eligible(Registration registration) {
        Optional<sh.harold.library.spatial.SpaceId> commonSpace = registration.cast.get(0).spaceId();
        if (commonSpace.isEmpty()) {
            return false;
        }
        boolean anyTracked = false;
        for (NpcConversationParticipant participant : registration.cast) {
            if (!participant.configured()
                    || !participant.atCleanupCheckpoint()
                    || !commonSpace.equals(participant.spaceId())
                    || locks.containsKey(participant.actorId())) {
                return false;
            }
            anyTracked |= participant.trackingViewerCount() > 0;
        }
        return anyTracked;
    }

    private void start(Registration registration, long tick) {
        List<NpcConversationParticipant> ordered = registration.cast.stream()
                .sorted(Comparator.comparing(NpcConversationParticipant::actorId))
                .toList();
        List<NpcConversationParticipant> acquired = new ArrayList<>();
        for (NpcConversationParticipant participant : ordered) {
            if (!participant.tryReserveConversation(registration.id)) {
                acquired.forEach(value -> value.releaseConversation(registration.id));
                return;
            }
            acquired.add(participant);
        }
        for (NpcConversationParticipant participant : acquired) {
            Registration prior = locks.putIfAbsent(participant.actorId(), registration);
            if (prior != null) {
                acquired.forEach(value -> value.releaseConversation(registration.id));
                acquired.forEach(value -> locks.remove(value.actorId(), registration));
                return;
            }
        }
        registration.state = NpcConversationState.ACTIVE;
        registration.plannedTurns = between(MINIMUM_TURNS, MAXIMUM_TURNS);
        registration.completedTurns = 0;
        registration.lineBag = new ShuffleBag<>(registration.topic.lines(), random);
        registration.stagingBag = new ShuffleBag<>(List.of(NpcConversationStagingMode.values()), random);
        registration.nextActionAt = tick;
        registration.revision++;
    }

    private void advanceActive(Registration registration, long tick) {
        if (registration.currentPlayback != null || tick < registration.nextActionAt) {
            return;
        }
        if (registration.completedTurns >= registration.plannedTurns) {
            finishToCooldown(registration, tick);
            return;
        }
        NpcConversationParticipant speaker = registration.cast.get(random.nextInt(0, registration.cast.size()));
        Component line = registration.lineBag.draw();
        NpcConversationStagingMode staging = registration.stagingBag.draw();
        stage(registration, speaker, staging);
        registration.speaker = speaker;
        registration.currentLine = line;
        registration.stagingMode = staging;
        registration.revision++;
        NpcPlayback playback;
        try {
            playback = speaker.speakConversation(line, false);
        } catch (RuntimeException unavailable) {
            abortToCooldown(registration, tick);
            return;
        }
        registration.currentPlayback = playback;
        playback.completion().whenComplete((ignored, failure) -> events.add(() -> {
            if (registration.state != NpcConversationState.ACTIVE || registration.currentPlayback != playback) {
                return;
            }
            registration.currentPlayback = null;
            registration.currentLine = null;
            registration.speaker = null;
            registration.stagingMode = null;
            registration.completedTurns++;
            registration.nextActionAt = lastTick + between(GAP_MINIMUM_TICKS, GAP_MAXIMUM_TICKS);
            registration.revision++;
        }));
    }

    private void stage(
            Registration registration,
            NpcConversationParticipant speaker,
            NpcConversationStagingMode staging
    ) {
        List<NpcConversationParticipant> listeners = registration.cast.stream()
                .filter(participant -> participant != speaker)
                .toList();
        NpcConversationParticipant addressee = listeners.get(random.nextInt(0, listeners.size()));
        boolean anyCasual = false;
        for (NpcConversationParticipant listener : listeners) {
            boolean selected = switch (staging) {
                case EXPLICIT_ADDRESSEE -> listener == addressee;
                case GROUP_FACES_SPEAKER -> false;
                case CASUAL_RANDOM_SUBSET -> random.nextDouble() < 0.5;
                case SPEAKER_FOCUSED_PASSIVE_LISTENERS -> false;
            };
            if (staging == NpcConversationStagingMode.CASUAL_RANDOM_SUBSET) {
                anyCasual |= selected;
            }
            if (staging == NpcConversationStagingMode.SPEAKER_FOCUSED_PASSIVE_LISTENERS) {
                listener.clearConversationStage();
            } else {
                listener.stageConversation(staging, speaker.position(), selected);
            }
        }
        if (staging == NpcConversationStagingMode.CASUAL_RANDOM_SUBSET && !anyCasual) {
            addressee.stageConversation(staging, speaker.position(), true);
        }
        speaker.clearConversationStage();
    }

    private void interrupt(Registration registration, UUID interactedActorId, UUID viewerId) {
        if (registration.closed || (registration.state != NpcConversationState.ACTIVE
                && registration.state != NpcConversationState.INTERRUPTING)) {
            registration.cast.stream()
                    .filter(participant -> participant.actorId().equals(interactedActorId))
                    .findFirst()
                    .ifPresent(participant -> participant.finishDeferredInteraction(viewerId));
            return;
        }
        if (registration.currentPlayback != null) {
            registration.currentPlayback.cancel();
            registration.currentPlayback = null;
        }
        registration.cast.forEach(NpcConversationParticipant::clearConversationSpeech);
        registration.currentLine = null;
        registration.speaker = null;
        registration.stagingMode = null;

        if (registration.state != NpcConversationState.INTERRUPTING) {
            registration.barriers = registration.cast.stream()
                    .map(NpcConversationParticipant::beginInterruptionBarrier)
                    .toList();
        }
        registration.state = NpcConversationState.INTERRUPTING;
        registration.cast.forEach(participant -> participant.conversationInterruption(true));
        registration.interacted = registration.cast.stream()
                .filter(participant -> participant.actorId().equals(interactedActorId))
                .findFirst()
                .orElseThrow();
        registration.interactionViewer = viewerId;
        List<NpcConversationParticipant> reacting = new ArrayList<>(registration.cast);
        reacting.remove(registration.interacted);
        shuffle(reacting);
        registration.cascade = new ArrayDeque<>(reacting);
        registration.nextActionAt = lastTick;
        registration.revision++;
    }

    private void advanceInterruption(Registration registration, long tick) {
        if (registration.currentPlayback != null || tick < registration.nextActionAt) {
            return;
        }
        NpcConversationParticipant reactor = registration.cascade.pollFirst();
        if (reactor == null) {
            NpcConversationParticipant interacted = registration.interacted;
            UUID viewerId = registration.interactionViewer;
            closeBarriers(registration);
            finishToCooldown(registration, tick);
            interacted.finishDeferredInteraction(viewerId);
            return;
        }
        List<Component> lines = reactor.interruptionLines(registration.topic.interruptionLines());
        if (lines.isEmpty()) {
            reactor.reactToInterruption();
            registration.nextActionAt = tick + between(GAP_MINIMUM_TICKS, GAP_MAXIMUM_TICKS);
            registration.revision++;
            return;
        }
        Component line = lines.get(random.nextInt(0, lines.size()));
        registration.speaker = reactor;
        registration.currentLine = line;
        registration.stagingMode = NpcConversationStagingMode.GROUP_FACES_SPEAKER;
        registration.revision++;
        NpcPlayback playback;
        try {
            playback = reactor.speakConversation(line, true);
        } catch (RuntimeException unavailable) {
            abortToCooldown(registration, tick);
            return;
        }
        registration.currentPlayback = playback;
        playback.completion().whenComplete((ignored, failure) -> events.add(() -> {
            if (registration.state != NpcConversationState.INTERRUPTING
                    || registration.currentPlayback != playback) {
                return;
            }
            registration.currentPlayback = null;
            registration.currentLine = null;
            registration.speaker = null;
            registration.stagingMode = null;
            registration.nextActionAt = lastTick + between(GAP_MINIMUM_TICKS, GAP_MAXIMUM_TICKS);
            registration.revision++;
        }));
    }

    private void finishToCooldown(Registration registration, long tick) {
        if (registration.currentPlayback != null) {
            registration.currentPlayback.cancel();
            registration.currentPlayback = null;
        }
        registration.cast.forEach(participant -> {
            participant.clearConversationStage();
            participant.releaseConversation(registration.id);
            locks.remove(participant.actorId(), registration);
        });
        registration.state = NpcConversationState.COOLDOWN;
        registration.currentLine = null;
        registration.speaker = null;
        registration.stagingMode = null;
        registration.nextActionAt = tick + between(COOLDOWN_MINIMUM_TICKS, COOLDOWN_MAXIMUM_TICKS);
        registration.revision++;
    }

    private void abortToCooldown(Registration registration, long tick) {
        closeBarriers(registration);
        registration.cast.forEach(NpcConversationParticipant::clearConversationSpeech);
        finishToCooldown(registration, tick);
    }

    private void closeBarriers(Registration registration) {
        for (AutoCloseable barrier : registration.barriers) {
            try {
                barrier.close();
            } catch (Exception failure) {
                throw new IllegalStateException("Failed to release NPC interruption barrier", failure);
            }
        }
        registration.barriers = List.of();
    }

    private void unregister(Registration registration) {
        if (registration.closed) {
            return;
        }
        registration.closed = true;
        if (registration.currentPlayback != null) {
            registration.currentPlayback.cancel();
            registration.currentPlayback = null;
        }
        closeBarriers(registration);
        registration.cast.forEach(participant -> {
            participant.clearConversationSpeech();
            participant.clearConversationStage();
            participant.releaseConversation(registration.id);
            locks.remove(participant.actorId(), registration);
        });
        registration.state = NpcConversationState.CLOSED;
        registration.revision++;
        registrations.remove(registration.id);
    }

    private void drainEvents() {
        Runnable event;
        while ((event = events.poll()) != null) {
            event.run();
        }
    }

    private int between(int minimum, int maximum) {
        return random.betweenInclusive(minimum, maximum);
    }

    private <T> void shuffle(List<T> values) {
        for (int index = values.size() - 1; index > 0; index--) {
            int swap = random.nextInt(0, index + 1);
            T value = values.get(index);
            values.set(index, values.get(swap));
            values.set(swap, value);
        }
    }

    private void requireOpen() {
        if (closed.get()) {
            throw new IllegalStateException("NPC conversation registry is closed");
        }
    }

    private final class Registration implements NpcConversationRegistration {
        private final UUID id;
        private final NpcConversationTopic topic;
        private final List<NpcConversationParticipant> cast;
        private volatile NpcConversationState state = NpcConversationState.WAITING;
        private volatile boolean closed;
        private int completedTurns;
        private int plannedTurns;
        private long revision;
        private long nextActionAt;
        private NpcConversationParticipant speaker;
        private Component currentLine;
        private NpcConversationStagingMode stagingMode;
        private NpcPlayback currentPlayback;
        private ShuffleBag<Component> lineBag;
        private ShuffleBag<NpcConversationStagingMode> stagingBag;
        private Deque<NpcConversationParticipant> cascade = new ArrayDeque<>();
        private NpcConversationParticipant interacted;
        private UUID interactionViewer;
        private List<AutoCloseable> barriers = List.of();

        private Registration(UUID id, NpcConversationTopic topic, List<NpcConversationParticipant> cast) {
            this.id = id;
            this.topic = topic;
            this.cast = cast;
        }

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public NpcConversationTopic topic() {
            return topic;
        }

        @Override
        public List<UUID> cast() {
            return cast.stream().map(NpcConversationParticipant::actorId).toList();
        }

        @Override
        public NpcConversationSnapshot snapshot() {
            synchronized (StandardNpcConversationRegistry.this) {
                return new NpcConversationSnapshot(
                        state,
                        cast(),
                        Optional.ofNullable(speaker).map(NpcConversationParticipant::actorId),
                        Optional.ofNullable(currentLine),
                        Optional.ofNullable(stagingMode),
                        completedTurns,
                        plannedTurns,
                        revision
                );
            }
        }

        @Override
        public boolean registered() {
            return !closed;
        }

        @Override
        public void unregister() {
            synchronized (StandardNpcConversationRegistry.this) {
                StandardNpcConversationRegistry.this.unregister(this);
            }
        }
    }

    private static final class ShuffleBag<T> {
        private final List<T> source;
        private final NpcBehaviorRandom random;
        private final Deque<T> bag = new ArrayDeque<>();

        private ShuffleBag(Collection<? extends T> source, NpcBehaviorRandom random) {
            this.source = List.copyOf(source);
            if (this.source.isEmpty()) {
                throw new IllegalArgumentException("shuffle bag source must not be empty");
            }
            this.random = random;
        }

        private T draw() {
            if (bag.isEmpty()) {
                List<T> values = new ArrayList<>(source);
                for (int index = values.size() - 1; index > 0; index--) {
                    int swap = random.nextInt(0, index + 1);
                    T value = values.get(index);
                    values.set(index, values.get(swap));
                    values.set(swap, value);
                }
                bag.addAll(values);
            }
            return bag.removeFirst();
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
