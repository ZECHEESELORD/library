package sh.harold.creative.library.trajectory;

import sh.harold.creative.library.spatial.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TrajectoryPreviewResult(
        List<Vec3> sampledPoints,
        double totalDistance,
        long simulatedTicks,
        Optional<CollisionHit> firstHit,
        Vec3 endPosition,
        boolean valid,
        PreviewInvalidReason invalidReason
) {

    public TrajectoryPreviewResult {
        sampledPoints = List.copyOf(Objects.requireNonNull(sampledPoints, "sampledPoints"));
        if (!Double.isFinite(totalDistance) || totalDistance < 0.0) {
            throw new IllegalArgumentException("totalDistance must be finite and non-negative");
        }
        if (simulatedTicks < 0L) {
            throw new IllegalArgumentException("simulatedTicks cannot be negative");
        }
        firstHit = Objects.requireNonNull(firstHit, "firstHit");
        endPosition = Objects.requireNonNull(endPosition, "endPosition");
        invalidReason = Objects.requireNonNull(invalidReason, "invalidReason");
    }
}
