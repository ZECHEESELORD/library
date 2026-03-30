package sh.harold.creative.library.menu;

import sh.harold.creative.library.ui.value.UiValue;

import java.util.Objects;

public record MenuValueLine(String prefix, UiValue value) {

    public MenuValueLine {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(value, "value");
    }

    public static MenuValueLine of(String prefix, UiValue value) {
        return new MenuValueLine(prefix, value);
    }

    public static MenuValueLine of(String prefix, Object value) {
        return new MenuValueLine(prefix, UiValue.of(Objects.requireNonNull(value, "value")));
    }
}
