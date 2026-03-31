package sh.harold.creative.library.spatial;

import java.util.Objects;
import java.util.UUID;

public sealed interface AnchorRef permits AnchorRef.Entity, AnchorRef.Fixed, AnchorRef.Offset {

    record Fixed(AnchorSnapshot snapshot) implements AnchorRef {

        public Fixed {
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    record Entity(SpaceId spaceId, UUID entityId) implements AnchorRef {

        public Entity {
            spaceId = Objects.requireNonNull(spaceId, "spaceId");
            entityId = Objects.requireNonNull(entityId, "entityId");
        }
    }

    record Offset(AnchorRef base, Vec3 localOffset) implements AnchorRef {

        public Offset {
            base = Objects.requireNonNull(base, "base");
            localOffset = Objects.requireNonNull(localOffset, "localOffset");
        }
    }
}
