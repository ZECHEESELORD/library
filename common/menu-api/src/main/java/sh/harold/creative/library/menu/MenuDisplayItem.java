package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Optional;

public final class MenuDisplayItem implements MenuItem {

    private final MenuIcon icon;
    private final Component name;
    private final String secondary;
    private final List<MenuBlock> blocks;
    private final List<Component> exactLore;
    private final boolean glow;
    private final int amount;
    private final MenuTooltipBehavior tooltipBehavior;

    private MenuDisplayItem(Builder builder) {
        this.icon = builder.icon();
        this.name = builder.name();
        this.secondary = builder.secondary();
        this.blocks = builder.blocks();
        this.exactLore = builder.exactLore();
        this.glow = builder.isGlowing();
        this.amount = builder.amount();
        this.tooltipBehavior = builder.tooltipBehavior();
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
