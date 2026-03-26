package sh.harold.creative.library.entity.house;

import net.kyori.adventure.text.Component;
import sh.harold.creative.library.entity.EntitySpec;

import java.util.Objects;
import java.util.Optional;

public final class HouseServiceSpec {

    private final EntitySpec entitySpec;
    private final Component name;
    private final HouseRole role;
    private final HousePromptMode promptMode;
    private final HouseVisibilityPolicy visibilityPolicy;
    private final HouseServiceClickHandler clickHandler;

    private HouseServiceSpec(
            EntitySpec entitySpec,
            Component name,
            HouseRole role,
            HousePromptMode promptMode,
            HouseVisibilityPolicy visibilityPolicy,
            HouseServiceClickHandler clickHandler
    ) {
        this.entitySpec = Objects.requireNonNull(entitySpec, "entitySpec");
        this.name = name;
        this.role = Objects.requireNonNull(role, "role");
        this.promptMode = Objects.requireNonNull(promptMode, "promptMode");
        this.visibilityPolicy = Objects.requireNonNull(visibilityPolicy, "visibilityPolicy");
        this.clickHandler = clickHandler;
    }

    public EntitySpec entitySpec() {
        return entitySpec;
    }

    public Component name() {
        return name;
    }

    public HouseRole role() {
        return role;
    }

    public HousePromptMode promptMode() {
        return promptMode;
    }

    public HouseVisibilityPolicy visibilityPolicy() {
        return visibilityPolicy;
    }

    public Optional<HouseServiceClickHandler> clickHandler() {
        return Optional.ofNullable(clickHandler);
    }

    public static Builder builder(EntitySpec entitySpec) {
        return new Builder(entitySpec);
    }

    public static final class Builder {
        private final EntitySpec entitySpec;
        private Component name;
        private HouseRole role = HouseRole.hidden();
        private HousePromptMode promptMode = HousePromptMode.INTERACT;
        private HouseVisibilityPolicy visibilityPolicy = HouseVisibilityPolicy.ALWAYS;
        private HouseServiceClickHandler clickHandler;

        private Builder(EntitySpec entitySpec) {
            this.entitySpec = Objects.requireNonNull(entitySpec, "entitySpec");
        }

        public Builder name(Component name) {
            this.name = Objects.requireNonNull(name, "name");
            return this;
        }

        public Builder role(HouseRole role) {
            this.role = Objects.requireNonNull(role, "role");
            return this;
        }

        public Builder promptMode(HousePromptMode promptMode) {
            this.promptMode = Objects.requireNonNull(promptMode, "promptMode");
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

        public HouseServiceSpec build() {
            return new HouseServiceSpec(entitySpec, name, role, promptMode, visibilityPolicy, clickHandler);
        }
    }
}
