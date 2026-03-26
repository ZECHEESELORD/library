package sh.harold.creative.library.menu;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public enum AccentFamily {

    RED(NamedTextColor.RED, NamedTextColor.DARK_RED),
    GOLD(NamedTextColor.YELLOW, NamedTextColor.GOLD),
    GREEN(NamedTextColor.GREEN, NamedTextColor.DARK_GREEN),
    AQUA(NamedTextColor.AQUA, NamedTextColor.DARK_AQUA),
    BLUE(NamedTextColor.BLUE, NamedTextColor.DARK_BLUE),
    PURPLE(NamedTextColor.LIGHT_PURPLE, NamedTextColor.DARK_PURPLE);

    private final TextColor light;
    private final TextColor dark;

    AccentFamily(TextColor light, TextColor dark) {
        this.light = light;
        this.dark = dark;
    }

    public TextColor light() {
        return light;
    }

    public TextColor dark() {
        return dark;
    }
}
