package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import sh.harold.creative.library.menu.AccentFamily;
import sh.harold.creative.library.menu.MenuBlock;
import sh.harold.creative.library.menu.MenuClick;
import sh.harold.creative.library.menu.MenuInteraction;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuOptionLine;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.MenuTooltipBehavior;
import sh.harold.creative.library.ui.value.UiValue;

import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HouseMenuCompiler {

    private static final WrapProfile DEFAULT_WRAP_PROFILE = new WrapProfile(20, 30);
    private static final WrapProfile BULLET_WRAP_PROFILE = new WrapProfile(30, 50);
    private static final int WRAP_CACHE_LIMIT = 1_024;
    private static final int PROGRESS_BAR_WIDTH = 20;
    private static final Map<WrapCacheKey, List<String>> WRAP_CACHE =
            new LinkedHashMap<>(WRAP_CACHE_LIMIT, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<WrapCacheKey, List<String>> eldest) {
                    return size() > WRAP_CACHE_LIMIT;
                }
            };

    private static final TextColor STRONG_NEUTRAL = NamedTextColor.WHITE;
    private static final TextColor BODY_NEUTRAL = NamedTextColor.GRAY;
    private static final TextColor MUTED_NEUTRAL = NamedTextColor.DARK_GRAY;

    private HouseMenuCompiler() {
    }

    public static MenuSlot compile(int slot, MenuItem item) {
        CompiledMenuPresentation presentation = compilePresentation(item);
        return presentation.toMenuSlot(slot, item.interactions());
    }

    public static MenuSlot compile(
            int slot,
            MenuIcon icon,
            Component name,
            String secondary,
            List<MenuBlock> blocks,
            boolean glow,
            Map<MenuClick, MenuInteraction> interactions,
            boolean promptSuppressed,
            int amount
    ) {
        CompiledMenuPresentation presentation = compilePresentation(icon, name, secondary, blocks, glow,
                interactions, promptSuppressed, amount);
        return presentation.toMenuSlot(slot, interactions);
    }

    static CompiledMenuPresentation compilePresentation(MenuItem item) {
        List<Component> lore = new ArrayList<>();
        List<Component> exactLoreLines = item.exactLore().orElse(null);
        boolean exactLore = exactLoreLines != null;
        int replaceableLoreLineCount = 0;
        if (exactLore) {
            lore.addAll(exactLoreLines);
            replaceableLoreLineCount = exactLoreLines.size();
        }
        appendSecondary(item.secondary().orElse(null), lore);
        appendBlocks(item.blocks(), lore, item.secondary().isPresent(), exactLore && item.secondary().isEmpty());
        appendPrompt(item.interactions(), item.promptSuppressed(), lore, exactLore);
        int effectiveReplaceableLoreLineCount = item.tooltipBehavior() == MenuTooltipBehavior.LITERAL
                ? replaceableLoreLineCount
                : 0;
        return new CompiledMenuPresentation(item.icon(), item.name(), lore, item.glow(), item.amount(),
                item.tooltipBehavior(), effectiveReplaceableLoreLineCount);
    }

    static CompiledMenuPresentation compilePresentation(
            MenuIcon icon,
            Component name,
            String secondary,
            List<MenuBlock> blocks,
            boolean glow,
            Map<MenuClick, MenuInteraction> interactions,
            boolean promptSuppressed,
            int amount
    ) {
        List<Component> lore = new ArrayList<>();
        appendSecondary(secondary, lore);
        appendBlocks(blocks, lore, secondary != null, false);
        appendPrompt(interactions, promptSuppressed, lore, false);
        return new CompiledMenuPresentation(icon, name, lore, glow, amount, MenuTooltipBehavior.CHROME, 0);
    }

    static int footerStart(int rows) {
        return (rows - 1) * 9;
    }

    private static void appendSecondary(String secondary, List<Component> lore) {
        if (secondary == null) {
            return;
        }
        wrapText(secondary, 0, 0).forEach(line -> lore.add(text(line, MUTED_NEUTRAL)));
    }

    private static void appendBlocks(List<MenuBlock> blocks, List<Component> lore, boolean hasSecondary, boolean forceInitialSpacer) {
        boolean renderedBlock = false;
        for (MenuBlock block : blocks) {
            List<Component> blockLines = renderBlock(block);
            if (blockLines.isEmpty()) {
                continue;
            }
            boolean needsSpacer = renderedBlock
                    || forceInitialSpacer
                    || (hasSecondary && !renderedBlock)
                    || (!hasSecondary && lore.isEmpty() && block instanceof MenuBlock.Description);
            if (needsSpacer) {
                lore.add(Component.empty());
            }
            lore.addAll(blockLines);
            renderedBlock = true;
        }
    }

    private static void appendPrompt(
            Map<MenuClick, MenuInteraction> interactions,
            boolean promptSuppressed,
            List<Component> lore,
            boolean separateFromPreservedPresentation
    ) {
        if (interactions.isEmpty() || promptSuppressed) {
            return;
        }
        List<Component> promptLines = new ArrayList<>();
        MenuInteraction left = interactions.get(MenuClick.LEFT);
        if (left != null) {
            promptLines.add(promptLine("CLICK", left.promptLabel(), NamedTextColor.YELLOW));
        }
        MenuInteraction right = interactions.get(MenuClick.RIGHT);
        if (right != null) {
            promptLines.add(promptLine("RIGHT CLICK", right.promptLabel(), NamedTextColor.AQUA));
        }
        if (!promptLines.isEmpty()) {
            if (!lore.isEmpty() || separateFromPreservedPresentation) {
                lore.add(Component.empty());
            }
            lore.addAll(promptLines);
        }
    }

    private static List<Component> renderBlock(MenuBlock block) {
        return switch (block) {
            case MenuBlock.Description description -> wrapText(description.text(), 0, 0).stream()
                    .map(line -> text(line, BODY_NEUTRAL))
                    .toList();
            case MenuBlock.Lines lines -> renderLines(lines.lines(), lines.wrapMode());
            case MenuBlock.MutedLines lines -> renderMutedLines(lines.lines());
            case MenuBlock.Options options -> renderOptions(options.options(), options.windowSize());
            case MenuBlock.ValueLines valueLines -> renderValueLines(valueLines.lines(), valueLines.wrapMode());
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

    private static List<Component> renderMutedLines(List<String> lines) {
        List<Component> rendered = new ArrayList<>();
        for (String line : lines) {
            wrapText(line, 0, 0).forEach(wrappedLine -> rendered.add(text(wrappedLine, MUTED_NEUTRAL)));
        }
        return rendered;
    }

    private static List<Component> renderOptions(List<MenuOptionLine> options, int windowSize) {
        List<MenuOptionLine> visible = visibleOptions(options, windowSize);
        List<Component> rendered = new ArrayList<>(visible.size());
        for (MenuOptionLine option : visible) {
            TextColor lineColor = option.selected() ? option.color() : muted(option.color());
            String prefix = option.selected() ? "→ " : "   ";
            Component prefixComponent = text(prefix, lineColor, option.selected());
            Component labelComponent = text(option.label(), lineColor);
            rendered.add(Component.text()
                    .append(prefixComponent)
                    .append(labelComponent)
                    .decoration(TextDecoration.ITALIC, false)
                    .build());
        }
        return rendered;
    }

    private static List<Component> renderValueLines(List<MenuBlock.ValueLines.Entry> lines, MenuBlock.WrapMode wrapMode) {
        List<Component> rendered = new ArrayList<>();
        boolean canWrap = wrapMode == MenuBlock.WrapMode.SOFT && lines.size() == 1;
        for (MenuBlock.ValueLines.Entry line : lines) {
            if (canWrap) {
                List<String> wrapped = softWrap(line.value().text(), line.prefix().length(), line.prefix().length());
                String indent = " ".repeat(line.prefix().length());
                for (int i = 0; i < wrapped.size(); i++) {
                    if (i == 0) {
                        rendered.add(prefixedValueLine(line.prefix(), wrapped.get(i), line.value(), BODY_NEUTRAL));
                    } else {
                        rendered.add(valueText(indent + wrapped.get(i), line.value(), BODY_NEUTRAL));
                    }
                }
            } else {
                rendered.add(prefixedValueLine(line.prefix(), line.value().text(), line.value(), BODY_NEUTRAL));
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
                List<String> wrapped = softWrap(pair.value().text(), prefix.length(), prefix.length());
                String indent = " ".repeat(prefix.length());
                for (int i = 0; i < wrapped.size(); i++) {
                    if (i == 0) {
                        rendered.add(pairLine(pair.key(), wrapped.get(i), pair.value()));
                    } else {
                        rendered.add(valueText(indent + wrapped.get(i), pair.value(), STRONG_NEUTRAL));
                    }
                }
            } else {
                rendered.add(pairLine(pair.key(), pair.value().text(), pair.value()));
            }
        }
        return rendered;
    }

    private static List<Component> renderBullets(List<String> bullets) {
        List<Component> rendered = new ArrayList<>();
        for (String bullet : bullets) {
            List<String> wrapped = wrapText(bullet, 2, 2, BULLET_WRAP_PROFILE);
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

    private static Component pairLine(String key, String value, UiValue styledValue) {
        return prefixedValueLine(key + ": ", value, styledValue, STRONG_NEUTRAL);
    }

    private static Component prefixedValueLine(String prefix, String value, UiValue styledValue, TextColor defaultValueColor) {
        return Component.text()
                .append(text(prefix, BODY_NEUTRAL))
                .append(valueText(value, styledValue, defaultValueColor))
                .decoration(TextDecoration.ITALIC, false)
                .build();
    }

    private static Component valueText(String text, UiValue value, TextColor defaultColor) {
        TextColor color = value.colorOverride() == null ? defaultColor : value.colorOverride();
        return text(text, color);
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

    private static List<MenuOptionLine> visibleOptions(List<MenuOptionLine> options, int windowSize) {
        if (windowSize <= 0 || windowSize >= options.size()) {
            return options;
        }
        int selectedIndex = selectedIndex(options);
        int halfWindow = windowSize / 2;
        int start = Math.max(0, selectedIndex - halfWindow);
        int end = Math.min(options.size(), start + windowSize);
        start = Math.max(0, end - windowSize);
        return options.subList(start, end);
    }

    private static int selectedIndex(List<MenuOptionLine> options) {
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).selected()) {
                return index;
            }
        }
        return 0;
    }

    private static TextColor muted(TextColor color) {
        float[] hsb = Color.RGBtoHSB(color.red(), color.green(), color.blue(), null);
        float saturation = Math.max(0.08f, hsb[1] * 0.18f);
        float brightness = Math.min(1.0f, 0.38f + (hsb[2] * 0.42f));
        return TextColor.color(Color.HSBtoRGB(hsb[0], saturation, brightness) & 0xFFFFFF);
    }

    private static List<String> softWrap(String text, int firstIndentChars, int continuationIndentChars) {
        return wrapText(text, firstIndentChars, continuationIndentChars, DEFAULT_WRAP_PROFILE);
    }

    private static List<String> wrapText(String text, int firstIndentChars, int continuationIndentChars) {
        return wrapText(text, firstIndentChars, continuationIndentChars, DEFAULT_WRAP_PROFILE);
    }

    private static List<String> wrapText(String text, int firstIndentChars, int continuationIndentChars, WrapProfile profile) {
        String normalized = normalize(text);
        WrapCacheKey cacheKey = new WrapCacheKey(normalized, firstIndentChars, continuationIndentChars, profile);
        synchronized (WRAP_CACHE) {
            List<String> cached = WRAP_CACHE.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        List<String> wrapped = computeWrappedText(normalized, firstIndentChars, continuationIndentChars, profile);
        synchronized (WRAP_CACHE) {
            List<String> cached = WRAP_CACHE.get(cacheKey);
            if (cached != null) {
                return cached;
            }
            WRAP_CACHE.put(cacheKey, wrapped);
        }
        return wrapped;
    }

    private static List<String> computeWrappedText(String normalized, int firstIndentChars, int continuationIndentChars, WrapProfile profile) {
        if (normalized.length() + firstIndentChars <= profile.hardWrapLimit()) {
            return List.of(normalized);
        }
        String[] words = normalized.split(" ");
        WrappedLayout best = bestLayout(words, 0, firstIndentChars, continuationIndentChars, false, profile, new HashMap<>());
        return best == null || best.lines().isEmpty() ? List.of(normalized) : best.lines();
    }

    private static String normalize(String text) {
        return text.trim().replaceAll("\\s+", " ");
    }

    private static WrappedLayout bestLayout(
            String[] words,
            int start,
            int firstIndentChars,
            int continuationIndentChars,
            boolean continuation,
            WrapProfile profile,
            Map<WrapState, WrappedLayout> memo
    ) {
        WrapState state = new WrapState(start, continuation);
        WrappedLayout cached = memo.get(state);
        if (cached != null) {
            return cached;
        }
        int indentChars = continuation ? continuationIndentChars : firstIndentChars;
        WrappedLayout best = null;
        for (int end : candidateEnds(words, start, indentChars, profile)) {
            String line = joinWords(words, start, end);
            int visibleLength = indentChars + line.length();
            WrappedLayout suffix;
            if (end == words.length - 1) {
                suffix = WrappedLayout.empty();
            } else {
                suffix = bestLayout(words, end + 1, firstIndentChars, continuationIndentChars, true, profile, memo);
            }
            WrappedLayout candidate = suffix.prepend(line, end, visibleLength, profile.softWrapStart());
            if (best == null || compareLayouts(candidate, best) < 0) {
                best = candidate;
            }
        }
        memo.put(state, best);
        return best;
    }

    private static List<Integer> candidateEnds(String[] words, int start, int indentChars, WrapProfile profile) {
        List<Integer> ends = new ArrayList<>();
        int budget = Math.max(1, profile.hardWrapLimit() - indentChars);
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
        int lineCountComparison = Integer.compare(left.lineCount(), right.lineCount());
        if (lineCountComparison != 0) {
            return lineCountComparison;
        }
        int scoreComparison = Long.compare(left.sumSquares(), right.sumSquares());
        if (scoreComparison != 0) {
            return scoreComparison;
        }
        int softTargetComparison = Long.compare(left.softTargetScore(), right.softTargetScore());
        if (softTargetComparison != 0) {
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

    private record WrapProfile(int softWrapStart, int hardWrapLimit) {
    }

    private record WrapCacheKey(String text, int firstIndentChars, int continuationIndentChars, WrapProfile profile) {
    }

    private record WrapState(int start, boolean continuation) {
    }

    private record WrappedLayout(List<String> lines, List<Integer> breaks, int lineCount, long sumSquares, long softTargetScore) {

        private static WrappedLayout empty() {
            return new WrappedLayout(List.of(), List.of(), 0, 0L, 0L);
        }

        private WrappedLayout prepend(String line, int end, int visibleLength, int softWrapStart) {
            List<String> nextLines = new ArrayList<>(lines.size() + 1);
            nextLines.add(line);
            nextLines.addAll(lines);

            List<Integer> nextBreaks = new ArrayList<>(breaks.size() + 1);
            nextBreaks.add(end);
            nextBreaks.addAll(breaks);

            long overflow = Math.max(0, visibleLength - softWrapStart);
            long nextSumSquares = sumSquares + (long) visibleLength * visibleLength;
            long nextSoftTargetScore = softTargetScore + overflow * overflow;
            return new WrappedLayout(List.copyOf(nextLines), List.copyOf(nextBreaks),
                    lineCount + 1, nextSumSquares, nextSoftTargetScore);
        }
    }
}
