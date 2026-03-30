package sh.harold.creative.library.menu;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public sealed interface MenuBlock permits MenuBlock.Description, MenuBlock.Lines, MenuBlock.Pairs, MenuBlock.Bullets, MenuBlock.Progress {

    enum WrapMode {
        /**
         * Render each entry on a single lore line.
         */
        SINGLE_LINE,
        /**
         * Allow a single long entry to rebalance into wrapped lore using the shared
         * character-count rules.
         * Multi-entry {@code lines(...)} and {@code pairs(...)} blocks still keep one entry per lore line.
         */
        SOFT
    }

    record Description(String text) implements MenuBlock {

        public Description {
            requireText(text, "text");
        }
    }

    record Lines(List<String> lines, WrapMode wrapMode) implements MenuBlock {

        public Lines {
            lines = immutableLines(lines, "lines");
            wrapMode = Objects.requireNonNull(wrapMode, "wrapMode");
        }
    }

    record Pairs(List<Entry> pairs, WrapMode wrapMode) implements MenuBlock {

        public Pairs {
            Objects.requireNonNull(pairs, "pairs");
            pairs = List.copyOf(pairs);
            if (pairs.isEmpty()) {
                throw new IllegalArgumentException("pairs cannot be empty");
            }
            wrapMode = Objects.requireNonNull(wrapMode, "wrapMode");
        }

        public record Entry(String key, String value) {

            public Entry {
                requireText(key, "key");
                requireText(value, "value");
            }
        }
    }

    record Bullets(List<String> bullets) implements MenuBlock {

        public Bullets {
            bullets = immutableLines(bullets, "bullets");
        }
    }

    record Progress(String label, BigDecimal current, BigDecimal max, AccentFamily accentFamily) implements MenuBlock {

        public Progress {
            requireText(label, "label");
            current = requireNumber(current, "current");
            max = requireNumber(max, "max");
            if (max.signum() <= 0) {
                throw new IllegalArgumentException("max must be greater than zero");
            }
            accentFamily = Objects.requireNonNull(accentFamily, "accentFamily");
        }
    }

    private static List<String> immutableLines(List<String> lines, String label) {
        Objects.requireNonNull(lines, label);
        List<String> copy = List.copyOf(lines);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be empty");
        }
        for (String line : copy) {
            requireText(line, label + " entry");
        }
        return copy;
    }

    private static BigDecimal requireNumber(BigDecimal value, String label) {
        Objects.requireNonNull(value, label);
        return value;
    }

    private static void requireText(String text, String label) {
        Objects.requireNonNull(text, label);
        if (text.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
    }
}
