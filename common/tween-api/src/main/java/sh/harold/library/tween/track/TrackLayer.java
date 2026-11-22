package sh.harold.library.tween.track;

import java.util.Objects;

/** One layer in a {@link LayeredTrack}, with its own weight track. */
public record TrackLayer<T>(StatelessSampler<T> track, StatelessSampler<Double> weight, BlendMode mode) {

    public TrackLayer {
        Objects.requireNonNull(track, "track");
        Objects.requireNonNull(weight, "weight");
        Objects.requireNonNull(mode, "mode");
    }

    public static <T> TrackLayer<T> additive(StatelessSampler<T> track, StatelessSampler<Double> weight) {
        return new TrackLayer<>(track, weight, BlendMode.ADDITIVE);
    }

    public static <T> TrackLayer<T> override(StatelessSampler<T> track, StatelessSampler<Double> weight) {
        return new TrackLayer<>(track, weight, BlendMode.OVERRIDE);
    }
}
