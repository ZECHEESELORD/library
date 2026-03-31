package sh.harold.creative.library.trajectory;

import net.kyori.adventure.key.Key;
import sh.harold.creative.library.tick.InstanceConflictPolicy;

import java.util.Objects;

public record TrajectoryPreviewSpec(
        Key key,
        PreviewScope scope,
        TrajectoryMotionSource motionSource,
        CollisionQuery collisionQuery,
        PreviewRecomputePolicy recomputePolicy,
        InstanceConflictPolicy conflictPolicy
) {

    public TrajectoryPreviewSpec {
        key = Objects.requireNonNull(key, "key");
        scope = Objects.requireNonNull(scope, "scope");
        motionSource = Objects.requireNonNull(motionSource, "motionSource");
        collisionQuery = Objects.requireNonNull(collisionQuery, "collisionQuery");
        recomputePolicy = Objects.requireNonNull(recomputePolicy, "recomputePolicy");
        conflictPolicy = Objects.requireNonNull(conflictPolicy, "conflictPolicy");
    }
}
