package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import sh.harold.creative.library.menu.AccentFamily;
import sh.harold.creative.library.menu.MenuBlock;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuClick;
import sh.harold.creative.library.menu.MenuInteraction;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuSlot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class HouseMenuCompiler {

    private static final int SOFT_WRAP_START = 20;
    private static final int HARD_WRAP_LIMIT = 30;
    private static final int PROGRESS_BAR_WIDTH = 20;
    private static final double SCORE_EPSILON = 0.000001d;

    private static final TextColor STRONG_NEUTRAL = NamedTextColor.WHITE;
    private static final TextColor BODY_NEUTRAL = NamedTextColor.GRAY;
    private static final TextColor MUTED_NEUTRAL = NamedTextColor.DARK_GRAY;

    private HouseMenuCompiler() {
    }

    static MenuSlot compile(int slot, MenuItem item) {
        List<Component> lore = new ArrayList<>();
        appendSecondary(item, lore);
        appendBlocks(item, lore);
        appendPrompt(item, lore);
        return new MenuSlot(slot, item.icon(), item.name(), lore, item.glow(), interactions(item));
    }

    static int footerStart(int rows) {
        return (rows - 1) * 9;
    }

    private static void appendSecondary(MenuItem item, List<Component> lore) {
        item.secondary().ifPresent(secondary -> wrapText(secondary, 0, 0)
                .forEach(line -> lore.add(text(line, MUTED_NEUTRAL))));
    }

    private static void appendBlocks(MenuItem item, List<Component> lore) {
        for (MenuBlock block : item.blocks()) {
            List<Component> blockLines = renderBlock(block);
            if (blockLines.isEmpty()) {
                continue;
            }
            if (!lore.isEmpty()) {
                lore.add(Component.empty());
            }
            lore.addAll(blockLines);
        }
    }

    private static void appendPrompt(MenuItem item, List<Component> lore) {
        if (!(item instanceof MenuButton button) || button.promptSuppressed()) {
            return;
        }
        List<Component> promptLines = new ArrayList<>();
        MenuInteraction left = button.interactions().get(MenuClick.LEFT);
        if (left != null) {
            promptLines.add(promptLine("CLICK", left.promptLabel(), NamedTextColor.YELLOW));
        }
        MenuInteraction right = button.interactions().get(MenuClick.RIGHT);
        if (right != null) {
            promptLines.add(promptLine("RIGHT CLICK", right.promptLabel(), NamedTextColor.AQUA));
        }
        if (!promptLines.isEmpty()) {
            if (!lore.isEmpty()) {
                lore.add(Component.empty());
            }
            lore.addAll(promptLines);
        }
    }

    private static Map<MenuClick, MenuInteraction> interactions(MenuItem item) {
        if (item instanceof MenuButton button) {
            return button.interactions();
        }
        return Map.of();
    }

    private static List<Component> renderBlock(MenuBlock block) {
        return switch (block) {
            case MenuBlock.Description description -> wrapText(description.text(), 0, 0).stream()
                    .map(line -> text(line, BODY_NEUTRAL))
                    .toList();
            case MenuBlock.Lines lines -> renderLines(lines.lines(), lines.wrapMode());
            case MenuBlock.Pairs pairs -> renderPairs(pairs.pairs(), pairs.wrapMode());
            case MenuBlock.Bullets bullets -> renderBullets(bullets.bullets());
            case MenuBlock.Progress progress -> renderProgress(progress);
        };
    }

    private static List<Component> renderLines(List<String> lines, MenuBlock.WrapMode wrapMode) {
        List<Component> rendered = new ArrayList<>();
        boolean canWrap = wrapMode == MenuBlock.WrapMode.SOFT && lines.size() == 1;
        for (String line : lines) {
            List<String> wrapped = canWrap ? softWrap(line, 0, 0) : List.of(line);
            for (String wrappedLine : wrapped) {
                rendered.add(text(wrappedLine, BODY_NEUTRAL));
            }
        }
        return rendered;
    }

    private static List<Component> renderPairs(List<MenuBlock.Pairs.Entry> pairs, MenuBlock.WrapMode wrapMode) {
        List<Component> rendered = new ArrayList<>();
        boolean canWrap = wrapMode == MenuBlock.WrapMode.SOFT && pairs.size() == 1;
        for (MenuBlock.Pairs.Entry pair : pairs) {
            String prefix = pair.key() + ": ";
            if (canWrap) {
                List<String> wrapped = softWrap(pair.value(), prefix.length(), prefix.length());
                String indent = " ".repeat(prefix.length());
                for (int i = 0; i < wrapped.size(); i++) {
                    if (i == 0) {
                        rendered.add(pairLine(pair.key(), wrapped.get(i)));
                    } else {
                        rendered.add(text(indent + wrapped.get(i), STRONG_NEUTRAL));
                    }
                }
            } else {
                rendered.add(pairLine(pair.key(), pair.value()));
            }
        }
        return rendered;
    }

    private static List<Component> renderBullets(List<String> bullets) {
        List<Component> rendered = new ArrayList<>();
        for (String bullet : bullets) {
            List<String> wrapped = softWrap(bullet, 2, 2);
            for (int i = 0; i < wrapped.size(); i++) {
                if (i == 0) {
                    rendered.add(Component.text()
                            .append(text("• ", BODY_NEUTRAL))
                            .append(text(wrapped.get(i), BODY_NEUTRAL))
                            .decoration(TextDecoration.ITALIC, false)
                            .build());
                } else {
                    rendered.add(text("  " + wrapped.get(i), BODY_NEUTRAL));
                }
            }
        }
        return rendered;
    }

    private static List<Component> renderProgress(MenuBlock.Progress progress) {
        BigDecimal ratio = progress.current().divide(progress.max(), 6, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO)
                .min(BigDecimal.ONE);
        int filled = ratio.multiply(BigDecimal.valueOf(PROGRESS_BAR_WIDTH))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
        int empty = Math.max(0, PROGRESS_BAR_WIDTH - filled);
        AccentFamily accent = progress.accentFamily();
        String filledBar = "-".repeat(Math.max(0, filled));
        String emptyBar = "-".repeat(empty);
        Component firstLine = Component.text()
                .append(text(progress.label() + ": ", BODY_NEUTRAL))
                .append(text(HouseNumberFormatter.formatPercent(ratio) + "%", accent.light()))
                .decoration(TextDecoration.ITALIC, false)
                .build();
        Component secondLine = Component.text()
                .append(text(filledBar, accent.light(), false, true))
                .append(text(emptyBar, BODY_NEUTRAL, false, true))
                .append(Component.space())
                .append(text(HouseNumberFormatter.format(progress.current()), accent.light()))
                .append(text("/", accent.dark()))
                .append(text(HouseNumberFormatter.format(progress.max()), accent.light()))
                .decoration(TextDecoration.ITALIC, false)
                .build();
        return List.of(firstLine, secondLine);
    }

    private static Component pairLine(String key, String value) {
        return Component.text()
                .append(text(key + ": ", BODY_NEUTRAL))
                .append(text(value, STRONG_NEUTRAL))
                .decoration(TextDecoration.ITALIC, false)
                .build();
    }

    private static Component promptLine(String clickLabel, String promptLabel, NamedTextColor color) {
        return Component.text()
                .append(text(clickLabel, color, true))
                .append(text(" to " + emphaticPromptLabel(promptLabel), color))
                .decoration(TextDecoration.ITALIC, false)
                .build();
    }

    private static String emphaticPromptLabel(String promptLabel) {
        return promptLabel.endsWith("!") ? promptLabel : promptLabel + "!";
    }

    private static Component text(String text, TextColor color) {
        return text(text, color, false);
    }

    private static Component text(String text, TextColor color, boolean bold) {
        return text(text, color, bold, false);
    }

    private static Component text(String text, TextColor color, boolean bold, boolean strikethrough) {
        return Component.text(text, color)
                .decoration(TextDecoration.BOLD, bold)
                .decoration(TextDecoration.STRIKETHROUGH, strikethrough)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static List<String> softWrap(String text, int firstIndentChars, int continuationIndentChars) {
        return wrapText(text, firstIndentChars, continuationIndentChars);
    }

    private static List<String> wrapText(String text, int firstIndentChars, int continuationIndentChars) {
        String normalized = normalize(text);
        if (normalized.length() + firstIndentChars <= HARD_WRAP_LIMIT) {
            return List.of(normalized);
        }
        String[] words = normalized.split(" ");
        WrappedLayout best = new WrappedLayout(List.of(), List.of(), List.of());
        List<WrappedLayout> candidates = new ArrayList<>();
        collectLayouts(words, 0, firstIndentChars, continuationIndentChars, false,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), candidates);
        for (WrappedLayout candidate : candidates) {
            if (best.lines().isEmpty() || compareLayouts(candidate, best) < 0) {
                best = candidate;
            }
        }
        return best.lines().isEmpty() ? List.of(normalized) : best.lines();
    }

    private static String normalize(String text) {
        return text.trim().replaceAll("\\s+", " ");
    }

    private static void collectLayouts(
            String[] words,
            int start,
            int firstIndentChars,
            int continuationIndentChars,
            boolean continuation,
            List<String> lines,
            List<Integer> visibleLengths,
            List<Integer> breaks,
            List<WrappedLayout> candidates
    ) {
        int indentChars = continuation ? continuationIndentChars : firstIndentChars;
        for (int end : candidateEnds(words, start, indentChars)) {
            String line = joinWords(words, start, end);
            lines.add(line);
            visibleLengths.add(indentChars + line.length());
            breaks.add(end);
            if (end == words.length - 1) {
                candidates.add(new WrappedLayout(List.copyOf(lines), List.copyOf(visibleLengths), List.copyOf(breaks)));
            } else {
                collectLayouts(words, end + 1, firstIndentChars, continuationIndentChars, true,
                        lines, visibleLengths, breaks, candidates);
            }
            lines.remove(lines.size() - 1);
            visibleLengths.remove(visibleLengths.size() - 1);
            breaks.remove(breaks.size() - 1);
        }
    }

    private static List<Integer> candidateEnds(String[] words, int start, int indentChars) {
        List<Integer> ends = new ArrayList<>();
        int budget = Math.max(1, HARD_WRAP_LIMIT - indentChars);
        int currentLength = 0;
        for (int end = start; end < words.length; end++) {
            int spacer = end == start ? 0 : 1;
            int candidateLength = currentLength + spacer + words[end].length();
            if (candidateLength > budget) {
                if (end == start) {
                    ends.add(end);
                }
                break;
            }
            currentLength = candidateLength;
            ends.add(end);
        }
        return ends;
    }

    private static String joinWords(String[] words, int start, int end) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i <= end; i++) {
            if (i > start) {
                builder.append(' ');
            }
            builder.append(words[i]);
        }
        return builder.toString();
    }

    private static int compareLayouts(WrappedLayout left, WrappedLayout right) {
        int lineCountComparison = Integer.compare(left.lines().size(), right.lines().size());
        if (lineCountComparison != 0) {
            return lineCountComparison;
        }
        int scoreComparison = Double.compare(left.imbalanceScore(), right.imbalanceScore());
        if (scoreComparison != 0 && Math.abs(left.imbalanceScore() - right.imbalanceScore()) > SCORE_EPSILON) {
            return scoreComparison;
        }
        int softTargetComparison = Double.compare(left.softTargetScore(), right.softTargetScore());
        if (softTargetComparison != 0 && Math.abs(left.softTargetScore() - right.softTargetScore()) > SCORE_EPSILON) {
            return softTargetComparison;
        }
        for (int i = 0; i < left.breaks().size(); i++) {
            int breakComparison = Integer.compare(right.breaks().get(i), left.breaks().get(i));
            if (breakComparison != 0) {
                return breakComparison;
            }
        }
        return 0;
    }

    private record WrappedLayout(List<String> lines, List<Integer> visibleLengths, List<Integer> breaks) {

        private double imbalanceScore() {
            double average = visibleLengths.stream()
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0.0d);
            double score = 0.0d;
            for (int length : visibleLengths) {
                double delta = length - average;
                score += delta * delta;
            }
            return score;
        }

        private double softTargetScore() {
            double score = 0.0d;
            for (int length : visibleLengths) {
                double overflow = Math.max(0, length - SOFT_WRAP_START);
                score += overflow * overflow;
            }
            return score;
        }
    }
}
