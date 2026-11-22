package sh.harold.library.tween.core;

import sh.harold.library.tween.signal.SignalChannelSpec;
import sh.harold.library.tween.spring.StatefulSampler;

import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * Signal, then map curve, then spring (catalog 1.2). On each tick it reads the signal, maps it, retargets the
 * spring (keeping its velocity) and steps it. Between ticks it continues in closed form like any spring.
 */
public final class SignalChannel implements StatefulSampler<Double> {

    private final SignalChannelSpec spec;
    private final DoubleSupplier signal;
    private final Spring spring;

    public SignalChannel(SignalChannelSpec spec, DoubleSupplier signal, double initial) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.signal = Objects.requireNonNull(signal, "signal");
        this.spring = new Spring(spec.spring(), initial);
    }

    public SignalChannelSpec spec() {
        return spec;
    }

    /** The spring underneath, for impulse kicks on top of the signal. */
    public Spring spring() {
        return spring;
    }

    @Override
    public void tick() {
        spring.retarget(spec.map().sample(signal.getAsDouble()));
        spring.tick();
    }

    @Override
    public Double sample(double partial) {
        return spring.sampleDouble(partial);
    }

    @Override
    public void reset(Double value) {
        spring.reset(value);
    }
}
