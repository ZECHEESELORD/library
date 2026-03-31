package sh.harold.creative.library.trajectory;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public sealed interface PreviewScope permits PreviewScope.Everyone, PreviewScope.Explicit, PreviewScope.OwnerOnly {

    record Everyone() implements PreviewScope {
    }

    record OwnerOnly(UUID ownerId) implements PreviewScope {

        public OwnerOnly {
            ownerId = Objects.requireNonNull(ownerId, "ownerId");
        }
    }

    record Explicit(Set<UUID> viewerIds) implements PreviewScope {

        public Explicit {
            viewerIds = Set.copyOf(Objects.requireNonNull(viewerIds, "viewerIds"));
        }
    }
}
