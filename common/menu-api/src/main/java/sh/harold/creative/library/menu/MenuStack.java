package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Optional;

public final class MenuStack implements MenuItem {

    private final MenuIcon icon;
    private final Component name;
    private final String secondary;
    private final List<MenuBlock> blocks;
    private final boolean glow;
    private final int amount;

    private MenuStack(Builder builder) {
        this.icon = builder.icon();
        this.name = builder.name();
        this.secondary = builder.secondary();
        this.blocks = builder.blocks();
        this.glow = builder.isGlowing();
        this.amount = builder.amount;
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
    public boolean glow() {
        return glow;
    }

    @Override
    public int amount() {
        return amount;
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

        public MenuStack build() {
            return new MenuStack(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
