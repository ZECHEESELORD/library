package sh.harold.creative.library.menu;

import sh.harold.creative.library.ui.value.UiValue;

import java.util.Objects;

public record MenuPair(String key, UiValue value) {

    public MenuPair {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key cannot be blank");
        }
        Objects.requireNonNull(value, "value");
    }

    public static MenuPair of(String key, UiValue value) {
        return new MenuPair(key, value);
    }

    public static MenuPair of(String key, Object value) {
        return new MenuPair(key, UiValue.of(Objects.requireNonNull(value, "value")));
    }
}
