package sh.harold.library.npc.behavior.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Thread-safe acquisition stack for natural, interaction-refreshed, and leased
 * attention. It intentionally contains no platform objects.
 */
public final class NpcAttentionStack {

    private final Policy policy;
    private final Map<UUID, MutableSession> sessions = new LinkedHashMap<>();
    private final Deque<Event> events = new ArrayDeque<>();
    private long sequence;
    private long leaseSequence;

    public NpcAttentionStack(Policy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public synchronized void observe(Observation observation) {
        observe(observation, true);
    }

    /**
     * Applies distance/space/tracking every time, while counting LOS
     * hysteresis only when the platform publishes a newly completed probe.
     */
    public synchronized void observe(Observation observation, boolean freshLineOfSightProbe) {
        Objects.requireNonNull(observation, "observation");
        UUID viewerId = observation.viewerId();
        MutableSession session = sessions.get(viewerId);

        if (!observation.tracked() || (policy.sameSpaceRequired() && !observation.sameSpace())) {
            retire(viewerId, ReleaseReason.UNTRACKED);
            return;
        }

        boolean outsideExit = observation.horizontalDistanceSquared() > square(policy.exitRadius())
                || observation.verticalDifference() > policy.maximumVerticalDifference();
        if (outsideExit) {
            releaseNatural(viewerId, session, ReleaseReason.EXIT_BOUNDARY);
            return;
        }

        if (session == null) {
            boolean insideEnter = observation.horizontalDistanceSquared() <= square(policy.enterRadius())
                    && observation.verticalDifference() <= policy.maximumVerticalDifference();
            if (insideEnter && acquisitionLineOfSightPasses(observation, freshLineOfSightProbe)) {
                session = new MutableSession(viewerId, ++sequence, observation.target(), true);
                sessions.put(viewerId, session);
                events.addLast(new Event(EventType.ACQUIRED, viewerId, AcquisitionReason.PROXIMITY));
                events.addLast(new Event(EventType.CANONICAL_CHANGED, viewerId, AcquisitionReason.PROXIMITY));
            }
            return;
        }

        session.target = observation.target();
        if (!session.natural) {
            boolean insideEnter = observation.horizontalDistanceSquared() <= square(policy.enterRadius())
                    && observation.verticalDifference() <= policy.maximumVerticalDifference();
            if (insideEnter && acquisitionLineOfSightPasses(observation, freshLineOfSightProbe)) {
                session.natural = true;
                session.lineOfSightFailures = 0;
                session.acknowledgementLatched = false;
                session.promotion = ++sequence;
                events.addLast(new Event(EventType.ACQUIRED, viewerId, AcquisitionReason.PROXIMITY));
                events.addLast(new Event(EventType.CANONICAL_CHANGED, viewerId, AcquisitionReason.PROXIMITY));
            }
            return;
        }
        if (freshLineOfSightProbe) {
            if (lineOfSightPasses(observation)) {
                session.lineOfSightFailures = 0;
            } else if (++session.lineOfSightFailures >= policy.lineOfSightReleaseFailures()) {
                releaseNatural(viewerId, session, ReleaseReason.LINE_OF_SIGHT);
            }
        }
    }

    /** Direct use/attack promotes the session and refreshes its acquisition act. */
    public synchronized void interaction(UUID viewerId, GazeTarget target) {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(target, "target");
        MutableSession session = sessions.get(viewerId);
        if (session == null) {
            session = new MutableSession(viewerId, ++sequence, target, true);
            sessions.put(viewerId, session);
            events.addLast(new Event(EventType.ACQUIRED, viewerId, AcquisitionReason.INTERACTION));
        } else {
            session.natural = true;
            session.target = target;
            session.lineOfSightFailures = 0;
            session.acknowledgementLatched = false;
            session.promotion = ++sequence;
            events.addLast(new Event(EventType.REFRESHED, viewerId, AcquisitionReason.INTERACTION));
        }
        promoteCanonical(viewerId, AcquisitionReason.INTERACTION);
    }

    public synchronized Lease lease(UUID viewerId, GazeTarget target) {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(target, "target");
        long token = ++leaseSequence;
        MutableSession session = sessions.get(viewerId);
        boolean newlyAcquired = session == null;
        if (newlyAcquired) {
            session = new MutableSession(viewerId, ++sequence, target, false);
            sessions.put(viewerId, session);
            events.addLast(new Event(EventType.ACQUIRED, viewerId, AcquisitionReason.MANUAL_LEASE));
        } else {
            session.target = target;
        }
        session.leases.add(token);
        promoteCanonical(viewerId, AcquisitionReason.MANUAL_LEASE);
        return new Lease(this, viewerId, token);
    }

    /** Marks ACKNOWLEDGE as already delivered until this session exits. */
    public synchronized boolean latchAcknowledgement(UUID viewerId) {
        MutableSession session = sessions.get(viewerId);
        if (session == null || session.acknowledgementLatched) {
            return false;
        }
        session.acknowledgementLatched = true;
        return true;
    }

    public synchronized void retire(UUID viewerId, ReleaseReason reason) {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(reason, "reason");
        UUID before = canonicalId();
        MutableSession removed = sessions.remove(viewerId);
        if (removed == null) {
            return;
        }
        events.addLast(new Event(EventType.RELEASED, viewerId, null, reason));
        emitFallbackIfChanged(before);
    }

    public synchronized Snapshot snapshot() {
        UUID canonical = canonicalId();
        List<Session> immutableSessions = sessions.values().stream()
                .sorted(Comparator.comparingLong(session -> session.promotion))
                .map(session -> new Session(
                        session.viewerId,
                        session.promotion,
                        session.target,
                        session.natural,
                        session.leases.size(),
                        session.lineOfSightFailures,
                        session.acknowledgementLatched,
                        session.viewerId.equals(canonical)
                ))
                .toList();
        return new Snapshot(Optional.ofNullable(canonical), immutableSessions);
    }

    public synchronized List<Event> drainEvents() {
        List<Event> drained = List.copyOf(events);
        events.clear();
        return drained;
    }

    public synchronized int size() {
        return sessions.size();
    }

    public synchronized boolean contains(UUID viewerId) {
        return sessions.containsKey(Objects.requireNonNull(viewerId, "viewerId"));
    }

    private void releaseNatural(UUID viewerId, MutableSession session, ReleaseReason reason) {
        if (session == null || !session.natural) {
            return;
        }
        if (!session.leases.isEmpty()) {
            session.natural = false;
            session.lineOfSightFailures = 0;
            return;
        }
        retire(viewerId, reason);
    }

    private boolean lineOfSightPasses(Observation observation) {
        return !policy.lineOfSightRequired() || observation.lineOfSight();
    }

    private boolean acquisitionLineOfSightPasses(
            Observation observation,
            boolean freshLineOfSightProbe
    ) {
        return !policy.lineOfSightRequired()
                || (freshLineOfSightProbe && observation.lineOfSight());
    }

    private void closeLease(UUID viewerId, long token) {
        synchronized (this) {
            MutableSession session = sessions.get(viewerId);
            if (session == null || !session.leases.remove(token)) {
                return;
            }
            if (!session.natural && session.leases.isEmpty()) {
                retire(viewerId, ReleaseReason.LEASE_CLOSED);
            }
        }
    }

    private void promoteCanonical(UUID viewerId, AcquisitionReason reason) {
        MutableSession session = sessions.get(viewerId);
        if (session == null) {
            return;
        }
        UUID before = canonicalId();
        session.promotion = ++sequence;
        if (!viewerId.equals(before)) {
            events.addLast(new Event(EventType.CANONICAL_CHANGED, viewerId, reason));
        }
    }

    private void emitFallbackIfChanged(UUID before) {
        UUID after = canonicalId();
        if (!Objects.equals(before, after)) {
            events.addLast(new Event(EventType.CANONICAL_CHANGED, after, AcquisitionReason.FALLBACK));
        }
    }

    private UUID canonicalId() {
        MutableSession latest = null;
        for (MutableSession candidate : sessions.values()) {
            if (latest == null || candidate.promotion > latest.promotion) {
                latest = candidate;
            }
        }
        return latest == null ? null : latest.viewerId;
    }

    private static double square(double value) {
        return value * value;
    }

    public record Policy(
            double enterRadius,
            double exitRadius,
            double maximumVerticalDifference,
            boolean sameSpaceRequired,
            boolean lineOfSightRequired,
            int lineOfSightReleaseFailures
    ) {
        public Policy {
            requireFinitePositive(enterRadius, "enterRadius");
            requireFinitePositive(exitRadius, "exitRadius");
            requireFiniteNonNegative(maximumVerticalDifference, "maximumVerticalDifference");
            if (exitRadius < enterRadius) {
                throw new IllegalArgumentException("exitRadius must be at least enterRadius");
            }
            if (lineOfSightReleaseFailures < 1) {
                throw new IllegalArgumentException("lineOfSightReleaseFailures must be positive");
            }
        }

        public static Policy defaults() {
            return new Policy(6.0, 8.0, 4.0, true, true, 3);
        }

        private static void requireFinitePositive(double value, String name) {
            if (!Double.isFinite(value) || value <= 0.0) {
                throw new IllegalArgumentException(name + " must be finite and positive");
            }
        }

        private static void requireFiniteNonNegative(double value, String name) {
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException(name + " must be finite and non-negative");
            }
        }
    }

    public record Observation(
            UUID viewerId,
            boolean tracked,
            boolean sameSpace,
            double horizontalDistanceSquared,
            double verticalDifference,
            boolean lineOfSight,
            GazeTarget target
    ) {
        public Observation {
            viewerId = Objects.requireNonNull(viewerId, "viewerId");
            if (!Double.isFinite(horizontalDistanceSquared) || horizontalDistanceSquared < 0.0) {
                throw new IllegalArgumentException("horizontalDistanceSquared must be finite and non-negative");
            }
            if (!Double.isFinite(verticalDifference) || verticalDifference < 0.0) {
                throw new IllegalArgumentException("verticalDifference must be finite and non-negative");
            }
            target = Objects.requireNonNull(target, "target");
        }
    }

    public record GazeTarget(float yaw, float pitch) {
        public GazeTarget {
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                throw new IllegalArgumentException("yaw and pitch must be finite");
            }
        }
    }

    public record Session(
            UUID viewerId,
            long promotion,
            GazeTarget target,
            boolean naturallyAcquired,
            int leaseCount,
            int lineOfSightFailures,
            boolean acknowledgementLatched,
            boolean canonical
    ) {
        public Session {
            viewerId = Objects.requireNonNull(viewerId, "viewerId");
            target = Objects.requireNonNull(target, "target");
        }
    }

    public record Snapshot(Optional<UUID> canonicalViewer, List<Session> sessions) {
        public Snapshot {
            canonicalViewer = Objects.requireNonNull(canonicalViewer, "canonicalViewer");
            sessions = List.copyOf(Objects.requireNonNull(sessions, "sessions"));
        }

        public Optional<Session> session(UUID viewerId) {
            return sessions.stream().filter(session -> session.viewerId().equals(viewerId)).findFirst();
        }

        public Set<UUID> overlayViewers() {
            if (sessions.size() < 2) {
                return Set.of();
            }
            Set<UUID> viewers = new LinkedHashSet<>();
            for (Session session : sessions) {
                if (!session.canonical()) {
                    viewers.add(session.viewerId());
                }
            }
            return Set.copyOf(viewers);
        }
    }

    public record Event(
            EventType type,
            UUID viewerId,
            AcquisitionReason acquisitionReason,
            ReleaseReason releaseReason
    ) {
        public Event(EventType type, UUID viewerId, AcquisitionReason acquisitionReason) {
            this(type, viewerId, acquisitionReason, null);
        }

        public Event {
            type = Objects.requireNonNull(type, "type");
        }
    }

    public enum EventType {
        ACQUIRED,
        REFRESHED,
        RELEASED,
        CANONICAL_CHANGED
    }

    public enum AcquisitionReason {
        PROXIMITY,
        INTERACTION,
        MANUAL_LEASE,
        FALLBACK
    }

    public enum ReleaseReason {
        EXIT_BOUNDARY,
        LINE_OF_SIGHT,
        UNTRACKED,
        LEASE_CLOSED,
        PROFILE_REPLACED,
        DESPAWNED,
        PLATFORM_CLOSED
    }

    public static final class Lease implements AutoCloseable {
        private NpcAttentionStack owner;
        private final UUID viewerId;
        private final long token;

        private Lease(NpcAttentionStack owner, UUID viewerId, long token) {
            this.owner = owner;
            this.viewerId = viewerId;
            this.token = token;
        }

        @Override
        public void close() {
            NpcAttentionStack current = owner;
            if (current != null) {
                owner = null;
                current.closeLease(viewerId, token);
            }
        }
    }

    private static final class MutableSession {
        private final UUID viewerId;
        private final Set<Long> leases = new LinkedHashSet<>();
        private long promotion;
        private GazeTarget target;
        private boolean natural;
        private int lineOfSightFailures;
        private boolean acknowledgementLatched;

        private MutableSession(UUID viewerId, long promotion, GazeTarget target, boolean natural) {
            this.viewerId = viewerId;
            this.promotion = promotion;
            this.target = target;
            this.natural = natural;
        }
    }
}
