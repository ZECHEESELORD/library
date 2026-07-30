package sh.harold.library.menu.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.HashMap;
import java.util.Map;

final class MinecraftFontMetrics {

    private static final Map<Integer, Integer> WIDTHS = new HashMap<>();

    static {
        put(2, "!,.:;|i");
        put(3, "'`l");
        put(4, " ()[]{}Itfk<>*");
        put(5, "\"/\\t+?-_~^");
        put(6, "0123456789ABCDEFGHJKLMNOPQRSTUVWXYZabcdefghjmnopqrstuvwxyz#$%&=@");
        put(7, "MNQRSUVWXYmnqrsuvwxyz");
    }

    private MinecraftFontMetrics() {
    }

    static int width(Component component) {
        if (component == null) {
            return 0;
        }
        WidthAccumulator accumulator = new WidthAccumulator();
        append(component, Style.empty(), accumulator);
        return accumulator.width();
    }

    static int width(String text) {
        return width(text, false);
    }

    static int width(String text, boolean bold) {
        WidthAccumulator accumulator = new WidthAccumulator();
        text.codePoints().forEach(codePoint -> accumulator.add(codePoint, bold));
        return accumulator.width();
    }

    static int codePointWidth(int codePoint, boolean bold) {
        int width = WIDTHS.getOrDefault(codePoint, 6);
        if (codePoint == ' ') {
            return 4;
        }
        return bold ? width + 1 : width;
    }

    private static void append(Component component, Style inherited, WidthAccumulator accumulator) {
        Style resolved = inherited.merge(component.style(), Style.Merge.Strategy.ALWAYS);
        boolean bold = resolved.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
        if (component instanceof TextComponent textComponent) {
            textComponent.content().codePoints().forEach(codePoint -> accumulator.add(codePoint, bold));
        }
        for (Component child : component.children()) {
            append(child, resolved, accumulator);
        }
    }

    private static void put(int width, String characters) {
        characters.codePoints().forEach(codePoint -> WIDTHS.put(codePoint, width));
    }

    private static final class WidthAccumulator {

        private int width;
        private boolean empty = true;

        private void add(int codePoint, boolean bold) {
            width += codePointWidth(codePoint, bold) + 1;
            empty = false;
        }

        private int width() {
            return empty ? 0 : width - 1;
        }
    }
}
