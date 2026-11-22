package sh.harold.library.camera.rig.core;

import net.kyori.adventure.key.Key;
import sh.harold.library.camera.rig.cue.CameraCue;
import sh.harold.library.camera.rig.cue.CameraCueHandle;
import sh.harold.library.camera.rig.cue.ChannelCurve;
import sh.harold.library.camera.rig.cue.CueCurve;
import sh.harold.library.tween.EasingFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * The live cues. Sampling is a pure function of the frame time and the cue list (start and release times), so the
 * same time gives the same values in any order of evaluation; only {@link #prune(double)} forgets cues, and only ones
 * that no later frame can show. Guarded by the owning rig's lock.
 */
final class CueStack {

    private final Object lock;
    private final DoubleSupplier now;
    private final List<LiveCue> cues = new ArrayList<>();

    CueStack(Object lock, DoubleSupplier now) {
        this.lock = lock;
        this.now = now;
    }

    CameraCueHandle start(CameraCue cue, double startTime) {
        Objects.requireNonNull(cue, "cue");
        if (!Double.isFinite(startTime)) {
            throw new IllegalArgumentException("startTime must be finite");
        }
        synchronized (lock) {
            LiveCue existing = find(cue.key());
            if (existing != null) {
                switch (cue.conflict()) {
                    case REJECT -> {
                        return existing;
                    }
                    case REFRESH -> {
                        existing.restart(cue, startTime);
                        return existing;
                    }
                    case REPLACE -> existing.closeLocked();
                }
            }
            LiveCue live = new LiveCue(cue, startTime);
            cues.add(live);
            return live;
        }
    }

    void cancelAll(Key owner) {
        synchronized (lock) {
            for (LiveCue cue : List.copyOf(cues)) {
                if (cue.spec.owner().equals(owner)) {
                    cue.closeLocked();
                }
            }
        }
    }

    void sample(double time, boolean reduced, LayerAccumulator into) {
        synchronized (lock) {
            for (LiveCue cue : cues) {
                cue.sample(time, reduced, into);
            }
        }
    }

    /** Ends every cue that is over before {@code time}: no frame at or after {@code time} can show it. */
    void prune(double time) {
        synchronized (lock) {
            for (LiveCue cue : List.copyOf(cues)) {
                if (cue.start + cue.end() < time) {
                    cue.closeLocked();
                }
            }
        }
    }

    boolean isEmpty() {
        synchronized (lock) {
            return cues.isEmpty();
        }
    }

    void closeAll() {
        synchronized (lock) {
            for (LiveCue cue : List.copyOf(cues)) {
                cue.closeLocked();
            }
        }
    }

    private LiveCue find(Key key) {
        for (LiveCue cue : cues) {
            if (cue.spec.key().equals(key)) {
                return cue;
            }
        }
        return null;
    }

    private final class LiveCue implements CameraCueHandle {

        private CameraCue spec;
        private double start;
        private double releaseTime = Double.NaN;
        private boolean open = true;

        private LiveCue(CameraCue spec, double start) {
            this.spec = spec;
            this.start = start;
        }

        private void restart(CameraCue newSpec, double newStart) {
            this.spec = newSpec;
            this.start = newStart;
            this.releaseTime = Double.NaN;
        }

        private void sample(double time, boolean reduced, LayerAccumulator into) {
            double tau = time - start;
            if (tau < 0.0) {
                return;
            }
            boolean live = false;
            for (ChannelCurve channel : spec.curves()) {
                CueCurve curve = channel.curveFor(reduced);
                boolean substitute = reduced && channel.reduced().isPresent();
                double end = curveEnd(channel, curve);
                if (tau > end) {
                    continue;
                }
                live = true;
                if (channel.absolute()) {
                    // A target, not an offset: nothing to fade on release, so it ends there (curveEnd).
                    into.setEyeTarget(CueCurves.sample(curve, tau));
                    continue;
                }
                double value = released()
                        ? CueCurves.sampleReleased(curve, tau, releaseTime - start, releaseTicks(curve),
                        releaseEasing(curve))
                        : CueCurves.sample(curve, tau);
                into.add(channel.channel(), value, substitute);
                if (channel.unwound() && !substitute) {
                    into.markUnwound();
                }
            }
            if (live) {
                into.addCue(spec.key());
            }
        }

        /** The local end over both profiles, so flipping the profile never resurrects a pruned cue. */
        private double end() {
            double end = 0.0;
            for (ChannelCurve channel : spec.curves()) {
                end = Math.max(end, curveEnd(channel, channel.curve()));
                if (channel.reduced().isPresent()) {
                    end = Math.max(end, curveEnd(channel, channel.reduced().get()));
                }
            }
            return end;
        }

        private double curveEnd(ChannelCurve channel, CueCurve curve) {
            if (channel.absolute() && released()) {
                return Math.min(CueCurves.end(curve), Math.max(0.0, releaseTime - start));
            }
            return released()
                    ? CueCurves.releasedEnd(curve, releaseTime - start, releaseTicks(curve))
                    : CueCurves.end(curve);
        }

        private double releaseTicks(CueCurve curve) {
            return curve instanceof CueCurve.Pulse pulse ? pulse.releaseTicks() : spec.releaseTicks();
        }

        private EasingFunction releaseEasing(CueCurve curve) {
            return curve instanceof CueCurve.Pulse pulse ? pulse.releaseEasing() : spec.releaseEasing();
        }

        @Override
        public Key key() {
            synchronized (lock) {
                return spec.key();
            }
        }

        @Override
        public Key owner() {
            synchronized (lock) {
                return spec.owner();
            }
        }

        @Override
        public double startTime() {
            synchronized (lock) {
                return start;
            }
        }

        @Override
        public boolean active() {
            synchronized (lock) {
                return open;
            }
        }

        @Override
        public void release() {
            synchronized (lock) {
                if (open && !released()) {
                    releaseTime = Math.max(start, now.getAsDouble());
                }
            }
        }

        @Override
        public boolean released() {
            synchronized (lock) {
                return !Double.isNaN(releaseTime);
            }
        }

        @Override
        public CameraCueHandle replace(CameraCue next) {
            Objects.requireNonNull(next, "next");
            synchronized (lock) {
                closeLocked();
                LiveCue live = new LiveCue(next, now.getAsDouble());
                LiveCue clash = find(next.key());
                if (clash != null) {
                    clash.closeLocked();
                }
                cues.add(live);
                return live;
            }
        }

        @Override
        public void close() {
            synchronized (lock) {
                closeLocked();
            }
        }

        private void closeLocked() {
            if (open) {
                open = false;
                cues.remove(this);
            }
        }
    }
}
