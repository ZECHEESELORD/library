package sh.harold.library.tween.core;

import sh.harold.library.tween.spring.StatefulSampler;
import sh.harold.library.tween.track.StatelessSampler;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Makes a stateful sampler seekable by re-simulation (catalog R6). The state at clock time {@code t} is a fresh
 * sampler stepped {@code floor(t)} ticks, with {@code driver} setting targets and kicks before each tick,
 * then continued by the fractional part. Moving forward steps on from the cached state; moving backward
 * re-simulates from the start. Either way the value at {@code t} is the same, which is what the scrubber and the
 * headless sampler rely on.
 *
 * <p>Not thread-safe: one instance per evaluating thread.
 */
public final class SeekableSampler<S extends StatefulSampler<T>, T> implements StatelessSampler<T> {

    private final Supplier<? extends S> factory;
    private final TickDriver<? super S> driver;
    private S state;
    private long stateTick;

    public SeekableSampler(Supplier<? extends S> factory, TickDriver<? super S> driver) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.driver = Objects.requireNonNull(driver, "driver");
    }

    @Override
    public T sample(double t) {
        if (!Double.isFinite(t) || t < 0.0) {
            throw new IllegalArgumentException("t must be finite and non-negative");
        }
        long whole = (long) Math.floor(t);
        if (state == null || whole < stateTick) {
            state = Objects.requireNonNull(factory.get(), "factory returned null");
            stateTick = 0L;
        }
        while (stateTick < whole) {
            driver.beforeTick(state, stateTick);
            state.tick();
            stateTick++;
        }
        return state.sample(t - whole);
    }

    /** Sets a sampler's inputs for one tick, as a pure function of the tick. */
    @FunctionalInterface
    public interface TickDriver<S> {

        /** Called before the sampler steps from tick {@code tick} to {@code tick + 1}. */
        void beforeTick(S sampler, long tick);
    }
}
