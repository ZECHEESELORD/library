package sh.harold.creative.library.message;

import net.kyori.adventure.text.format.TextColor;

import java.util.Objects;

public record Topic(String label, TextColor color) {

    public Topic {
        Objects.requireNonNull(label, "label");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label cannot be blank");
        }
        Objects.requireNonNull(color, "color");
    }

    public static Topic of(String label, TextColor color) {
        return new Topic(label, color);
    }

    public static Topic of(String label, int rgbHex) {
        return new Topic(label, TextColor.color(requireRgb(rgbHex)));
    }

    private static int requireRgb(int rgbHex) {
        if (rgbHex < 0x000000 || rgbHex > 0xFFFFFF) {
            throw new IllegalArgumentException("rgbHex must be between 0x000000 and 0xFFFFFF");
        }
        return rgbHex;
    }
}
