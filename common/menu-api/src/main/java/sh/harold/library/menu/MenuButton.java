package sh.harold.library.menu;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

public final class MenuButton implements MenuItem {

    private final MenuIcon icon;
    private final Component name;
    private final Component secondary;
    private final List<MenuSection> sections;
    private final List<Component> statusLines;
    private final List<Component> exactLore;
    private final boolean glow;
    private final int amount;
    private final Map<MenuClick, MenuInteraction> interactions;
    private final boolean promptSuppressed;
    private final MenuTooltipBehavior tooltipBehavior;

    private MenuButton(Builder builder) {
        this(builder.icon(), builder.name(), builder.secondary(), builder.sections(), builder.statusLines(),
                builder.exactLore(), builder.isGlowing(), builder.amount(), builder.interactions,
                builder.promptSuppressed, builder.tooltipBehavior());
    }

    MenuButton(
            MenuIcon icon,
            Component name,
            Component secondary,
            List<MenuSection> sections,
            List<Component> statusLines,
            List<Component> exactLore,
            boolean glow,
            int amount,
            Map<MenuClick, MenuInteraction> interactions,
            boolean promptSuppressed,
            MenuTooltipBehavior tooltipBehavior
    ) {
        this.icon = Objects.requireNonNull(icon, "icon");
        this.name = Objects.requireNonNull(name, "name");
        this.secondary = secondary;
        this.sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        this.statusLines = List.copyOf(Objects.requireNonNull(statusLines, "statusLines"));
        this.exactLore = exactLore == null ? null : List.copyOf(exactLore);
        this.glow = glow;
        this.amount = amount;
        this.interactions = Map.copyOf(Objects.requireNonNull(interactions, "interactions"));
        this.promptSuppressed = promptSuppressed;
        this.tooltipBehavior = Objects.requireNonNull(tooltipBehavior, "tooltipBehavior");
        if (this.interactions.isEmpty()) {
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
    public Optional<Component> secondary() {
        return Optional.ofNullable(secondary);
    }

    @Override
    public List<MenuSection> sections() {
        return sections;
    }

    @Override
    public List<Component> statusLines() {
        return statusLines;
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

        public Builder onShiftLeftClick(ActionVerb verb, MenuAction action) {
            return onShiftLeftClick(verb, verb.promptLabel(), action);
        }

        public Builder onShiftLeftClick(ActionVerb verb, String promptLabel, MenuAction action) {
            interactions.put(MenuClick.SHIFT_LEFT, MenuInteraction.of(verb, promptLabel, new MenuSlotAction.Execute(action)));
            return this;
        }

        public Builder onShiftRightClick(ActionVerb verb, MenuAction action) {
            return onShiftRightClick(verb, verb.promptLabel(), action);
        }

        public Builder onShiftRightClick(ActionVerb verb, String promptLabel, MenuAction action) {
            interactions.put(MenuClick.SHIFT_RIGHT, MenuInteraction.of(verb, promptLabel, new MenuSlotAction.Execute(action)));
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

        private Builder updateInteraction(MenuClick click, UnaryOperator<MenuInteraction> transform, String message) {
            MenuInteraction interaction = interactions.get(click);
            if (interaction == null) {
                throw new IllegalStateException(message);
            }
            interactions.put(click, transform.apply(interaction));
            return this;
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
