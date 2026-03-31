package sh.harold.creative.library.telegraph;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public sealed interface ViewerScope permits ViewerScope.Everyone, ViewerScope.Explicit, ViewerScope.Relation, ViewerScope.SourceOnly {

    record Everyone() implements ViewerScope {
    }

    record SourceOnly(UUID sourceId) implements ViewerScope {

        public SourceOnly {
            sourceId = Objects.requireNonNull(sourceId, "sourceId");
        }
    }

    record Relation(UUID sourceId, ViewerRelation relation) implements ViewerScope {

        public Relation {
            sourceId = Objects.requireNonNull(sourceId, "sourceId");
            relation = Objects.requireNonNull(relation, "relation");
        }
    }

    record Explicit(Set<UUID> viewerIds) implements ViewerScope {

        public Explicit {
            viewerIds = Set.copyOf(Objects.requireNonNull(viewerIds, "viewerIds"));
        }
    }
}
