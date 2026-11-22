package sh.harold.library.tween.track;

import java.util.List;
import java.util.Objects;

/**
 * A base sampler with layers stacked on top in order: per frame {@code base + sum(w_i * add_i)} for additive
 * layers, and {@code lerp(below, layer, w)} for override layers (catalog 1.1, "Layer blend").
 */
public final class LayeredTrack<T> implements StatelessSampler<T> {

    private final TrackValue<T> values;
    private final StatelessSampler<T> base;
    private final List<TrackLayer<T>> layers;

    public LayeredTrack(TrackValue<T> values, StatelessSampler<T> base, List<TrackLayer<T>> layers) {
        this.values = Objects.requireNonNull(values, "values");
        this.base = Objects.requireNonNull(base, "base");
        this.layers = List.copyOf(layers);
    }

    public List<TrackLayer<T>> layers() {
        return layers;
    }

    @Override
    public T sample(double t) {
        T value = base.sample(t);
        for (TrackLayer<T> layer : layers) {
            double weight = layer.weight().sample(t);
            if (weight == 0.0) {
                continue;
            }
            T layerValue = layer.track().sample(t);
            value = switch (layer.mode()) {
                case OVERRIDE -> values.lerp(value, layerValue, weight);
                case ADDITIVE -> values.add(value, values.scale(layerValue, weight));
            };
        }
        return value;
    }
}
