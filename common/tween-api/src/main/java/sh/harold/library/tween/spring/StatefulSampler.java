package sh.harold.library.tween.spring;

/**
 * A sampler with state, such as a spring or a damped follow (catalog R3). It steps once per tick with
 * {@code dt = 1 tick}, reading its target on the tick. The per-frame value is the closed-form continuation of
 * the last tick's state by {@code partial}, so the result is the same at 60 or 240 fps and tick telemetry is
 * exact. {@link #sample(double)} never changes state.
 */
public interface StatefulSampler<T> {

    /** Advances the state by one tick. */
    void tick();

    /** The value {@code partial} ticks after the last tick, {@code partial} in {@code [0, 1]}. */
    T sample(double partial);

    /** The value at the last tick; the same as {@code sample(0)}. */
    default T value() {
        return sample(0.0);
    }

    /** Jumps to {@code value} at rest. A cut calls this on every stateful sampler (catalog R4). */
    void reset(T value);
}
