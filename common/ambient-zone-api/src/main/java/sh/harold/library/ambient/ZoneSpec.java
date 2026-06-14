package sh.harold.library.ambient;

import net.kyori.adventure.key.Key;
import sh.harold.library.spatial.AnchorRef;
import sh.harold.library.spatial.Volume;
import sh.harold.library.tick.InstanceConflictPolicy;

import java.util.Objects;

public record ZoneSpec(
        Key key,
        AnchorRef anchor,
        Volume localVolume,
        AmbientProfile profile,
        AmbientBlendMode blendMode,
        AmbientWeightModel weightModel,
        int priority,
        long ttlTicks,
        InstanceConflictPolicy conflictPolicy
) {

    public ZoneSpec {
        key = Objects.requireNonNull(key, "key");
        anchor = Objects.requireNonNull(anchor, "anchor");
        localVolume = Objects.requireNonNull(localVolume, "localVolume");
        profile = Objects.requireNonNull(profile, "profile");
        blendMode = Objects.requireNonNull(blendMode, "blendMode");
        weightModel = Objects.requireNonNull(weightModel, "weightModel");
        if (ttlTicks < 0L) {
            throw new IllegalArgumentException("ttlTicks cannot be negative");
        }
        conflictPolicy = Objects.requireNonNull(conflictPolicy, "conflictPolicy");
    }
}
