package sh.harold.library.npc.behavior.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
        String plain = PlainTextComponentSerializer.plainText().serialize(text);
        WrapCursor cursor = new WrapCursor(planWords(plain, width));
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

    private static List<WordLayout> planWords(String plain, int width) {
        List<WordLayout> planned = new ArrayList<>();
        List<Integer> paragraph = new ArrayList<>();
        int wordLength = 0;
        Matcher matcher = GRAPHEME.matcher(plain);
        while (matcher.find()) {
            String grapheme = matcher.group();
            if (isLineBreak(grapheme)) {
                if (wordLength > 0) {
                    paragraph.add(wordLength);
                    wordLength = 0;
                }
                planParagraph(paragraph, width, planned);
                paragraph.clear();
            } else if (isWhitespace(grapheme)) {
                if (wordLength > 0) {
                    paragraph.add(wordLength);
                    wordLength = 0;
                }
            } else {
                wordLength++;
            }
        }
        if (wordLength > 0) {
            paragraph.add(wordLength);
        }
        planParagraph(paragraph, width, planned);
        return planned;
    }

    private static void planParagraph(List<Integer> words, int width, List<WordLayout> output) {
        if (words.isEmpty()) {
            return;
        }
        List<PlannedLine> lines = greedyLines(words, width);
        rebalanceAdjacentLines(words, width, lines);
        boolean[] breakBefore = new boolean[words.size()];
        for (PlannedLine line : lines) {
            if (line.start > 0) {
                breakBefore[line.start] = true;
            }
        }
        for (int index = 0; index < words.size(); index++) {
            output.add(new WordLayout(words.get(index), breakBefore[index]));
        }
    }

    /** Greedy packing is linear and never joins a word to a line it would overflow. */
    private static List<PlannedLine> greedyLines(List<Integer> words, int width) {
        List<PlannedLine> lines = new ArrayList<>();
        int start = 0;
        int length = 0;
        for (int index = 0; index < words.size(); index++) {
            int wordLength = words.get(index);
            int candidate = index == start ? wordLength : length + 1 + wordLength;
            if (index > start && candidate > width) {
                lines.add(new PlannedLine(start, index, length));
                start = index;
                length = wordLength;
            } else {
                length = candidate;
            }
        }
        lines.add(new PlannedLine(start, words.size(), length));
        return lines;
    }

    /**
     * Softens a sparse trailing line when the preceding line can donate a word
     * without overflowing it. Each word moves at most once, keeping the whole
     * planner linear while producing more consistent word counts.
     */
    private static void rebalanceAdjacentLines(
            List<Integer> words,
            int width,
            List<PlannedLine> lines
    ) {
        for (int index = lines.size() - 1; index > 0; index--) {
            PlannedLine left = lines.get(index - 1);
            PlannedLine right = lines.get(index);
            while (left.wordCount() > right.wordCount() + 1) {
                int movedLength = words.get(left.end - 1);
                if (movedLength + 1 + right.length > width) {
                    break;
                }
                left.removeLast(movedLength);
                right.prepend(movedLength);
            }
        }
    }

    private static boolean isLineBreak(String grapheme) {
        return "\n".equals(grapheme) || "\r".equals(grapheme) || "\r\n".equals(grapheme);
    }

    private static boolean isWhitespace(String grapheme) {
        return grapheme.codePoints().allMatch(Character::isWhitespace);
    }

    private static final class WrapCursor {
        private final Deque<WordLayout> words;
        private final StringBuilder pendingWhitespace = new StringBuilder();
        private int column;
        private int pendingWhitespaceWidth;
        private boolean inWord;

        private WrapCursor(List<WordLayout> words) {
            this.words = new ArrayDeque<>(words);
        }

        private String wrap(String content) {
            StringBuilder output = new StringBuilder(content.length() + 8);
            Matcher matcher = GRAPHEME.matcher(content);
            while (matcher.find()) {
                String grapheme = matcher.group();
                if (isLineBreak(grapheme)) {
                    pendingWhitespace.setLength(0);
                    pendingWhitespaceWidth = 0;
                    output.append(grapheme);
                    column = 0;
                    inWord = false;
                    continue;
                }
                if (isWhitespace(grapheme)) {
                    pendingWhitespace.append(grapheme);
                    pendingWhitespaceWidth++;
                    inWord = false;
                    continue;
                }
                if (!inWord) {
                    WordLayout word = words.pollFirst();
                    if (word == null) {
                        throw new IllegalStateException("NPC speech word plan was exhausted early");
                    }
                    if (word.breakBefore() && column > 0) {
                        output.append('\n');
                        column = 0;
                        pendingWhitespace.setLength(0);
                        pendingWhitespaceWidth = 0;
                    } else if (column > 0 && !pendingWhitespace.isEmpty()) {
                        output.append(pendingWhitespace);
                        column += pendingWhitespaceWidth;
                    }
                    pendingWhitespace.setLength(0);
                    pendingWhitespaceWidth = 0;
                    inWord = true;
                }
                output.append(grapheme);
                column++;
            }
            return output.toString();
        }

        private void advance(String content) {
            wrap(content);
        }
    }

    private record WordLayout(int length, boolean breakBefore) {
        private WordLayout {
            if (length < 1) {
                throw new IllegalArgumentException("word length must be positive");
            }
        }
    }

    private static final class PlannedLine {
        private int start;
        private int end;
        private int length;

        private PlannedLine(int start, int end, int length) {
            this.start = start;
            this.end = end;
            this.length = length;
        }

        private int wordCount() {
            return end - start;
        }

        private void removeLast(int wordLength) {
            end--;
            length -= wordLength;
            if (end > start) {
                length--;
            }
        }

        private void prepend(int wordLength) {
            start--;
            length += wordLength + 1;
        }
    }
}
