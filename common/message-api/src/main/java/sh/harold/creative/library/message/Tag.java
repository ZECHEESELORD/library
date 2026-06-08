package sh.harold.creative.library.message;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Optional;

public enum Tag {
    STAFF("STAFF", NamedTextColor.AQUA),
    DAEMON("DAEMON"),
    DEBUG("DEBUG", NamedTextColor.GRAY);

    private final String label;
    private final TextColor color;

    Tag(String label) {
        this(label, null);
    }

    Tag(String label, TextColor color) {
        this.label = label;
        this.color = color;
    }

    public String label() {
        return label;
    }

    public Optional<TextColor> color() {
        return Optional.ofNullable(color);
    }
}
