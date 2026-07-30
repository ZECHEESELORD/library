package sh.harold.library.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;

import java.util.Objects;

public record MenuPair(Component key, Component value) {

    public MenuPair {
        key = MenuComponents.requireContent(Objects.requireNonNull(key, "key"), "key");
        value = MenuComponents.requireContent(Objects.requireNonNull(value, "value"), "value");
    }

    public MenuPair(String key, Object value) {
        this(Component.text(MenuComponents.requireText(key, "key")), MenuComponents.component(value));
    }

    public static MenuPair of(String key, Object value) {
        return new MenuPair(key, value);
    }

    public static MenuPair of(ComponentLike key, ComponentLike value) {
        return new MenuPair(
                Objects.requireNonNull(key, "key").asComponent(),
                Objects.requireNonNull(value, "value").asComponent());
    }
}
