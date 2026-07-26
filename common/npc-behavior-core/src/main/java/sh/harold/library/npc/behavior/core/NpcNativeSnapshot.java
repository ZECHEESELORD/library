package sh.harold.library.npc.behavior.core;

import sh.harold.library.spatial.SpaceId;
import sh.harold.library.spatial.Vec3;

import java.util.Objects;
import java.util.Optional;

/** Immutable native state published by an actor lane. */
public record NpcNativeSnapshot(
        Vec3 position,
        Optional<SpaceId> spaceId,
        NpcRenderFrame frame,
        int trackingViewerCount
) {
    public NpcNativeSnapshot {
        position = Objects.requireNonNull(position, "position");
        spaceId = Objects.requireNonNull(spaceId, "spaceId");
        frame = Objects.requireNonNull(frame, "frame");
        if (trackingViewerCount < 0) {
            throw new IllegalArgumentException("trackingViewerCount must not be negative");
        }
    }
}
