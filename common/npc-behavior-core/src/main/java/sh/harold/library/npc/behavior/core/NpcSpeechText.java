package sh.harold.library.npc.behavior.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rich-component-safe speech layout and timing rules. */
public final class NpcSpeechText {

    public static final int DEFAULT_WRAP_GRAPHEMES = 40;
    public static final int BREATH_TICKS = 8;
    private static final Pattern GRAPHEME = Pattern.compile("\\X");

    private NpcSpeechText() {
    }

    /**
     * Inserts client-friendly line breaks without flattening the authored
     * component, its styles, hover events, or click events.
     */
    public static Component wrap(Component text) {
        return wrap(text, DEFAULT_WRAP_GRAPHEMES);
    }

    public static Component wrap(Component text, int width) {
        Objects.requireNonNull(text, "text");
        if (width < 1) {
            throw new IllegalArgumentException("width must be positive");
        }
        WrapCursor cursor = new WrapCursor(width);
        return wrapComponent(text, cursor);
    }

    public static int graphemeCount(Component text) {
        Objects.requireNonNull(text, "text");
        String plain = PlainTextComponentSerializer.plainText().serialize(text);
        Matcher matcher = GRAPHEME.matcher(plain);
        int count = 0;
        while (matcher.find()) {
            if (!"\n".equals(matcher.group())) {
                count++;
            }
        }
        return count;
    }

    /**
     * min(10s, max(2s, 0.5s + graphemes / 15s)), represented at 20 Hz.
     */
    public static int holdTicks(Component text) {
        double seconds = Math.min(10.0, Math.max(2.0, 0.5 + (graphemeCount(text) / 15.0)));
        return (int) Math.ceil(seconds * 20.0);
    }

    private static Component wrapComponent(Component component, WrapCursor cursor) {
        Component transformed = component;
        if (component instanceof TextComponent textComponent) {
            transformed = textComponent.content(cursor.wrap(textComponent.content()));
        } else {
            // We cannot safely split a translated/keybind component. Count its
            // visible representation so following rich text still wraps near
            // the requested width.
            cursor.advance(PlainTextComponentSerializer.plainText().serialize(component.children(List.of())));
        }

        if (component.children().isEmpty()) {
            return transformed;
        }
        List<Component> children = new ArrayList<>(component.children().size());
        for (Component child : component.children()) {
            children.add(wrapComponent(child, cursor));
        }
        return transformed.children(children);
    }

    private static final class WrapCursor {
        private final int width;
        private int column;

        private WrapCursor(int width) {
            this.width = width;
        }

        private String wrap(String content) {
            StringBuilder output = new StringBuilder(content.length() + 8);
            Matcher matcher = GRAPHEME.matcher(content);
            while (matcher.find()) {
                String grapheme = matcher.group();
                if ("\n".equals(grapheme)) {
                    output.append(grapheme);
                    column = 0;
                    continue;
                }
                boolean whitespace = grapheme.codePoints().allMatch(Character::isWhitespace);
                if (column >= width) {
                    output.append('\n');
                    column = 0;
                    if (whitespace) {
                        continue;
                    }
                }
                output.append(grapheme);
                column++;
            }
            return output.toString();
        }

        private void advance(String content) {
            Matcher matcher = GRAPHEME.matcher(content);
            while (matcher.find()) {
                if ("\n".equals(matcher.group())) {
                    column = 0;
                } else {
                    column = (column + 1) % width;
                }
            }
        }
    }
}
