package sh.harold.creative.library.impulse.core;

import net.kyori.adventure.key.Key;
import sh.harold.creative.library.impulse.AxisMask;
import sh.harold.creative.library.impulse.ComposedImpulse;
import sh.harold.creative.library.impulse.ImpulseActorState;
import sh.harold.creative.library.impulse.ImpulseController;
import sh.harold.creative.library.impulse.ImpulseMode;
import sh.harold.creative.library.impulse.ImpulseSpec;
import sh.harold.creative.library.impulse.ImpulseStackMode;
import sh.harold.creative.library.impulse.ImpulseVector;
import sh.harold.creative.library.spatial.Frame3;
import sh.harold.creative.library.spatial.Vec3;
import sh.harold.creative.library.tick.InstanceConflictPolicy;
import sh.harold.creative.library.tick.KeyedHandle;
import sh.harold.creative.library.tick.TickMath;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class StandardImpulseController implements ImpulseController {

    private final Map<Key, ActiveImpulse> activeByKey = new LinkedHashMap<>();

    private long currentTick;
    private long nextGeneration = 1L;
    private boolean closed;

    @Override
    public KeyedHandle start(ImpulseSpec spec) {
        ensureOpen();
        ImpulseSpec value = Objects.requireNonNull(spec, "spec");
        ActiveImpulse existing = activeByKey.get(value.key());
        if (existing != null) {
            if (value.conflictPolicy() == InstanceConflictPolicy.REJECT) {
                return new Handle(value.key(), 0L);
            }
            if (value.conflictPolicy() == InstanceConflictPolicy.REFRESH) {
                activeByKey.put(value.key(), new ActiveImpulse(value, existing.generation, currentTick));
                return new Handle(value.key(), existing.generation);
            }
        }
        long generation = nextGeneration++;
        activeByKey.put(value.key(), new ActiveImpulse(value, generation, currentTick));
        return new Handle(value.key(), generation);
    }

    @Override
    public boolean stop(Key key) {
        ensureOpen();
        return activeByKey.remove(Objects.requireNonNull(key, "key")) != null;
    }

    @Override
    public void stopAll() {
        ensureOpen();
        activeByKey.clear();
    }

    @Override
    public ComposedImpulse tick(ImpulseActorState actorState) {
        ensureOpen();
        ImpulseActorState state = Objects.requireNonNull(actorState, "actorState");
        Vec3 additive = Vec3.ZERO;
        ResolvedAbsolute absolute = null;
        Map<Key, ActiveImpulse> expired = new LinkedHashMap<>();
        for (Map.Entry<Key, ActiveImpulse> entry : activeByKey.entrySet()) {
            ActiveImpulse active = entry.getValue();
            if (active.expiredAt(currentTick)) {
                expired.put(entry.getKey(), active);
                continue;
            }
            Optional<Vec3> sample = active.sample(state, currentTick);
            if (sample.isEmpty()) {
                continue;
            }
            Vec3 vector = sample.get();
            if (usesAbsoluteVelocity(active.spec.mode())) {
                ResolvedAbsolute candidate = new ResolvedAbsolute(vector, active.spec.priority(), active.startedAtTick);
                if (absolute == null || candidate.compareTo(absolute) > 0) {
                    absolute = candidate;
                }
            } else {
                additive = combine(additive, vector, active.spec.stackMode());
            }
        }
        expired.keySet().forEach(activeByKey::remove);
        currentTick++;
        return new ComposedImpulse(additive, absolute == null ? Optional.empty() : Optional.of(absolute.vector()));
    }

    @Override
    public boolean hasActiveImpulses() {
        return !closed && !activeByKey.isEmpty();
    }

    @Override
    public void close() {
        closed = true;
        activeByKey.clear();
    }

    private Vec3 combine(Vec3 current, Vec3 candidate, ImpulseStackMode stackMode) {
        return switch (stackMode) {
            case ADD -> current.add(candidate);
            case MAX_MAGNITUDE_PER_AXIS -> new Vec3(
                    maxMagnitudeAxis(current.x(), candidate.x()),
                    maxMagnitudeAxis(current.y(), candidate.y()),
                    maxMagnitudeAxis(current.z(), candidate.z())
            );
            case MAX_MAGNITUDE_VECTOR -> candidate.lengthSquared() > current.lengthSquared() ? candidate : current;
            case PRIORITY_WINNER -> candidate;
        };
    }

    private boolean active(Key key, long generation) {
        if (closed) {
            return false;
        }
        ActiveImpulse active = activeByKey.get(key);
        return active != null && active.generation == generation;
    }

    private void cancel(Key key, long generation) {
        if (closed) {
            return;
        }
        ActiveImpulse active = activeByKey.get(key);
        if (active != null && active.generation == generation) {
            activeByKey.remove(key);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Impulse controller is closed");
        }
    }

    private static boolean usesAbsoluteVelocity(ImpulseMode mode) {
        return mode == ImpulseMode.SET_VELOCITY
                || mode == ImpulseMode.DASH_TOWARD_DIRECTION
                || mode == ImpulseMode.UPWARD_LAUNCH;
    }

    private static double maxMagnitudeAxis(double current, double candidate) {
        return Math.abs(candidate) > Math.abs(current) ? candidate : current;
    }

    private final class Handle implements KeyedHandle {

        private final Key key;
        private final long generation;

        private Handle(Key key, long generation) {
            this.key = key;
            this.generation = generation;
        }

        @Override
        public Key key() {
            return key;
        }

        @Override
        public boolean active() {
            return StandardImpulseController.this.active(key, generation);
        }

        @Override
        public void close() {
            StandardImpulseController.this.cancel(key, generation);
        }
    }

    private static final class ActiveImpulse {

        private final ImpulseSpec spec;
        private final long generation;
        private final long startedAtTick;

        private ActiveImpulse(ImpulseSpec spec, long generation, long startedAtTick) {
            this.spec = spec;
            this.generation = generation;
            this.startedAtTick = startedAtTick;
        }

        private Optional<Vec3> sample(ImpulseActorState state, long currentTick) {
            long localTick = TickMath.localTick(currentTick, startedAtTick);
            if (localTick < spec.delayTicks()) {
                return Optional.empty();
            }
            long activeTick = localTick - spec.delayTicks();
            if (activeTick > spec.durationTicks()) {
                return Optional.empty();
            }
            Vec3 base = resolveVector(spec.vector(), state);
            double strength = spec.envelope().sample(activeTick, spec.durationTicks());
            Vec3 masked = spec.axisMask().apply(base.multiply(strength));
            if (spec.mode() == ImpulseMode.CLAMPED_ADD && spec.maxMagnitude() > 0.0 && masked.length() > spec.maxMagnitude()) {
                masked = masked.normalize().multiply(spec.maxMagnitude());
            }
            if (spec.mode() == ImpulseMode.UPWARD_LAUNCH) {
                masked = spec.axisMask().apply(new Vec3(0.0, masked.length(), 0.0));
            }
            return Optional.of(masked);
        }

        private boolean expiredAt(long currentTick) {
            return TickMath.localTick(currentTick, startedAtTick) > spec.delayTicks() + spec.durationTicks();
        }

        private static Vec3 resolveVector(ImpulseVector vector, ImpulseActorState state) {
            return switch (vector) {
                case ImpulseVector.World world -> world.vector();
                case ImpulseVector.LocalLook localLook -> state.lookFrame().localToWorldVector(localLook.vector());
                case ImpulseVector.LocalHorizontal localHorizontal -> horizontalFrame(state.lookFrame()).localToWorldVector(localHorizontal.vector());
                case ImpulseVector.TowardPoint towardPoint ->
                        towardPoint.point().subtract(state.position()).normalize().multiply(towardPoint.strength());
                case ImpulseVector.AwayFromPoint awayFromPoint ->
                        state.position().subtract(awayFromPoint.point()).normalize().multiply(awayFromPoint.strength());
                case ImpulseVector.RadialFromOrigin radial ->
                        state.position().subtract(radial.origin()).normalize().multiply(radial.strength());
            };
        }

        private static Frame3 horizontalFrame(Frame3 frame) {
            Vec3 horizontalForward = new Vec3(frame.forward().x(), 0.0, frame.forward().z()).normalize();
            if (horizontalForward.equals(Vec3.ZERO)) {
                horizontalForward = Vec3.UNIT_Z;
            }
            return Frame3.of(frame.origin(), horizontalForward, Vec3.UNIT_Y);
        }
    }

    private record ResolvedAbsolute(Vec3 vector, int priority, long startedAtTick) implements Comparable<ResolvedAbsolute> {

        @Override
        public int compareTo(ResolvedAbsolute other) {
            int byPriority = Integer.compare(priority, other.priority);
            if (byPriority != 0) {
                return byPriority;
            }
            return Long.compare(startedAtTick, other.startedAtTick);
        }
    }
}
