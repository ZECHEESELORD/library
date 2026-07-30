package sh.harold.library.menu.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import sh.harold.library.menu.MenuBlock;
import sh.harold.library.menu.MenuChecklistEntry;
import sh.harold.library.menu.MenuClick;
import sh.harold.library.menu.MenuIcon;
import sh.harold.library.menu.MenuInteraction;
import sh.harold.library.menu.MenuItem;
import sh.harold.library.menu.MenuOptionLine;
import sh.harold.library.menu.MenuProgress;
import sh.harold.library.menu.MenuProgressPalette;
import sh.harold.library.menu.MenuSection;
import sh.harold.library.menu.MenuSlot;
import sh.harold.library.menu.MenuTooltipBehavior;

import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HouseMenuCompiler {

    static final int LORE_WIDTH_PIXELS = 240;

    private static final int WRAP_CACHE_LIMIT = 1_024;
    private static final int PROGRESS_BAR_WIDTH = 25;
    private static final Map<WrapCacheKey, List<Component>> WRAP_CACHE =
            new LinkedHashMap<>(WRAP_CACHE_LIMIT, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<WrapCacheKey, List<Component>> eldest) {
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
            Component secondary,
            List<MenuSection> sections,
            List<Component> statusLines,
            boolean glow,
            Map<MenuClick, MenuInteraction> interactions,
            boolean promptSuppressed,
            int amount
    ) {
        CompiledMenuPresentation presentation = compilePresentation(icon, name, secondary, sections, statusLines,
                glow, interactions, promptSuppressed, amount);
        return presentation.toMenuSlot(slot, interactions);
    }

    static CompiledMenuPresentation compilePresentation(MenuItem item) {
        List<Component> lore = new ArrayList<>();
        List<Component> exactLoreLines = item.exactLore().orElse(null);
        int replaceableLoreLineCount = 0;
        if (exactLoreLines != null) {
            appendGroup(lore, exactLoreLines);
            replaceableLoreLineCount = exactLoreLines.size();
        }

        item.secondary().ifPresent(secondary ->
                appendGroup(lore, wrap(fallback(secondary, MUTED_NEUTRAL), Component.empty(), Component.empty())));
        appendSections(item.sections(), lore);
        appendStatus(item.statusLines(), lore);
        appendPrompt(item.interactions(), item.promptSuppressed(), lore);

        int effectiveReplaceableLoreLineCount = item.tooltipBehavior() == MenuTooltipBehavior.LITERAL
                ? replaceableLoreLineCount
                : 0;
        return new CompiledMenuPresentation(item.icon(), item.name(), lore, item.glow(), item.amount(),
                item.tooltipBehavior(), effectiveReplaceableLoreLineCount);
    }

    static CompiledMenuPresentation compilePresentation(
            MenuIcon icon,
            Component name,
            Component secondary,
            List<MenuSection> sections,
            List<Component> statusLines,
            boolean glow,
            Map<MenuClick, MenuInteraction> interactions,
            boolean promptSuppressed,
            int amount
    ) {
        List<Component> lore = new ArrayList<>();
        if (secondary != null) {
            appendGroup(lore, wrap(fallback(secondary, MUTED_NEUTRAL), Component.empty(), Component.empty()));
        }
        appendSections(sections, lore);
        appendStatus(statusLines, lore);
        appendPrompt(interactions, promptSuppressed, lore);
        return new CompiledMenuPresentation(icon, name, lore, glow, amount, MenuTooltipBehavior.CHROME, 0);
    }

    static int footerStart(int rows) {
        return (rows - 1) * 9;
    }

    private static void appendSections(List<MenuSection> sections, List<Component> lore) {
        for (MenuSection section : sections) {
            List<Component> lines = new ArrayList<>();
            for (MenuBlock block : section.blocks()) {
                lines.addAll(renderBlock(block));
            }
            appendGroup(lore, lines);
        }
    }

    private static void appendStatus(List<Component> statusLines, List<Component> lore) {
        if (statusLines.isEmpty()) {
            return;
        }
        List<Component> rendered = new ArrayList<>();
        for (Component line : statusLines) {
            rendered.addAll(wrap(fallback(line, BODY_NEUTRAL), Component.empty(), Component.empty()));
        }
        appendGroup(lore, rendered);
    }

    private static void appendPrompt(
            Map<MenuClick, MenuInteraction> interactions,
            boolean promptSuppressed,
            List<Component> lore
    ) {
        if (interactions.isEmpty() || promptSuppressed) {
            return;
        }
        List<Component> promptLines = new ArrayList<>();
        appendPrompt(promptLines, interactions.get(MenuClick.LEFT), "CLICK");
        appendPrompt(promptLines, interactions.get(MenuClick.SHIFT_LEFT), "SHIFT CLICK");
        appendPrompt(promptLines, interactions.get(MenuClick.RIGHT), "RIGHT CLICK");
        appendPrompt(promptLines, interactions.get(MenuClick.SHIFT_RIGHT), "SHIFT RIGHT CLICK");
        appendGroup(lore, promptLines);
    }

    private static void appendPrompt(List<Component> lines, MenuInteraction interaction, String clickLabel) {
        if (interaction != null) {
            lines.add(promptLine(clickLabel, interaction.promptLabel()));
        }
    }

    private static void appendGroup(List<Component> lore, List<Component> lines) {
        if (lines.isEmpty()) {
            return;
        }
        if (!lore.isEmpty() && !isBlank(lore.getLast())) {
            lore.add(Component.empty());
        }
        lore.addAll(lines);
    }

    private static boolean isBlank(Component component) {
        return ComponentText.flatten(component).isEmpty();
    }

    private static List<Component> renderBlock(MenuBlock block) {
        return switch (block) {
            case MenuBlock.Description description ->
                    wrap(fallback(description.content(), BODY_NEUTRAL), Component.empty(), Component.empty());
            case MenuBlock.Lines lines -> literalLines(lines.lines(), BODY_NEUTRAL);
            case MenuBlock.MutedLines lines -> literalLines(lines.lines(), MUTED_NEUTRAL);
            case MenuBlock.Options options -> renderOptions(options.options(), options.windowSize());
            case MenuBlock.ValueLines valueLines -> renderValueLines(valueLines.lines());
            case MenuBlock.Pairs pairs -> renderPairs(pairs.pairs());
            case MenuBlock.Bullets bullets -> renderBullets(bullets.bullets());
            case MenuBlock.Checklist checklist -> renderChecklist(checklist.entries());
            case MenuBlock.Progress progress -> renderProgress(progress.progress());
        };
    }

    private static List<Component> literalLines(List<Component> lines, TextColor defaultColor) {
        return lines.stream().map(line -> fallback(line, defaultColor)).toList();
    }

    private static List<Component> renderOptions(List<MenuOptionLine> options, int windowSize) {
        List<MenuOptionLine> visible = visibleOptions(options, windowSize);
        List<Component> rendered = new ArrayList<>();
        for (MenuOptionLine option : visible) {
            TextColor lineColor = option.selected() ? option.color() : muted(option.color());
            Component prefix = text(option.selected() ? "→ " : "   ", lineColor, option.selected(), false);
            Component indent = indentFor(prefix, lineColor);
            rendered.addAll(wrap(fallback(option.label(), lineColor), prefix, indent));
        }
        return rendered;
    }

    private static List<Component> renderValueLines(List<MenuBlock.ValueLines.Entry> lines) {
        List<Component> rendered = new ArrayList<>();
        for (MenuBlock.ValueLines.Entry line : lines) {
            Component prefix = fallback(line.prefix(), BODY_NEUTRAL);
            rendered.addAll(wrap(fallback(line.value(), BODY_NEUTRAL), prefix, indentFor(prefix, BODY_NEUTRAL)));
        }
        return rendered;
    }

    private static List<Component> renderPairs(List<MenuBlock.Pairs.Entry> pairs) {
        List<Component> rendered = new ArrayList<>();
        for (MenuBlock.Pairs.Entry pair : pairs) {
            Component prefix = Component.text()
                    .append(fallback(pair.key(), BODY_NEUTRAL))
                    .append(text(": ", BODY_NEUTRAL))
                    .decoration(TextDecoration.ITALIC, false)
                    .build();
            rendered.addAll(wrap(fallback(pair.value(), STRONG_NEUTRAL), prefix,
                    indentFor(prefix, BODY_NEUTRAL)));
        }
        return rendered;
    }

    private static List<Component> renderBullets(List<Component> bullets) {
        List<Component> rendered = new ArrayList<>();
        Component prefix = text("• ", BODY_NEUTRAL);
        Component indent = indentFor(prefix, BODY_NEUTRAL);
        for (Component bullet : bullets) {
            rendered.addAll(wrap(fallback(bullet, BODY_NEUTRAL), prefix, indent));
        }
        return rendered;
    }

    private static List<Component> renderChecklist(List<MenuChecklistEntry> entries) {
        List<Component> rendered = new ArrayList<>();
        for (MenuChecklistEntry entry : entries) {
            TextColor markerColor = entry.complete() ? NamedTextColor.GREEN : NamedTextColor.RED;
            Component prefix = text(entry.complete() ? " ✔ " : " ✖ ", markerColor);
            rendered.addAll(wrap(fallback(entry.label(), BODY_NEUTRAL), prefix,
                    indentFor(prefix, BODY_NEUTRAL)));
        }
        return rendered;
    }

    private static List<Component> renderProgress(MenuProgress progress) {
        BigDecimal ratio = progress.current().divide(progress.max(), 10, RoundingMode.HALF_UP);
        int filled = ratio.multiply(BigDecimal.valueOf(PROGRESS_BAR_WIDTH))
                .setScale(0, RoundingMode.CEILING)
                .intValue();
        filled = Math.max(0, Math.min(PROGRESS_BAR_WIDTH, filled));
        int empty = PROGRESS_BAR_WIDTH - filled;

        MenuProgressPalette palette = progress.palette();
        Component prefix = Component.text()
                .append(fallback(progress.label(), BODY_NEUTRAL))
                .append(text(": ", BODY_NEUTRAL))
                .decoration(TextDecoration.ITALIC, false)
                .build();
        Component percent = text(HouseNumberFormatter.formatPercent(ratio) + "%", palette.percent());
        List<Component> labelLines = wrap(percent, prefix, indentFor(prefix, BODY_NEUTRAL));

        Component barLine = Component.text()
                .append(text(" ".repeat(filled), palette.filled(), true, true))
                .append(text(" ".repeat(empty), NamedTextColor.WHITE, true, true))
                .append(Component.space())
                .append(text(HouseNumberFormatter.format(progress.current()), palette.value()))
                .append(text("/", palette.separator()))
                .append(text(HouseNumberFormatter.format(progress.max()), palette.value()))
                .append(progress.unit() == null ? Component.empty() : text(" " + progress.unit(), BODY_NEUTRAL))
                .decoration(TextDecoration.ITALIC, false)
                .build();

        List<Component> rendered = new ArrayList<>(labelLines.size() + 1);
        rendered.addAll(labelLines);
        rendered.add(barLine);
        return rendered;
    }

    private static Component promptLine(String clickLabel, String promptLabel) {
        return Component.text()
                .append(text(clickLabel, NamedTextColor.YELLOW, true, false))
                .append(text(" to " + emphaticPromptLabel(promptLabel), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false)
                .build();
    }

    private static String emphaticPromptLabel(String promptLabel) {
        return promptLabel.endsWith("!") ? promptLabel : promptLabel + "!";
    }

    private static Component fallback(Component component, TextColor color) {
        return Component.text()
                .color(color)
                .decoration(TextDecoration.ITALIC, false)
                .append(component)
                .build();
    }

    private static Component indentFor(Component prefix, TextColor color) {
        int spaces = Math.max(0, Math.round((MinecraftFontMetrics.width(prefix) + 1) / 5.0f));
        return text(" ".repeat(spaces), color);
    }

    private static Component text(String content, TextColor color) {
        return text(content, color, false, false);
    }

    private static Component text(String content, TextColor color, boolean bold, boolean strikethrough) {
        return Component.text(content, color)
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

    private static List<Component> wrap(Component source, Component firstPrefix, Component continuationPrefix) {
        NormalizedText normalized = normalize(source);
        if (normalized.words().isEmpty()) {
            return List.of();
        }

        WrapCacheKey key = new WrapCacheKey(
                normalized.resolved(),
                resolve(firstPrefix),
                resolve(continuationPrefix),
                LORE_WIDTH_PIXELS);
        synchronized (WRAP_CACHE) {
            List<Component> cached = WRAP_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }

        List<Component> wrapped = greedyWrap(normalized.words(), firstPrefix, continuationPrefix);
        synchronized (WRAP_CACHE) {
            List<Component> existing = WRAP_CACHE.get(key);
            if (existing != null) {
                return existing;
            }
            WRAP_CACHE.put(key, wrapped);
        }
        return wrapped;
    }

    private static List<Component> greedyWrap(
            List<StyledWord> words,
            Component firstPrefix,
            Component continuationPrefix
    ) {
        List<Component> lines = new ArrayList<>();
        List<StyledWord> current = new ArrayList<>();
        Component prefix = firstPrefix;

        for (StyledWord word : words) {
            List<StyledWord> candidate = new ArrayList<>(current);
            candidate.add(word);
            Component candidateLine = line(prefix, candidate);
            if (!current.isEmpty() && MinecraftFontMetrics.width(candidateLine) > LORE_WIDTH_PIXELS) {
                lines.add(line(prefix, current));
                current = new ArrayList<>();
                prefix = continuationPrefix;
            }
            current.add(word);
        }
        if (!current.isEmpty()) {
            lines.add(line(prefix, current));
        }
        return List.copyOf(lines);
    }

    private static Component line(Component prefix, List<StyledWord> words) {
        TextComponent.Builder builder = Component.text().decoration(TextDecoration.ITALIC, false);
        if (!isBlank(prefix)) {
            builder.append(prefix);
        }
        for (int index = 0; index < words.size(); index++) {
            if (index > 0) {
                builder.append(Component.space());
            }
            builder.append(words.get(index).component());
        }
        return builder.build();
    }

    private static NormalizedText normalize(Component source) {
        List<StyledRun> runs = new ArrayList<>();
        flatten(source, Style.empty(), runs);

        List<StyledWord> words = new ArrayList<>();
        List<StyledRun> current = new ArrayList<>();
        for (StyledRun run : runs) {
            StringBuilder segment = new StringBuilder();
            int[] codePoints = run.text().codePoints().toArray();
            for (int codePoint : codePoints) {
                if (Character.isWhitespace(codePoint)) {
                    flushSegment(current, segment, run.style());
                    flushWord(words, current);
                } else {
                    segment.appendCodePoint(codePoint);
                }
            }
            flushSegment(current, segment, run.style());
        }
        flushWord(words, current);

        TextComponent.Builder resolved = Component.text();
        for (int index = 0; index < words.size(); index++) {
            if (index > 0) {
                resolved.append(Component.space());
            }
            resolved.append(words.get(index).component());
        }
        return new NormalizedText(List.copyOf(words), resolved.build());
    }

    private static Component resolve(Component source) {
        List<StyledRun> runs = new ArrayList<>();
        flatten(source, Style.empty(), runs);
        TextComponent.Builder builder = Component.text();
        for (StyledRun run : runs) {
            builder.append(Component.text(run.text(), run.style()));
        }
        return builder.build();
    }

    private static void flatten(Component component, Style inherited, List<StyledRun> runs) {
        Style resolved = inherited.merge(component.style(), Style.Merge.Strategy.ALWAYS);
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            appendRun(runs, text.content(), resolved);
        }
        for (Component child : component.children()) {
            flatten(child, resolved, runs);
        }
    }

    private static void appendRun(List<StyledRun> runs, String content, Style style) {
        if (content.isEmpty()) {
            return;
        }
        if (!runs.isEmpty() && runs.getLast().style().equals(style)) {
            StyledRun previous = runs.removeLast();
            runs.add(new StyledRun(previous.text() + content, style));
        } else {
            runs.add(new StyledRun(content, style));
        }
    }

    private static void flushSegment(List<StyledRun> word, StringBuilder segment, Style style) {
        if (segment.isEmpty()) {
            return;
        }
        String content = segment.toString();
        segment.setLength(0);
        if (!word.isEmpty() && word.getLast().style().equals(style)) {
            StyledRun previous = word.removeLast();
            word.add(new StyledRun(previous.text() + content, style));
        } else {
            word.add(new StyledRun(content, style));
        }
    }

    private static void flushWord(List<StyledWord> words, List<StyledRun> current) {
        if (current.isEmpty()) {
            return;
        }
        words.add(new StyledWord(List.copyOf(current)));
        current.clear();
    }

    private record StyledRun(String text, Style style) {
    }

    private record StyledWord(List<StyledRun> runs) {

        private Component component() {
            TextComponent.Builder builder = Component.text();
            for (StyledRun run : runs) {
                builder.append(Component.text(run.text(), run.style()));
            }
            return builder.build();
        }
    }

    private record NormalizedText(List<StyledWord> words, Component resolved) {
    }

    private record WrapCacheKey(
            Component resolvedSource,
            Component resolvedFirstPrefix,
            Component resolvedContinuationPrefix,
            int width
    ) {
    }
}
