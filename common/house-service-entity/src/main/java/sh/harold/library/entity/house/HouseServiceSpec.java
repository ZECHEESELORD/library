package sh.harold.library.entity.house;

import net.kyori.adventure.text.Component;
import sh.harold.library.entity.EntitySpec;
import sh.harold.library.entity.EntityTypes;
import sh.harold.library.npc.behavior.NpcBehaviorProfile;

import java.util.Objects;
import java.util.Optional;

public final class HouseServiceSpec {

    private final EntitySpec entitySpec;
    private final Component name;
    private final Component description;
    private final HouseVisibilityPolicy visibilityPolicy;
    private final HouseServiceClickHandler clickHandler;
    private final NpcBehaviorProfile behaviorProfile;

    private HouseServiceSpec(
            EntitySpec entitySpec,
            Component name,
            Component description,
            HouseVisibilityPolicy visibilityPolicy,
            HouseServiceClickHandler clickHandler,
            NpcBehaviorProfile behaviorProfile
    ) {
        this.entitySpec = Objects.requireNonNull(entitySpec, "entitySpec");
        this.name = name;
        this.description = description;
        this.visibilityPolicy = Objects.requireNonNull(visibilityPolicy, "visibilityPolicy");
        this.clickHandler = clickHandler;
        this.behaviorProfile = behaviorProfile;
    }

    public EntitySpec entitySpec() {
        return entitySpec;
    }

    public Component name() {
        return name;
    }

    public Component description() {
        return description;
    }

    public HouseVisibilityPolicy visibilityPolicy() {
        return visibilityPolicy;
    }

    public Optional<HouseServiceClickHandler> clickHandler() {
        return Optional.ofNullable(clickHandler);
    }

    /**
     * Optional by design: omission preserves the historical motionless service
     * NPC presentation.
     */
    public Optional<NpcBehaviorProfile> behaviorProfile() {
        return Optional.ofNullable(behaviorProfile);
    }

    public static Builder builder(EntitySpec entitySpec) {
        return new Builder(entitySpec);
    }

    public static final class Builder {
        private final EntitySpec entitySpec;
        private Component name;
        private Component description;
        private HouseVisibilityPolicy visibilityPolicy = HouseVisibilityPolicy.ALWAYS;
        private HouseServiceClickHandler clickHandler;
        private NpcBehaviorProfile behaviorProfile;

        private Builder(EntitySpec entitySpec) {
            this.entitySpec = Objects.requireNonNull(entitySpec, "entitySpec");
        }

        public Builder name(Component name) {
            this.name = Objects.requireNonNull(name, "name");
            return this;
        }

        public Builder name(String name) {
            this.name = HouseTextFormats.parse(name);
            return this;
        }

        public Builder description(Component description) {
            this.description = Objects.requireNonNull(description, "description");
            return this;
        }

        public Builder description(String description) {
            this.description = HouseTextFormats.parse(description);
            return this;
        }

        public Builder visibilityPolicy(HouseVisibilityPolicy visibilityPolicy) {
            this.visibilityPolicy = Objects.requireNonNull(visibilityPolicy, "visibilityPolicy");
            return this;
        }

        public Builder clickHandler(HouseServiceClickHandler clickHandler) {
            this.clickHandler = clickHandler;
            return this;
        }

        public Builder behaviorProfile(NpcBehaviorProfile behaviorProfile) {
            this.behaviorProfile = Objects.requireNonNull(behaviorProfile, "behaviorProfile");
            return this;
        }

        public HouseServiceSpec build() {
            if (behaviorProfile != null && !entitySpec.type().equals(EntityTypes.PLAYER_LIKE_HUMANOID)) {
                throw new IllegalArgumentException(
                        "behaviorProfile requires entity type " + EntityTypes.PLAYER_LIKE_HUMANOID.key()
                );
            }
            return new HouseServiceSpec(
                    entitySpec,
                    name,
                    description,
                    visibilityPolicy,
                    clickHandler,
                    behaviorProfile
            );
        }
    }
}
