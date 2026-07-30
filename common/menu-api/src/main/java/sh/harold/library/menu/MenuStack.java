package sh.harold.library.menu;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Optional;

/**
 * A stack-shaped menu presentation.
 *
 * <p>This type is not a lossless representation of a platform item. Real item ownership and
 * movement must stay in the platform adapter's custody runtime.</p>
 */
public final class MenuStack implements MenuItem {

    private final MenuIcon icon;
    private final Component name;
    private final Component secondary;
    private final List<MenuSection> sections;
    private final List<Component> statusLines;
    private final List<Component> exactLore;
    private final boolean glow;
    private final int amount;
    private final MenuTooltipBehavior tooltipBehavior;

    private MenuStack(Builder builder) {
        this.icon = builder.icon();
        this.name = builder.name();
        this.secondary = builder.secondary();
        this.sections = builder.sections();
        this.statusLines = builder.statusLines();
        this.exactLore = builder.exactLore();
        this.glow = builder.isGlowing();
        this.amount = builder.amount;
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
            literalItem();
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
