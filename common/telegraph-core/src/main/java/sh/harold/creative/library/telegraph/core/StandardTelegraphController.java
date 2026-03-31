package sh.harold.creative.library.telegraph.core;

import net.kyori.adventure.key.Key;
import sh.harold.creative.library.spatial.AnchorResolver;
import sh.harold.creative.library.spatial.AnchorSnapshot;
import sh.harold.creative.library.telegraph.TelegraphController;
import sh.harold.creative.library.telegraph.TelegraphFrame;
import sh.harold.creative.library.telegraph.TelegraphSpec;
import sh.harold.creative.library.tick.InstanceConflictPolicy;
import sh.harold.creative.library.tick.KeyedHandle;
import sh.harold.creative.library.tick.TickMath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class StandardTelegraphController implements TelegraphController {

    private final Map<Key, ActiveTelegraph> activeByKey = new LinkedHashMap<>();

    private long currentTick;
    private long nextGeneration = 1L;
    private boolean closed;

    @Override
    public KeyedHandle start(TelegraphSpec spec) {
        ensureOpen();
        TelegraphSpec value = Objects.requireNonNull(spec, "spec");
        ActiveTelegraph existing = activeByKey.get(value.key());
        if (existing != null) {
            if (value.conflictPolicy() == InstanceConflictPolicy.REJECT) {
                return new Handle(value.key(), 0L);
            }
            if (value.conflictPolicy() == InstanceConflictPolicy.REFRESH) {
                activeByKey.put(value.key(), new ActiveTelegraph(value, existing.generation, currentTick));
                return new Handle(value.key(), existing.generation);
            }
        }
        long generation = nextGeneration++;
        activeByKey.put(value.key(), new ActiveTelegraph(value, generation, currentTick));
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
    public List<TelegraphFrame> tick(AnchorResolver anchorResolver) {
        ensureOpen();
        AnchorResolver resolver = Objects.requireNonNull(anchorResolver, "anchorResolver");
        List<TelegraphFrame> frames = new ArrayList<>(activeByKey.size());
        List<Key> remove = new ArrayList<>();
        for (Map.Entry<Key, ActiveTelegraph> entry : activeByKey.entrySet()) {
            ActiveTelegraph active = entry.getValue();
            Optional<AnchorSnapshot> snapshot = resolver.resolve(active.spec.anchor());
            if (snapshot.isEmpty()) {
                remove.add(entry.getKey());
                continue;
            }
            TelegraphFrame frame = active.frameAt(currentTick, snapshot.get());
            if (frame == null) {
                remove.add(entry.getKey());
                continue;
            }
            frames.add(frame);
        }
        remove.forEach(activeByKey::remove);
        currentTick++;
        return List.copyOf(frames);
    }

    @Override
    public boolean hasActiveTelegraphs() {
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
        ActiveTelegraph active = activeByKey.get(key);
        return active != null && active.generation == generation;
    }

    private void cancel(Key key, long generation) {
        if (closed) {
            return;
        }
        ActiveTelegraph active = activeByKey.get(key);
        if (active != null && active.generation == generation) {
            activeByKey.remove(key);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Telegraph controller is closed");
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
            return StandardTelegraphController.this.active(key, generation);
        }

        @Override
        public void close() {
            StandardTelegraphController.this.cancel(key, generation);
        }
    }

    private static final class ActiveTelegraph {

        private final TelegraphSpec spec;
        private final long generation;
        private final long startedAtTick;

        private ActiveTelegraph(TelegraphSpec spec, long generation, long startedAtTick) {
            this.spec = spec;
            this.generation = generation;
            this.startedAtTick = startedAtTick;
        }

        private TelegraphFrame frameAt(long currentTick, AnchorSnapshot anchor) {
            long age = TickMath.localTick(currentTick, startedAtTick);
            if (age >= spec.timing().totalDurationTicks()) {
                return null;
            }
            double alpha = sampleAlpha(age, spec.timing());
            return new TelegraphFrame(
                    spec.key(),
                    anchor,
                    spec.shape(),
                    spec.viewerScope(),
                    alpha,
                    spec.timing().baseThickness(),
                    spec.priority(),
                    age
            );
        }

        private static double sampleAlpha(long age, sh.harold.creative.library.telegraph.TelegraphTiming timing) {
            if (timing.fadeInTicks() > 0L && age < timing.fadeInTicks()) {
                return timing.baseAlpha() * ((age + 1.0) / timing.fadeInTicks());
            }
            if (age < timing.fadeInTicks() + timing.holdTicks()) {
                return timing.baseAlpha();
            }
            if (timing.fadeOutTicks() > 0L) {
                long fadeAge = age - timing.fadeInTicks() - timing.holdTicks();
                return timing.baseAlpha() * Math.max(0.0, 1.0 - (fadeAge / (double) timing.fadeOutTicks()));
            }
            return 0.0;
        }
    }
}
