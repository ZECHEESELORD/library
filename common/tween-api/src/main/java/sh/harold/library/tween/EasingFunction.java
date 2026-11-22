package sh.harold.library.tween;

/**
 * An easing curve: maps segment progress {@code u} in {@code [0, 1]} to eased progress. A well-formed curve
 * returns 0 at 0 and 1 at 1; values between may leave {@code [0, 1]} (back, elastic).
 * {@link Easing} constants and {@link Easings#cubicBezier(double, double, double, double)} implement it.
 */
@FunctionalInterface
public interface EasingFunction {

    double apply(double u);
}
