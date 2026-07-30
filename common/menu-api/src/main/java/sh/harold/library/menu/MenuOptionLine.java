package sh.harold.library.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.TextColor;

import java.util.Objects;

public record MenuOptionLine(Component label, TextColor color, boolean selected) {

    public MenuOptionLine {
        label = MenuComponents.requireContent(Objects.requireNonNull(label, "label"), "label");
        color = Objects.requireNonNull(color, "color");
    }

    public MenuOptionLine(String label, TextColor color, boolean selected) {
        this(Component.text(MenuComponents.requireText(label, "label")), color, selected);
    }

    public static MenuOptionLine of(ComponentLike label, TextColor color, boolean selected) {
        return new MenuOptionLine(Objects.requireNonNull(label, "label").asComponent(), color, selected);
    }
}
