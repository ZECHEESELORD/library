package sh.harold.creative.library.entity;

import net.kyori.adventure.key.Key;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class EntitySpec {

    private final EntityTypeKey type;
    private final EntityTransform transform;
    private final CommonEntityFlags flags;
    private final Set<Key> tags;
    private final EntityInteractionHandler interactionHandler;

    private EntitySpec(
            EntityTypeKey type,
            EntityTransform transform,
            CommonEntityFlags flags,
            Set<Key> tags,
            EntityInteractionHandler interactionHandler
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.transform = Objects.requireNonNull(transform, "transform");
        this.flags = Objects.requireNonNull(flags, "flags");
        this.tags = Set.copyOf(tags);
        this.interactionHandler = interactionHandler;
    }

    public EntityTypeKey type() {
        return type;
    }

    public EntityTransform transform() {
        return transform;
    }

    public CommonEntityFlags flags() {
        return flags;
    }

    public Set<Key> tags() {
        return tags;
    }

    public Optional<EntityInteractionHandler> interactionHandler() {
        return Optional.ofNullable(interactionHandler);
    }

    public static Builder builder(EntityTypeKey type) {
        return new Builder(type);
    }

    public static final class Builder {
        private final EntityTypeKey type;
        private EntityTransform transform = EntityTransform.at(0.0, 0.0, 0.0);
        private CommonEntityFlags flags = CommonEntityFlags.defaults();
        private final Set<Key> tags = new LinkedHashSet<>();
        private EntityInteractionHandler interactionHandler;

        private Builder(EntityTypeKey type) {
            this.type = Objects.requireNonNull(type, "type");
        }

        public Builder transform(EntityTransform transform) {
            this.transform = Objects.requireNonNull(transform, "transform");
            return this;
        }

        public Builder flags(CommonEntityFlags flags) {
            this.flags = Objects.requireNonNull(flags, "flags");
            return this;
        }

        public Builder tag(Key tag) {
            this.tags.add(Objects.requireNonNull(tag, "tag"));
            return this;
        }

        public Builder tags(Collection<Key> tags) {
            this.tags.addAll(Objects.requireNonNull(tags, "tags"));
            return this;
        }

        public Builder interactionHandler(EntityInteractionHandler interactionHandler) {
            this.interactionHandler = interactionHandler;
            return this;
        }

        public EntitySpec build() {
            return new EntitySpec(type, transform, flags, tags, interactionHandler);
        }
    }
}
