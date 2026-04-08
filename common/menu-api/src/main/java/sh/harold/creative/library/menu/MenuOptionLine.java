package sh.harold.creative.library.menu;

import net.kyori.adventure.text.format.TextColor;

import java.util.Objects;

public record MenuOptionLine(String label, TextColor color, boolean selected) {

    public MenuOptionLine {
        Objects.requireNonNull(label, "label");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label cannot be blank");
        }
        color = Objects.requireNonNull(color, "color");
    }
}
