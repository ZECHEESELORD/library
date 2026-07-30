package sh.harold.library.menu;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public enum MenuProgressPalette {

    STANDARD(
            NamedTextColor.GREEN,
            NamedTextColor.DARK_GREEN,
            NamedTextColor.YELLOW,
            NamedTextColor.GOLD),
    AQUA(
            NamedTextColor.AQUA,
            NamedTextColor.DARK_AQUA,
            NamedTextColor.AQUA,
            NamedTextColor.DARK_AQUA);

    private final TextColor percent;
    private final TextColor filled;
    private final TextColor value;
    private final TextColor separator;

    MenuProgressPalette(TextColor percent, TextColor filled, TextColor value, TextColor separator) {
        this.percent = percent;
        this.filled = filled;
        this.value = value;
        this.separator = separator;
    }

    public TextColor percent() {
        return percent;
    }

    public TextColor filled() {
        return filled;
    }

    public TextColor value() {
        return value;
    }

    public TextColor separator() {
        return separator;
    }
}
