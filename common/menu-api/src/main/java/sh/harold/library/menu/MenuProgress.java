package sh.harold.library.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;

import java.math.BigDecimal;
import java.util.Objects;

public record MenuProgress(
        Component label,
        BigDecimal current,
        BigDecimal max,
        String unit,
        MenuProgressPalette palette
) {

    public MenuProgress {
        label = MenuComponents.requireContent(Objects.requireNonNull(label, "label"), "label");
        current = Objects.requireNonNull(current, "current");
        max = Objects.requireNonNull(max, "max");
        if (max.signum() <= 0) {
            throw new IllegalArgumentException("max must be greater than zero");
        }
        if (unit != null) {
            unit = MenuComponents.requireText(unit, "unit").trim();
        }
        palette = Objects.requireNonNull(palette, "palette");
    }

    public static Builder builder(String label, Number current, Number max) {
        return builder(Component.text(MenuComponents.requireText(label, "label")), current, max);
    }

    public static Builder builder(ComponentLike label, Number current, Number max) {
        return new Builder(
                Objects.requireNonNull(label, "label").asComponent(),
                decimal(current, "current"),
                decimal(max, "max"));
    }

    public static MenuProgress of(String label, Number current, Number max) {
        return builder(label, current, max).build();
    }

    public static MenuProgress of(ComponentLike label, Number current, Number max) {
        return builder(label, current, max).build();
    }

    private static BigDecimal decimal(Number value, String label) {
        Objects.requireNonNull(value, label);
        return new BigDecimal(String.valueOf(value));
    }

    public static final class Builder {

        private final Component label;
        private final BigDecimal current;
        private final BigDecimal max;
        private String unit;
        private MenuProgressPalette palette = MenuProgressPalette.STANDARD;

        private Builder(Component label, BigDecimal current, BigDecimal max) {
            this.label = label;
            this.current = current;
            this.max = max;
        }

        public Builder unit(String unit) {
            this.unit = MenuComponents.requireText(unit, "unit").trim();
            return this;
        }

        public Builder palette(MenuProgressPalette palette) {
            this.palette = Objects.requireNonNull(palette, "palette");
            return this;
        }

        public MenuProgress build() {
            return new MenuProgress(label, current, max, unit, palette);
        }
    }
}
