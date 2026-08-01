package sh.harold.library.message.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.HashMap;
import java.util.Map;

final class ChatFontMetrics {

    private static final int DEFAULT_ADVANCE = 6;
    private static final int SPACE_ADVANCE = 4;
    private static final Map<Integer, Integer> ADVANCES = new HashMap<>();

    static {
        put(2, "!',.:;i|");
        put(3, "`l\u2022");
        put(4, "\"()*I[]t{}");
        put(5, "<>fk");
        put(6, "#$%&+-/0123456789=?ABCDEFGHJKLMNOPQRSTUVWXYZ\\^_abcdeghjmnopqrsuvwxyz");
        put(7, "@~");
    }

    private ChatFontMetrics() {
    }

    static int width(Component component) {
        WidthAccumulator accumulator = new WidthAccumulator();
        append(component, Style.empty(), accumulator);
        return accumulator.width();
    }

    static int spaceWidth() {
        return SPACE_ADVANCE;
    }

    private static int codePointWidth(int codePoint, boolean bold) {
        int advance = codePoint == ' '
                ? SPACE_ADVANCE
                : ADVANCES.getOrDefault(codePoint, DEFAULT_ADVANCE);
        return bold ? advance + 1 : advance;
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

    private static void put(int advance, String characters) {
        characters.codePoints().forEach(codePoint -> ADVANCES.put(codePoint, advance));
    }

    private static final class WidthAccumulator {

        private int width;

        private void add(int codePoint, boolean bold) {
            width += codePointWidth(codePoint, bold);
        }

        private int width() {
            return width;
        }
    }
}
