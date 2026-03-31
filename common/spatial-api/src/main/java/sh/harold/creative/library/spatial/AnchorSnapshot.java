package sh.harold.creative.library.spatial;

import java.util.Objects;

public record AnchorSnapshot(SpaceId spaceId, Frame3 frame) {

    public AnchorSnapshot {
        spaceId = Objects.requireNonNull(spaceId, "spaceId");
        frame = Objects.requireNonNull(frame, "frame");
    }

    public AnchorSnapshot translated(Vec3 localOffset) {
        return new AnchorSnapshot(spaceId, frame.translated(frame.localToWorldVector(localOffset)));
    }
}
