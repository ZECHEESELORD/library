package sh.harold.creative.library.trajectory;

import sh.harold.creative.library.spatial.Angle;

import java.util.Objects;

public sealed interface PreviewRecomputePolicy permits PreviewRecomputePolicy.EveryTick, PreviewRecomputePolicy.OneShot,
        PreviewRecomputePolicy.Thresholded {

    record OneShot() implements PreviewRecomputePolicy {
    }

    record EveryTick() implements PreviewRecomputePolicy {
    }

    record Thresholded(double originDistanceThreshold, Angle aimDirectionThreshold) implements PreviewRecomputePolicy {

        public Thresholded {
            if (!Double.isFinite(originDistanceThreshold) || originDistanceThreshold < 0.0) {
                throw new IllegalArgumentException("originDistanceThreshold must be finite and non-negative");
            }
            aimDirectionThreshold = Objects.requireNonNull(aimDirectionThreshold, "aimDirectionThreshold");
        }
    }
}
