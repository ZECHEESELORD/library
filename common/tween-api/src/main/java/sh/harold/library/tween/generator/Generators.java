package sh.harold.library.tween.generator;

import sh.harold.library.tween.EasingFunction;
import sh.harold.library.tween.track.Tangent;
import sh.harold.library.tween.track.Track;
import sh.harold.library.tween.track.TrackValue;
import sh.harold.library.tween.track.TrackValues;

/**
 * Motion-craft generators that expand to ordinary {@link Track} keys (catalog 1.3), so the result samples,
 * seeks and layers like any authored track.
 */
public final class Generators {

    private Generators() {
    }

    /**
     * Overshoot and settle: travels from {@code from} to a peak {@code overshootPercent}% of the step past
     * {@code to}, arriving at {@code startTick + travelTicks}, then settles back onto {@code to} over
     * {@code settleTicks}. All three keys are Hermite with flat tangents, so the motion eases out of the start,
     * holds an instant at the peak and eases into rest.
     */
    public static <T> Track<T> overshoot(TrackValue<T> values, T from, T to, double startTick, double travelTicks,
                                         double overshootPercent, double settleTicks) {
        requirePositive(travelTicks, "travelTicks");
        requirePositive(settleTicks, "settleTicks");
        if (!Double.isFinite(overshootPercent) || overshootPercent < 0.0) {
            throw new IllegalArgumentException("overshootPercent must be finite and non-negative");
        }
        T peak = values.add(to, values.scale(values.difference(from, to), overshootPercent / 100.0));
        return Track.builder(values)
                .hermite(startTick, from, Tangent.flat(), Tangent.flat())
                .hermite(startTick + travelTicks, peak, Tangent.flat(), Tangent.flat())
                .hermite(startTick + travelTicks + settleTicks, to, Tangent.flat(), Tangent.flat())
                .build();
    }

    public static Track<Double> overshoot(double from, double to, double startTick, double travelTicks,
                                          double overshootPercent, double settleTicks) {
        return overshoot(TrackValues.SCALAR, from, to, startTick, travelTicks, overshootPercent, settleTicks);
    }

    /**
     * Anticipation: first moves {@code amount} (a fraction of the step, typically 0.05 to 0.15) against the action
     * over {@code anticipationTicks} with {@code anticipationEase}, then performs the action to {@code to} over
     * {@code actionTicks} with {@code actionEase}.
     */
    public static <T> Track<T> anticipate(TrackValue<T> values, T from, T to, double startTick,
                                          double amount, double anticipationTicks, EasingFunction anticipationEase,
                                          double actionTicks, EasingFunction actionEase) {
        requirePositive(anticipationTicks, "anticipationTicks");
        requirePositive(actionTicks, "actionTicks");
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("amount must be finite and non-negative");
        }
        T wound = values.add(from, values.scale(values.difference(from, to), -amount));
        return Track.builder(values)
                .eased(startTick, from, anticipationEase)
                .eased(startTick + anticipationTicks, wound, actionEase)
                .eased(startTick + anticipationTicks + actionTicks, to, actionEase)
                .build();
    }

    /**
     * Squash and stretch scales for stretch {@code s} along an axis: {@code s} along it and
     * {@code s^(-k/2)} across it. {@code preserve} {@code k = 1} keeps volume (25% squash gives 0.75 along and
     * 1.155 across); {@code k = 0} scales only along the axis.
     */
    public static AxisScale squashStretch(double stretch, double preserve) {
        if (!Double.isFinite(stretch) || stretch <= 0.0) {
            throw new IllegalArgumentException("stretch must be finite and positive");
        }
        if (!Double.isFinite(preserve) || preserve < 0.0 || preserve > 1.0) {
            throw new IllegalArgumentException("preserve must be in [0, 1]");
        }
        return new AxisScale(stretch, Math.pow(stretch, -preserve / 2.0));
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    /** Scale along the squash axis and across it. */
    public record AxisScale(double along, double across) {
    }
}
