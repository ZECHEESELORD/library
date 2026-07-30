package sh.harold.library.ui.value;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.TextColor;

import java.util.Objects;

public record UiValue(String text, TextColor colorOverride) implements ComponentLike {

    public UiValue {
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) {
            throw new IllegalArgumentException("text cannot be blank");
        }
    }

    public static UiValue of(String text) {
        return new UiValue(text, null);
    }

    public static UiValue of(Object value) {
        Objects.requireNonNull(value, "value");
        if (value instanceof UiValue uiValue) {
            return uiValue;
        }
        return of(String.valueOf(value));
    }

    public UiValue color(TextColor color) {
        return new UiValue(text, Objects.requireNonNull(color, "color"));
    }

    public UiValue color(int rgbHex) {
        return color(TextColor.color(requireRgb(rgbHex)));
    }

    @Override
    public Component asComponent() {
        return colorOverride == null ? Component.text(text) : Component.text(text, colorOverride);
    }

    private static int requireRgb(int rgbHex) {
        if (rgbHex < 0x000000 || rgbHex > 0xFFFFFF) {
            throw new IllegalArgumentException("rgbHex must be between 0x000000 and 0xFFFFFF");
        }
        return rgbHex;
    }
}
