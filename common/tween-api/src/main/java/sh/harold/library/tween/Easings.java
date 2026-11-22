package sh.harold.library.tween;

/**
 * Parametric easings that cannot be enum constants.
 */
public final class Easings {

    private static final int NEWTON_STEPS = 8;
    private static final double EPSILON = 1.0e-9;

    private Easings() {
    }

    /**
     * A CSS-style cubic Bezier easing through {@code (0,0)}, {@code (x1,y1)}, {@code (x2,y2)}, {@code (1,1)}.
     * {@code x1} and {@code x2} must lie in {@code [0, 1]} so the curve is a function of time; {@code y} values
     * are free, which allows overshoot.
     */
    public static EasingFunction cubicBezier(double x1, double y1, double x2, double y2) {
        if (!(x1 >= 0.0 && x1 <= 1.0 && x2 >= 0.0 && x2 <= 1.0)) {
            throw new IllegalArgumentException("x1 and x2 must lie in [0, 1]");
        }
        if (!Double.isFinite(y1) || !Double.isFinite(y2)) {
            throw new IllegalArgumentException("y1 and y2 must be finite");
        }
        return new CubicBezier(x1, y1, x2, y2);
    }

    private record CubicBezier(double x1, double y1, double x2, double y2) implements EasingFunction {

        @Override
        public double apply(double u) {
            if (u <= 0.0) {
                return 0.0;
            }
            if (u >= 1.0) {
                return 1.0;
            }
            double s = solveCurveX(u);
            return bezier(s, y1, y2);
        }

        private double solveCurveX(double x) {
            double s = x;
            for (int i = 0; i < NEWTON_STEPS; i++) {
                double error = bezier(s, x1, x2) - x;
                if (Math.abs(error) < EPSILON) {
                    return s;
                }
                double slope = bezierSlope(s, x1, x2);
                if (Math.abs(slope) < 1.0e-6) {
                    break;
                }
                s -= error / slope;
            }
            double lo = 0.0;
            double hi = 1.0;
            s = x;
            for (int i = 0; i < 64; i++) {
                double value = bezier(s, x1, x2);
                if (Math.abs(value - x) < EPSILON) {
                    return s;
                }
                if (value < x) {
                    lo = s;
                } else {
                    hi = s;
                }
                s = (lo + hi) * 0.5;
            }
            return s;
        }

        private static double bezier(double s, double p1, double p2) {
            double inv = 1.0 - s;
            return (3.0 * inv * inv * s * p1) + (3.0 * inv * s * s * p2) + (s * s * s);
        }

        private static double bezierSlope(double s, double p1, double p2) {
            double inv = 1.0 - s;
            return (3.0 * inv * inv * p1) + (6.0 * inv * s * (p2 - p1)) + (3.0 * s * s * (1.0 - p2));
        }
    }
}
