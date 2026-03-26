package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.EnumMap;

public final class MenuButton implements MenuItem {

    private final MenuIcon icon;
    private final Component name;
    private final String secondary;
    private final List<MenuBlock> blocks;
    private final boolean glow;
    private final Map<MenuClick, MenuInteraction> interactions;
    private final boolean promptSuppressed;

    private MenuButton(Builder builder) {
        this.icon = builder.icon();
        this.name = builder.name();
        this.secondary = builder.secondary();
        this.blocks = builder.blocks();
        this.glow = builder.isGlowing();
        this.interactions = Map.copyOf(builder.interactions);
        this.promptSuppressed = builder.promptSuppressed;
        if (interactions.isEmpty()) {
            throw new IllegalStateException("MenuButton requires at least one interaction");
        }
    }

    public static Builder builder(MenuIcon icon) {
        return new Builder(icon);
    }

    @Override
    public MenuIcon icon() {
        return icon;
    }

    @Override
    public Component name() {
        return name;
    }

    @Override
    public Optional<String> secondary() {
        return Optional.ofNullable(secondary);
    }

    @Override
    public List<MenuBlock> blocks() {
        return blocks;
    }

    @Override
    public boolean glow() {
        return glow;
    }

    public Map<MenuClick, MenuInteraction> interactions() {
        return interactions;
    }

    public boolean promptSuppressed() {
        return promptSuppressed;
    }

    public static final class Builder extends AbstractMenuItemBuilder<Builder> {

        private final Map<MenuClick, MenuInteraction> interactions = new EnumMap<>(MenuClick.class);
        private boolean promptSuppressed;

        private Builder(MenuIcon icon) {
            super(icon);
        }

        public Builder action(ActionVerb verb, MenuAction action) {
            return action(verb, verb.promptLabel(), action);
        }

        public Builder action(ActionVerb verb, String promptLabel, MenuAction action) {
            interactions.put(MenuClick.LEFT, new MenuInteraction(verb, promptLabel, new MenuSlotAction.Execute(action)));
            return this;
        }

        public Builder onLeftClick(ActionVerb verb, MenuAction action) {
            return action(verb, action);
        }

        public Builder onLeftClick(ActionVerb verb, String promptLabel, MenuAction action) {
            return action(verb, promptLabel, action);
        }

        public Builder onRightClick(ActionVerb verb, MenuAction action) {
            return onRightClick(verb, verb.promptLabel(), action);
        }

        public Builder onRightClick(ActionVerb verb, String promptLabel, MenuAction action) {
            interactions.put(MenuClick.RIGHT, new MenuInteraction(verb, promptLabel, new MenuSlotAction.Execute(action)));
            return this;
        }

        public Builder skipPrompt() {
            this.promptSuppressed = true;
            return this;
        }

        public MenuButton build() {
            return new MenuButton(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
