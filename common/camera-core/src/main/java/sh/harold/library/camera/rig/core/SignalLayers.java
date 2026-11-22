package sh.harold.library.camera.rig.core;

import net.kyori.adventure.key.Key;
import sh.harold.library.camera.rig.CameraChannel;
import sh.harold.library.tick.KeyedHandle;
import sh.harold.library.tween.core.Spring;
import sh.harold.library.tween.signal.SignalChannelSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * Signal-driven channels (catalog 1.2) stepped once per rig tick (R3). Each layer keeps the state of the last two
 * ticks: a frame between them follows the exact spring path from the older state toward the target it was stepped
 * with, reaching the newer state at the tick, so per-frame values are continuous and never extrapolate past what a
 * tick produced. Guarded by the owning rig's lock.
 */
final class SignalLayers {

    private static final double REST = 1.0e-9;

    private final Object lock;
    private final List<Layer> layers = new ArrayList<>();

    SignalLayers(Object lock) {
        this.lock = lock;
    }

    KeyedHandle add(Key key, CameraChannel channel, SignalChannelSpec spec, DoubleSupplier signal) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(signal, "signal");
        synchronized (lock) {
            for (Layer layer : List.copyOf(layers)) {
                if (layer.key.equals(key)) {
                    layer.close();
                }
            }
            Layer layer = new Layer(key, channel, spec, signal);
            layers.add(layer);
            return layer;
        }
    }

    /** Steps every layer from tick {@code n - 1} to tick {@code n}, reading its signal now. */
    void tick() {
        for (Layer layer : layers) {
            layer.step();
        }
    }

    /** Adds every layer's value at {@code time}, where the newest state is at {@code tick}. */
    void sample(double time, long tick, LayerAccumulator into) {
        for (Layer layer : layers) {
            double value = layer.sample(time, tick);
            if (value != 0.0 || !layer.atRest()) {
                into.add(layer.channel, value, false);
            }
        }
    }

    boolean atRest() {
        for (Layer layer : layers) {
            if (!layer.atRest()) {
                return false;
            }
        }
        return true;
    }

    void closeAll() {
        for (Layer layer : List.copyOf(layers)) {
            layer.close();
        }
    }

    private final class Layer implements KeyedHandle {

        private final Key key;
        private final CameraChannel channel;
        private final SignalChannelSpec spec;
        private final DoubleSupplier signal;
        private double previousX;
        private double previousV;
        private double x;
        private double v;
        private double target;
        private boolean open = true;

        private Layer(Key key, CameraChannel channel, SignalChannelSpec spec, DoubleSupplier signal) {
            this.key = key;
            this.channel = channel;
            this.spec = spec;
            this.signal = signal;
        }

        private void step() {
            double read = signal.getAsDouble();
            double mapped = Double.isFinite(read) ? spec.map().sample(read) : target;
            target = Double.isFinite(mapped) ? mapped : target;
            previousX = x;
            previousV = v;
            double[] next = Spring.advance(spec.spring(), x, v, target, 1.0);
            x = next[0];
            v = next[1];
            if (target == 0.0 && Math.abs(x) < REST && Math.abs(v) < REST) {
                x = 0.0;
                v = 0.0;
            }
        }

        private double sample(double time, long tick) {
            double fromPrevious = time - (tick - 1);
            if (fromPrevious <= 0.0) {
                return previousX;
            }
            if (fromPrevious < 1.0) {
                return Spring.advance(spec.spring(), previousX, previousV, target, fromPrevious)[0];
            }
            double fromCurrent = time - tick;
            return fromCurrent == 0.0 ? x : Spring.advance(spec.spring(), x, v, target, fromCurrent)[0];
        }

        private boolean atRest() {
            return x == 0.0 && v == 0.0 && previousX == 0.0 && previousV == 0.0 && target == 0.0;
        }

        @Override
        public Key key() {
            return key;
        }

        @Override
        public boolean active() {
            synchronized (lock) {
                return open;
            }
        }

        @Override
        public void close() {
            synchronized (lock) {
                open = false;
                layers.remove(this);
            }
        }
    }
}
