package sh.harold.library.menu;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MenuDisplayItem implements MenuItem {

    private final MenuIcon icon;
    private final Component name;
    private final Component secondary;
    private final List<MenuSection> sections;
    private final List<Component> statusLines;
    private final List<Component> exactLore;
    private final boolean glow;
    private final int amount;
    private final MenuTooltipBehavior tooltipBehavior;

    private MenuDisplayItem(Builder builder) {
        this(builder.icon(), builder.name(), builder.secondary(), builder.sections(), builder.statusLines(),
                builder.exactLore(), builder.isGlowing(), builder.amount(), builder.tooltipBehavior());
    }

    MenuDisplayItem(
            MenuIcon icon,
            Component name,
            Component secondary,
            List<MenuSection> sections,
            List<Component> statusLines,
            List<Component> exactLore,
            boolean glow,
            int amount,
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
        this.tooltipBehavior = Objects.requireNonNull(tooltipBehavior, "tooltipBehavior");
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
    public MenuTooltipBehavior tooltipBehavior() {
        return tooltipBehavior;
    }

    public static final class Builder extends AbstractMenuItemBuilder<Builder> {

        private int amount = 1;

        private Builder(MenuIcon icon) {
            super(icon);
        }

        public Builder amount(int amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be greater than zero");
            }
            this.amount = amount;
            return this;
        }

        public MenuDisplayItem build() {
            return new MenuDisplayItem(this);
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
