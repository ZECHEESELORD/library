package sh.harold.creative.library.ambient;

import java.util.Objects;

public record AmbientWeightModel(double featherDistance, WeightCurve curve) {

    public AmbientWeightModel {
        if (!Double.isFinite(featherDistance) || featherDistance < 0.0) {
            throw new IllegalArgumentException("featherDistance must be finite and non-negative");
        }
        curve = Objects.requireNonNull(curve, "curve");
    }

    public static AmbientWeightModel hardEdge() {
        return new AmbientWeightModel(0.0, WeightCurve.LINEAR);
    }
}
