package sh.harold.library.tween.track;

/**
 * A pure function of clock time. {@code t} is {@code localTick + partial} on the owning clock, in ticks, and
 * may be fractional. Stateless samplers are evaluated every rendered frame, never sampled on the tick and left
 * to the host to lerp (catalog R2).
 */
@FunctionalInterface
public interface StatelessSampler<T> {

    T sample(double t);

    static <T> StatelessSampler<T> constant(T value) {
        return t -> value;
    }
}
