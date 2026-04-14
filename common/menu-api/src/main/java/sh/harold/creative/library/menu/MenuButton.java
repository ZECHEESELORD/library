package sh.harold.creative.library.menu;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

public final class MenuButton implements MenuItem {

    private final MenuIcon icon;
    private final Component name;
    private final String secondary;
    private final List<MenuBlock> blocks;
    private final List<Component> exactLore;
    private final boolean glow;
    private final int amount;
    private final Map<MenuClick, MenuInteraction> interactions;
    private final boolean promptSuppressed;
    private final MenuTooltipBehavior tooltipBehavior;

    private MenuButton(Builder builder) {
        this.icon = builder.icon();
        this.name = builder.name();
        this.secondary = builder.secondary();
        this.blocks = builder.blocks();
        this.exactLore = builder.exactLore();
        this.glow = builder.isGlowing();
        this.amount = builder.amount();
        this.interactions = Map.copyOf(builder.interactions);
        this.promptSuppressed = builder.promptSuppressed;
        this.tooltipBehavior = builder.tooltipBehavior();
        if (interactions.isEmpty()) {
            throw new IllegalStateException("MenuButton requires at least one interaction");
        }
        if (amount <= 0) {
            throw new IllegalStateException("amount must be greater than zero");
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
    public Optional<List<Component>> exactLore() {
        return Optional.ofNullable(exactLore);
    }

    @Override
    public boolean glow() {
        return glow;
    }

    @Override
    public int amount() {
        return amount;
    }

    @Override
    public Map<MenuClick, MenuInteraction> interactions() {
        return interactions;
    }

    @Override
    public boolean promptSuppressed() {
        return promptSuppressed;
    }

    @Override
    public MenuTooltipBehavior tooltipBehavior() {
        return tooltipBehavior;
    }

    public static final class Builder extends AbstractMenuItemBuilder<Builder> {

        private final Map<MenuClick, MenuInteraction> interactions = new EnumMap<>(MenuClick.class);
        private int amount = 1;
        private boolean promptSuppressed;

        private Builder(MenuIcon icon) {
            super(icon);
        }

        public Builder action(ActionVerb verb, MenuAction action) {
            return action(verb, verb.promptLabel(), action);
        }

        public Builder action(ActionVerb verb, String promptLabel, MenuAction action) {
            interactions.put(MenuClick.LEFT, MenuInteraction.of(verb, promptLabel, new MenuSlotAction.Execute(action)));
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
            interactions.put(MenuClick.RIGHT, MenuInteraction.of(verb, promptLabel, new MenuSlotAction.Execute(action)));
            return this;
        }

        public Builder emit(ActionVerb verb, Object message) {
            return emit(verb, verb.promptLabel(), message);
        }

        public Builder emit(ActionVerb verb, String promptLabel, Object message) {
            interactions.put(MenuClick.LEFT, MenuInteraction.of(verb, promptLabel, new MenuSlotAction.Dispatch(message)));
            return this;
        }

        public Builder onRightEmit(ActionVerb verb, Object message) {
            return onRightEmit(verb, verb.promptLabel(), message);
        }

        public Builder onRightEmit(ActionVerb verb, String promptLabel, Object message) {
            interactions.put(MenuClick.RIGHT, MenuInteraction.of(verb, promptLabel, new MenuSlotAction.Dispatch(message)));
            return this;
        }

        public Builder sound(Key soundCueKey) {
            return leftSound(soundCueKey);
        }

        public Builder sound(String soundCueKey) {
            return leftSound(Key.key(soundCueKey));
        }

        public Builder leftSound(Key soundCueKey) {
            return updateInteraction(MenuClick.LEFT, interaction -> interaction.withSound(soundCueKey),
                    "sound(...) requires a left-click interaction");
        }

        public Builder leftSound(String soundCueKey) {
            return leftSound(Key.key(soundCueKey));
        }

        public Builder rightSound(Key soundCueKey) {
            return updateInteraction(MenuClick.RIGHT, interaction -> interaction.withSound(soundCueKey),
                    "rightSound(...) requires a right-click interaction");
        }

        public Builder rightSound(String soundCueKey) {
            return rightSound(Key.key(soundCueKey));
        }

        public Builder withoutSound() {
            return updateInteraction(MenuClick.LEFT, MenuInteraction::withoutSound,
                    "withoutSound() requires a left-click interaction");
        }

        public Builder withoutRightSound() {
            return updateInteraction(MenuClick.RIGHT, MenuInteraction::withoutSound,
                    "withoutRightSound() requires a right-click interaction");
        }

        private Builder updateInteraction(MenuClick click, UnaryOperator<MenuInteraction> transform, String missingMessage) {
            MenuInteraction interaction = interactions.get(click);
            if (interaction == null) {
                throw new IllegalStateException(missingMessage);
            }
            interactions.put(click, transform.apply(interaction));
            return this;
        }

        public Builder skipPrompt() {
            this.promptSuppressed = true;
            return this;
        }

        public Builder amount(int amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be greater than zero");
            }
            this.amount = amount;
            return this;
        }

        public MenuButton build() {
            return new MenuButton(this);
        }

        private int amount() {
            return amount;
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
