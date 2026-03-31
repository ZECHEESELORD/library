package sh.harold.creative.library.trajectory;

import net.kyori.adventure.key.Key;

import java.util.Objects;

public record TrajectoryPreviewSnapshot(
        Key key,
        PreviewScope scope,
        TrajectoryPreviewResult result,
        boolean recomputed,
        long ageTicks
) {

    public TrajectoryPreviewSnapshot {
        key = Objects.requireNonNull(key, "key");
        scope = Objects.requireNonNull(scope, "scope");
        result = Objects.requireNonNull(result, "result");
        if (ageTicks < 0L) {
            throw new IllegalArgumentException("ageTicks cannot be negative");
        }
    }
}
