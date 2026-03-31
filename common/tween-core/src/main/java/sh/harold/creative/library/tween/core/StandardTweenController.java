package sh.harold.creative.library.tween.core;

import net.kyori.adventure.key.Key;
import sh.harold.creative.library.tick.InstanceConflictPolicy;
import sh.harold.creative.library.tick.TickMath;
import sh.harold.creative.library.tween.HoldBehavior;
import sh.harold.creative.library.tween.RepeatMode;
import sh.harold.creative.library.tween.RepeatSpec;
import sh.harold.creative.library.tween.Tween;
import sh.harold.creative.library.tween.TweenController;
import sh.harold.creative.library.tween.TweenHandle;
import sh.harold.creative.library.tween.TweenSample;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class StandardTweenController<T> implements TweenController<T> {

    private final Map<Key, ActiveTween<T>> activeByKey = new LinkedHashMap<>();

    private long currentTick;
    private long nextGeneration = 1L;
    private boolean closed;

    @Override
    public TweenHandle start(Tween<T> tween) {
        ensureOpen();
        Tween<T> value = Objects.requireNonNull(tween, "tween");
        ActiveTween<T> existing = activeByKey.get(value.key());
        if (existing != null) {
            if (value.conflictPolicy() == InstanceConflictPolicy.REJECT) {
                return new Handle(value.key(), 0L);
            }
            if (value.conflictPolicy() == InstanceConflictPolicy.REFRESH) {
                activeByKey.put(value.key(), new ActiveTween<>(value, existing.generation(), currentTick));
                return new Handle(value.key(), existing.generation());
            }
        }
        long generation = nextGeneration++;
        ActiveTween<T> active = new ActiveTween<>(value, generation, currentTick);
        activeByKey.put(value.key(), active);
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
    public boolean pause(Key key) {
        ensureOpen();
        ActiveTween<T> active = activeByKey.get(Objects.requireNonNull(key, "key"));
        if (active == null || active.paused()) {
            return false;
        }
        active.pause(currentTick);
        return true;
    }

    @Override
    public boolean resume(Key key) {
        ensureOpen();
        ActiveTween<T> active = activeByKey.get(Objects.requireNonNull(key, "key"));
        if (active == null || !active.paused()) {
            return false;
        }
        active.resume(currentTick);
        return true;
    }

    @Override
    public List<TweenSample<T>> tick() {
        ensureOpen();
        List<TweenSample<T>> samples = new ArrayList<>(activeByKey.size());
        List<Key> completed = new ArrayList<>();
        for (ActiveTween<T> active : activeByKey.values()) {
            TweenSample<T> sample = active.sampleAt(currentTick);
            if (sample != null) {
                samples.add(sample);
            }
            if (active.finishedAt(currentTick)) {
                completed.add(active.tween().key());
            }
        }
        for (Key key : completed) {
            activeByKey.remove(key);
        }
        currentTick++;
        return List.copyOf(samples);
    }

    @Override
    public boolean hasActiveTweens() {
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
        ActiveTween<T> active = activeByKey.get(key);
        return active != null && active.generation() == generation;
    }

    private void cancel(Key key, long generation) {
        if (closed) {
            return;
        }
        ActiveTween<T> active = activeByKey.get(key);
        if (active != null && active.generation() == generation) {
            activeByKey.remove(key);
        }
    }

    private void pause(Key key, long generation) {
        if (closed) {
            return;
        }
        ActiveTween<T> active = activeByKey.get(key);
        if (active != null && active.generation() == generation && !active.paused()) {
            active.pause(currentTick);
        }
    }

    private void resume(Key key, long generation) {
        if (closed) {
            return;
        }
        ActiveTween<T> active = activeByKey.get(key);
        if (active != null && active.generation() == generation && active.paused()) {
            active.resume(currentTick);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Tween controller is closed");
        }
    }

    private final class Handle implements TweenHandle {

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
            return StandardTweenController.this.active(key, generation);
        }

        @Override
        public void pause() {
            StandardTweenController.this.pause(key, generation);
        }

        @Override
        public void resume() {
            StandardTweenController.this.resume(key, generation);
        }

        @Override
        public void cancel() {
            StandardTweenController.this.cancel(key, generation);
        }
    }

    private static final class ActiveTween<T> {

        private final Tween<T> tween;
        private final long generation;
        private final long startTick;

        private long pausedAt = -1L;
        private long pausedTicks;
        private TweenSample<T> lastSample;

        private ActiveTween(Tween<T> tween, long generation, long startTick) {
            this.tween = tween;
            this.generation = generation;
            this.startTick = startTick;
        }

        private Tween<T> tween() {
            return tween;
        }

        private long generation() {
            return generation;
        }

        private boolean paused() {
            return pausedAt >= 0L;
        }

        private void pause(long currentTick) {
            pausedAt = currentTick;
        }

        private void resume(long currentTick) {
            pausedTicks += currentTick - pausedAt;
            pausedAt = -1L;
        }

        private TweenSample<T> sampleAt(long currentTick) {
            if (paused()) {
                return lastSample;
            }
            long effectiveLocalTick = TickMath.localTick(currentTick, startTick) - pausedTicks;
            if (effectiveLocalTick < tween.delayTicks()) {
                if (tween.holdBehavior() == HoldBehavior.EMIT_FROM_VALUE) {
                    lastSample = new TweenSample<>(tween.key(), tween.from(), 0.0, 0.0, false);
                    return lastSample;
                }
                lastSample = null;
                return null;
            }

            long activeTick = Math.max(0L, effectiveLocalTick - tween.delayTicks());
            Cycle cycle = resolveCycle(activeTick, tween.durationTicks(), tween.repeat());
            if (cycle == null) {
                lastSample = null;
                return null;
            }

            double rawProgress;
            if (cycle.terminal()) {
                rawProgress = 1.0;
            } else {
                rawProgress = TickMath.progress(cycle.cycleTick(), tween.durationTicks());
            }
            if (cycle.reverse()) {
                rawProgress = 1.0 - rawProgress;
            }
            double eased = tween.easing().apply(rawProgress);
            T value = tween.interpolator().interpolate(tween.from(), tween.to(), eased);
            double strength = tween.envelope().sample(cycle.cycleTick(), tween.durationTicks());
            lastSample = new TweenSample<>(tween.key(), value, rawProgress, strength, cycle.terminal());
            return lastSample;
        }

        private boolean finishedAt(long currentTick) {
            if (paused()) {
                return false;
            }
            long effectiveLocalTick = TickMath.localTick(currentTick, startTick) - pausedTicks;
            if (effectiveLocalTick < tween.delayTicks()) {
                return false;
            }
            long activeTick = Math.max(0L, effectiveLocalTick - tween.delayTicks());
            Cycle cycle = resolveCycle(activeTick, tween.durationTicks(), tween.repeat());
            return cycle != null && cycle.terminal();
        }

        private static Cycle resolveCycle(long activeTick, long durationTicks, RepeatSpec repeat) {
            if (durationTicks == 0L) {
                return new Cycle(0L, false, true);
            }
            long cycleIndex = activeTick / durationTicks;
            long cycleTick = activeTick % durationTicks;
            if (!repeat.infinite()) {
                long maxCycles = 1L + repeat.repeats();
                if (cycleIndex >= maxCycles) {
                    return new Cycle(durationTicks, repeat.mode() == RepeatMode.PING_PONG && ((maxCycles - 1L) % 2L == 1L), true);
                }
            }
            boolean reverse = repeat.mode() == RepeatMode.PING_PONG && (cycleIndex % 2L == 1L);
            return new Cycle(cycleTick, reverse, false);
        }
    }

    private record Cycle(long cycleTick, boolean reverse, boolean terminal) {
    }
}
