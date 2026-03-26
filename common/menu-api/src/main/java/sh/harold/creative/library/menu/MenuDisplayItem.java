package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Optional;

public final class MenuDisplayItem implements MenuItem {

    private final MenuIcon icon;
    private final Component name;
    private final String secondary;
    private final List<MenuBlock> blocks;
    private final boolean glow;

    private MenuDisplayItem(Builder builder) {
        this.icon = builder.icon();
        this.name = builder.name();
        this.secondary = builder.secondary();
        this.blocks = builder.blocks();
        this.glow = builder.isGlowing();
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

    public static final class Builder extends AbstractMenuItemBuilder<Builder> {

        private Builder(MenuIcon icon) {
            super(icon);
        }

        public MenuDisplayItem build() {
            return new MenuDisplayItem(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
