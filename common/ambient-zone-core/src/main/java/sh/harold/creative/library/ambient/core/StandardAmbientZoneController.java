package sh.harold.creative.library.ambient.core;

import net.kyori.adventure.key.Key;
import sh.harold.creative.library.ambient.AmbientBlendMode;
import sh.harold.creative.library.ambient.AmbientProfile;
import sh.harold.creative.library.ambient.AmbientSnapshot;
import sh.harold.creative.library.ambient.AmbientWeightModel;
import sh.harold.creative.library.ambient.AmbientZoneController;
import sh.harold.creative.library.ambient.ViewerAmbientState;
import sh.harold.creative.library.ambient.ZoneSpec;
import sh.harold.creative.library.spatial.AnchorResolver;
import sh.harold.creative.library.spatial.AnchorSnapshot;
import sh.harold.creative.library.spatial.Vec3;
import sh.harold.creative.library.tick.InstanceConflictPolicy;
import sh.harold.creative.library.tick.KeyedHandle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class StandardAmbientZoneController implements AmbientZoneController {

    private final Map<Key, ActiveZone> activeByKey = new LinkedHashMap<>();

    private long currentTick;
    private long nextGeneration = 1L;
    private boolean closed;

    @Override
    public KeyedHandle start(ZoneSpec spec) {
        ensureOpen();
        ZoneSpec value = Objects.requireNonNull(spec, "spec");
        ActiveZone existing = activeByKey.get(value.key());
        if (existing != null) {
            if (value.conflictPolicy() == InstanceConflictPolicy.REJECT) {
                return new Handle(value.key(), 0L);
            }
            if (value.conflictPolicy() == InstanceConflictPolicy.REFRESH) {
                activeByKey.put(value.key(), new ActiveZone(value, existing.generation, currentTick));
                return new Handle(value.key(), existing.generation);
            }
        }
        long generation = nextGeneration++;
        activeByKey.put(value.key(), new ActiveZone(value, generation, currentTick));
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
    public List<AmbientSnapshot> tick(List<ViewerAmbientState> viewers, AnchorResolver anchorResolver) {
        ensureOpen();
        List<ViewerAmbientState> states = List.copyOf(Objects.requireNonNull(viewers, "viewers"));
        AnchorResolver resolver = Objects.requireNonNull(anchorResolver, "anchorResolver");
        List<Key> expired = new ArrayList<>();
        List<AmbientSnapshot> snapshots = new ArrayList<>(states.size());
        for (Map.Entry<Key, ActiveZone> entry : activeByKey.entrySet()) {
            if (entry.getValue().expiredAt(currentTick)) {
                expired.add(entry.getKey());
            }
        }
        expired.forEach(activeByKey::remove);

        for (ViewerAmbientState viewer : states) {
            snapshots.add(evaluateViewer(viewer, resolver));
        }
        currentTick++;
        return List.copyOf(snapshots);
    }

    private AmbientSnapshot evaluateViewer(ViewerAmbientState viewer, AnchorResolver resolver) {
        ProfileAccumulator overlay = new ProfileAccumulator();
        ProfileAccumulator particles = new ProfileAccumulator();
        ProfileAccumulator sound = new ProfileAccumulator();
        ProfileAccumulator camera = new ProfileAccumulator();
        ProfileAccumulator border = new ProfileAccumulator();
        List<Key> contributors = new ArrayList<>();

        for (ActiveZone active : activeByKey.values()) {
            Optional<AnchorSnapshot> snapshot = resolver.resolve(active.spec.anchor());
            if (snapshot.isEmpty()) {
                continue;
            }
            if (!snapshot.get().spaceId().equals(viewer.spaceId())) {
                continue;
            }
            Vec3 local = snapshot.get().frame().worldToLocalPoint(viewer.position());
            double weight = weightAt(local, active.spec.localVolume(), active.spec.weightModel());
            if (weight <= 0.0) {
                continue;
            }
            contributors.add(active.spec.key());
            overlay.add(active.spec.profile().overlayStrength(), weight, active.spec.priority(), active.spec.blendMode());
            particles.add(active.spec.profile().particleStrength(), weight, active.spec.priority(), active.spec.blendMode());
            sound.add(active.spec.profile().soundStrength(), weight, active.spec.priority(), active.spec.blendMode());
            camera.add(active.spec.profile().cameraStrength(), weight, active.spec.priority(), active.spec.blendMode());
            border.add(active.spec.profile().borderPressure(), weight, active.spec.priority(), active.spec.blendMode());
        }

        return new AmbientSnapshot(
                viewer.viewerId(),
                new AmbientProfile(
                        overlay.value(),
                        particles.value(),
                        sound.value(),
                        camera.value(),
                        border.value()
                ),
                contributors
        );
    }

    @Override
    public boolean hasActiveZones() {
        return !closed && !activeByKey.isEmpty();
    }

    @Override
    public void close() {
        closed = true;
        activeByKey.clear();
    }

    private boolean active(Key key, long generation) {
        if (closed) {
            return false;
        }
        ActiveZone active = activeByKey.get(key);
        return active != null && active.generation == generation;
    }

    private void cancel(Key key, long generation) {
        if (closed) {
            return;
        }
        ActiveZone active = activeByKey.get(key);
        if (active != null && active.generation == generation) {
            activeByKey.remove(key);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Ambient zone controller is closed");
        }
    }

    private static double weightAt(Vec3 localPoint, sh.harold.creative.library.spatial.Volume volume, AmbientWeightModel weightModel) {
        if (volume.contains(localPoint)) {
            return 1.0;
        }
        if (weightModel.featherDistance() <= 0.0) {
            return 0.0;
        }
        double distance = volume.distance(localPoint);
        if (distance > weightModel.featherDistance()) {
            return 0.0;
        }
        double normalized = 1.0 - (distance / weightModel.featherDistance());
        return weightModel.curve().apply(normalized);
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
            return StandardAmbientZoneController.this.active(key, generation);
        }

        @Override
        public void close() {
            StandardAmbientZoneController.this.cancel(key, generation);
        }
    }

    private static final class ActiveZone {

        private final ZoneSpec spec;
        private final long generation;
        private final long startedAtTick;

        private ActiveZone(ZoneSpec spec, long generation, long startedAtTick) {
            this.spec = spec;
            this.generation = generation;
            this.startedAtTick = startedAtTick;
        }

        private boolean expiredAt(long currentTick) {
            return spec.ttlTicks() > 0L && currentTick - startedAtTick >= spec.ttlTicks();
        }
    }

    private static final class ProfileAccumulator {

        private double sum;
        private double weightSum;
        private Double max;
        private Double winnerValue;
        private int winnerPriority = Integer.MIN_VALUE;

        private void add(Double value, double weight, int priority, AmbientBlendMode blendMode) {
            if (value == null) {
                return;
            }
            double scaled = value * weight;
            switch (blendMode) {
                case MAX -> max = max == null ? scaled : Math.max(max, scaled);
                case ADD_CLAMPED -> sum = Math.min(1.0, sum + scaled);
                case PRIORITY_WINNER -> {
                    if (winnerValue == null || priority >= winnerPriority) {
                        winnerValue = scaled;
                        winnerPriority = priority;
                    }
                }
                case WEIGHTED_BLEND -> {
                    sum += value * weight;
                    weightSum += weight;
                }
            }
        }

        private Double value() {
            if (winnerValue != null) {
                return winnerValue;
            }
            if (max != null) {
                return max;
            }
            if (weightSum > 0.0) {
                return sum / weightSum;
            }
            return sum == 0.0 ? null : sum;
        }
    }
}
