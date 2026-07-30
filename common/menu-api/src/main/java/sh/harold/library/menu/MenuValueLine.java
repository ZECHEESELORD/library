package sh.harold.library.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;

import java.util.Objects;

public record MenuValueLine(Component prefix, Component value) {

    public MenuValueLine {
        prefix = Objects.requireNonNull(prefix, "prefix");
        value = MenuComponents.requireContent(Objects.requireNonNull(value, "value"), "value");
    }

    public MenuValueLine(String prefix, Object value) {
        this(Component.text(Objects.requireNonNull(prefix, "prefix")), MenuComponents.component(value));
    }

    public static MenuValueLine of(String prefix, Object value) {
        return new MenuValueLine(prefix, value);
    }

    public static MenuValueLine of(ComponentLike prefix, ComponentLike value) {
        return new MenuValueLine(
                Objects.requireNonNull(prefix, "prefix").asComponent(),
                Objects.requireNonNull(value, "value").asComponent());
    }
}
