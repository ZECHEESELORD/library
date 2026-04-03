package sh.harold.creative.library.boundary;

import net.kyori.adventure.key.Key;
import sh.harold.creative.library.spatial.SpaceId;

import java.util.Objects;
import java.util.Optional;

public record BoundaryDecisionQuery(BoundaryActorRef actor, Key action, BoundaryTarget target, Key source) {

    public BoundaryDecisionQuery {
        actor = Objects.requireNonNull(actor, "actor");
        action = Objects.requireNonNull(action, "action");
        target = Objects.requireNonNull(target, "target");
    }

    public BoundaryDecisionQuery(BoundaryActorRef actor, Key action, BoundaryTarget target) {
        this(actor, action, target, null);
    }

    public SpaceId spaceId() {
        return target.spaceId();
    }

    public Optional<Key> optionalSource() {
        return Optional.ofNullable(source);
    }
}
