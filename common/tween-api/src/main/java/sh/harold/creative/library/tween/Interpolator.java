package sh.harold.creative.library.tween;

@FunctionalInterface
public interface Interpolator<T> {

    T interpolate(T from, T to, double progress);
}
