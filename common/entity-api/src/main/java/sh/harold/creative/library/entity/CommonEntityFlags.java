package sh.harold.creative.library.entity;

import net.kyori.adventure.text.Component;

import java.util.Objects;
import java.util.Optional;

public record CommonEntityFlags(
        Optional<Component> customName,
        boolean customNameVisible,
        boolean glowing,
        boolean silent,
        boolean gravity,
        boolean invulnerable
) {

    public CommonEntityFlags {
        customName = Objects.requireNonNullElse(customName, Optional.empty());
    }

    public static CommonEntityFlags defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Component customName;
        private boolean customNameVisible;
        private boolean glowing;
        private boolean silent;
        private boolean gravity = true;
        private boolean invulnerable;

        private Builder() {
        }

        public Builder customName(Component customName) {
            this.customName = customName;
            return this;
        }

        public Builder customNameVisible(boolean customNameVisible) {
            this.customNameVisible = customNameVisible;
            return this;
        }

        public Builder glowing(boolean glowing) {
            this.glowing = glowing;
            return this;
        }

        public Builder silent(boolean silent) {
            this.silent = silent;
            return this;
        }

        public Builder gravity(boolean gravity) {
            this.gravity = gravity;
            return this;
        }

        public Builder invulnerable(boolean invulnerable) {
            this.invulnerable = invulnerable;
            return this;
        }

        public CommonEntityFlags build() {
            return new CommonEntityFlags(Optional.ofNullable(customName), customNameVisible, glowing, silent, gravity, invulnerable);
        }
    }
}
