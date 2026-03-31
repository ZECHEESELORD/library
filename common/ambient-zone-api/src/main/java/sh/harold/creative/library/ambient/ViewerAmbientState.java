package sh.harold.creative.library.ambient;

import sh.harold.creative.library.spatial.SpaceId;
import sh.harold.creative.library.spatial.Vec3;

import java.util.Objects;
import java.util.UUID;

public record ViewerAmbientState(UUID viewerId, SpaceId spaceId, Vec3 position) {

    public ViewerAmbientState {
        viewerId = Objects.requireNonNull(viewerId, "viewerId");
        spaceId = Objects.requireNonNull(spaceId, "spaceId");
        position = Objects.requireNonNull(position, "position");
    }
}
