package sh.harold.creative.library.trajectory.core;

import net.kyori.adventure.key.Key;
import sh.harold.creative.library.spatial.Angle;
import sh.harold.creative.library.spatial.Vec3;
import sh.harold.creative.library.tick.InstanceConflictPolicy;
import sh.harold.creative.library.tick.KeyedHandle;
import sh.harold.creative.library.tick.TickMath;
import sh.harold.creative.library.trajectory.PreviewRecomputePolicy;
import sh.harold.creative.library.trajectory.TrajectoryMotion;
import sh.harold.creative.library.trajectory.TrajectoryPreviewController;
import sh.harold.creative.library.trajectory.TrajectoryPreviewResult;
import sh.harold.creative.library.trajectory.TrajectoryPreviewSnapshot;
import sh.harold.creative.library.trajectory.TrajectoryPreviewSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class StandardTrajectoryPreviewController implements TrajectoryPreviewController {

    private final StandardTrajectorySolver solver = new StandardTrajectorySolver();
    private final Map<Key, ActivePreview> activeByKey = new LinkedHashMap<>();

    private long currentTick;
    private long nextGeneration = 1L;
    private boolean closed;

    @Override
    public KeyedHandle start(TrajectoryPreviewSpec spec) {
        ensureOpen();
        TrajectoryPreviewSpec value = Objects.requireNonNull(spec, "spec");
        ActivePreview existing = activeByKey.get(value.key());
        if (existing != null) {
            return switch (value.conflictPolicy()) {
                case REJECT -> new Handle(value.key(), 0L);
                case REFRESH -> {
                    existing.refresh(value, currentTick);
                    yield new Handle(value.key(), existing.generation);
                }
                case REPLACE -> replace(value);
            };
        }
        return replace(value);
    }

    @Override
    public boolean refresh(Key key) {
        ensureOpen();
        ActivePreview active = activeByKey.get(Objects.requireNonNull(key, "key"));
        if (active == null) {
            return false;
        }
        active.forceRefresh(currentTick);
        return true;
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
    public List<TrajectoryPreviewSnapshot> tick() {
        ensureOpen();
        List<TrajectoryPreviewSnapshot> snapshots = new ArrayList<>(activeByKey.size());
        for (ActivePreview active : activeByKey.values()) {
            snapshots.add(active.sampleAt(currentTick, solver));
        }
        currentTick++;
        return List.copyOf(snapshots);
    }

    @Override
    public boolean hasActivePreviews() {
        return !closed && !activeByKey.isEmpty();
    }

    @Override
    public void close() {
        closed = true;
        activeByKey.clear();
    }

    private KeyedHandle replace(TrajectoryPreviewSpec spec) {
        long generation = nextGeneration++;
        activeByKey.put(spec.key(), new ActivePreview(spec, generation, currentTick));
        return new Handle(spec.key(), generation);
    }

    private boolean active(Key key, long generation) {
        if (closed) {
            return false;
        }
        ActivePreview active = activeByKey.get(key);
        return active != null && active.generation == generation;
    }

    private void cancel(Key key, long generation) {
        if (closed) {
            return;
        }
        ActivePreview active = activeByKey.get(key);
        if (active != null && active.generation == generation) {
            activeByKey.remove(key);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Trajectory preview controller is closed");
        }
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
            return StandardTrajectoryPreviewController.this.active(key, generation);
        }

        @Override
        public void close() {
            StandardTrajectoryPreviewController.this.cancel(key, generation);
        }
    }

    private static final class ActivePreview {

        private TrajectoryPreviewSpec spec;
        private final long generation;
        private long startedAtTick;
        private boolean refreshRequested = true;
        private TrajectoryMotion lastMotion;
        private TrajectoryPreviewResult lastResult;

        private ActivePreview(TrajectoryPreviewSpec spec, long generation, long startedAtTick) {
            this.spec = spec;
            this.generation = generation;
            this.startedAtTick = startedAtTick;
        }

        private void refresh(TrajectoryPreviewSpec spec, long currentTick) {
            this.spec = spec;
            this.startedAtTick = currentTick;
            this.refreshRequested = true;
            this.lastMotion = null;
            this.lastResult = null;
        }

        private void forceRefresh(long currentTick) {
            startedAtTick = currentTick;
            refreshRequested = true;
        }

        private TrajectoryPreviewSnapshot sampleAt(long currentTick, StandardTrajectorySolver solver) {
            TrajectoryMotion motion = Objects.requireNonNull(spec.motionSource().currentMotion(), "motionSource.currentMotion()");
            boolean recompute = shouldRecompute(motion);
            if (recompute) {
                lastResult = solver.solve(motion, spec.collisionQuery());
                lastMotion = motion;
                refreshRequested = false;
            }
            return new TrajectoryPreviewSnapshot(
                    spec.key(),
                    spec.scope(),
                    Objects.requireNonNull(lastResult, "lastResult"),
                    recompute,
                    TickMath.localTick(currentTick, startedAtTick)
            );
        }

        private boolean shouldRecompute(TrajectoryMotion motion) {
            if (refreshRequested || lastResult == null || lastMotion == null) {
                return true;
            }
            return switch (spec.recomputePolicy()) {
                case PreviewRecomputePolicy.OneShot ignored -> false;
                case PreviewRecomputePolicy.EveryTick ignored -> true;
                case PreviewRecomputePolicy.Thresholded thresholded -> exceededThreshold(motion, thresholded);
            };
        }

        private boolean exceededThreshold(TrajectoryMotion motion, PreviewRecomputePolicy.Thresholded thresholded) {
            if (!Objects.equals(lastMotion.acceleration(), motion.acceleration())
                    || !Objects.equals(lastMotion.drag(), motion.drag())
                    || lastMotion.collisionRadius() != motion.collisionRadius()
                    || lastMotion.maxSimulationTicks() != motion.maxSimulationTicks()
                    || lastMotion.collisionResponse() != motion.collisionResponse()) {
                return true;
            }
            if (lastMotion.initialPosition().distance(motion.initialPosition()) >= thresholded.originDistanceThreshold()) {
                return true;
            }
            return velocityChanged(lastMotion.initialVelocity(), motion.initialVelocity(), thresholded.aimDirectionThreshold());
        }

        private static boolean velocityChanged(Vec3 previous, Vec3 current, Angle aimDirectionThreshold) {
            if (previous.equals(current)) {
                return false;
            }
            if (previous.length() != current.length()) {
                return true;
            }
            Vec3 previousNormalized = previous.normalize();
            Vec3 currentNormalized = current.normalize();
            if (previousNormalized.equals(Vec3.ZERO) || currentNormalized.equals(Vec3.ZERO)) {
                return !previousNormalized.equals(currentNormalized);
            }
            double dot = clamp(previousNormalized.dot(currentNormalized), -1.0, 1.0);
            double angle = Math.acos(dot);
            return angle >= aimDirectionThreshold.radians();
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
