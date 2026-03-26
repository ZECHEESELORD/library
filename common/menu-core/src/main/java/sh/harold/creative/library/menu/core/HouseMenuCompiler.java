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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class HouseMenuCompiler {

    private static final int DEFAULT_WRAP_WIDTH = 148;
    private static final int MIN_WRAP_WIDTH = 120;
    private static final int MAX_WRAP_WIDTH = 176;
    private static final int SOFT_WRAP_BUFFER = 10;
    private static final int PROGRESS_BAR_WIDTH = 20;

    private static final TextColor STRONG_NEUTRAL = NamedTextColor.WHITE;
    private static final TextColor BODY_NEUTRAL = NamedTextColor.GRAY;
    private static final TextColor MUTED_NEUTRAL = NamedTextColor.DARK_GRAY;

    private HouseMenuCompiler() {
    }

    static MenuSlot compile(int slot, MenuItem item) {
        int wrapWidth = resolveWrapWidth(item);
        List<Component> lore = new ArrayList<>();
        appendSecondary(item, lore, wrapWidth);
        appendBlocks(item, lore, wrapWidth);
        appendPrompt(item, lore);
        return new MenuSlot(slot, item.icon(), item.name(), lore, item.glow(), interactions(item));
    }

    static int footerStart(int rows) {
        return (rows - 1) * 9;
    }

    private static void appendSecondary(MenuItem item, List<Component> lore, int wrapWidth) {
        item.secondary().ifPresent(secondary -> wrapText(secondary, wrapWidth, 0)
                .forEach(line -> lore.add(text(line, MUTED_NEUTRAL))));
    }

    private static void appendBlocks(MenuItem item, List<Component> lore, int wrapWidth) {
        for (MenuBlock block : item.blocks()) {
            List<Component> blockLines = renderBlock(block, wrapWidth);
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
        if (button.interactions().size() == 1 && button.interactions().containsKey(MenuClick.LEFT)) {
            promptLines.add(promptLine("CLICK", button.interactions().get(MenuClick.LEFT).promptLabel()));
        } else {
            MenuInteraction left = button.interactions().get(MenuClick.LEFT);
            if (left != null) {
                promptLines.add(promptLine("LEFT CLICK", left.promptLabel()));
            }
            MenuInteraction right = button.interactions().get(MenuClick.RIGHT);
            if (right != null) {
                promptLines.add(promptLine("RIGHT CLICK", right.promptLabel()));
            }
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

    private static List<Component> renderBlock(MenuBlock block, int wrapWidth) {
        return switch (block) {
            case MenuBlock.Description description -> wrapText(description.text(), wrapWidth, 0).stream()
                    .map(line -> text(line, BODY_NEUTRAL))
                    .toList();
            case MenuBlock.Lines lines -> renderLines(lines.lines(), lines.wrapMode(), wrapWidth);
            case MenuBlock.Pairs pairs -> renderPairs(pairs.pairs(), pairs.wrapMode(), wrapWidth);
            case MenuBlock.Bullets bullets -> renderBullets(bullets.bullets(), wrapWidth);
            case MenuBlock.Progress progress -> renderProgress(progress);
        };
    }

    private static List<Component> renderLines(List<String> lines, MenuBlock.WrapMode wrapMode, int wrapWidth) {
        List<Component> rendered = new ArrayList<>();
        for (String line : lines) {
            List<String> wrapped = wrapMode == MenuBlock.WrapMode.SOFT ? softWrap(line, wrapWidth, 0) : List.of(line);
            for (String wrappedLine : wrapped) {
                rendered.add(text(wrappedLine, BODY_NEUTRAL));
            }
        }
        return rendered;
    }

    private static List<Component> renderPairs(List<MenuBlock.Pairs.Entry> pairs, MenuBlock.WrapMode wrapMode, int wrapWidth) {
        List<Component> rendered = new ArrayList<>();
        for (MenuBlock.Pairs.Entry pair : pairs) {
            String prefix = pair.key() + ": ";
            if (wrapMode == MenuBlock.WrapMode.SOFT) {
                List<String> wrapped = softWrap(prefix + pair.value(), wrapWidth, MinecraftFontMetrics.width(prefix));
                String indent = " ".repeat(prefix.length());
                for (int i = 0; i < wrapped.size(); i++) {
                    String line = wrapped.get(i);
                    if (i == 0) {
                        rendered.add(pairLine(pair.key(), line.substring(prefix.length())));
                    } else {
                        rendered.add(text(indent + line, STRONG_NEUTRAL));
                    }
                }
            } else {
                rendered.add(pairLine(pair.key(), pair.value()));
            }
        }
        return rendered;
    }

    private static List<Component> renderBullets(List<String> bullets, int wrapWidth) {
        List<Component> rendered = new ArrayList<>();
        for (String bullet : bullets) {
            List<String> wrapped = softWrap(bullet, wrapWidth - MinecraftFontMetrics.width("• "), MinecraftFontMetrics.width("  "));
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
                .append(text(filledBar, accent.light()))
                .append(text(emptyBar, BODY_NEUTRAL))
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

    private static Component promptLine(String clickLabel, String promptLabel) {
        return Component.text()
                .append(text(clickLabel, NamedTextColor.YELLOW, true))
                .append(text(" to " + promptLabel, NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false)
                .build();
    }

    private static Component text(String text, TextColor color) {
        return text(text, color, false);
    }

    private static Component text(String text, TextColor color, boolean bold) {
        return Component.text(text, color)
                .decoration(TextDecoration.BOLD, bold)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static int resolveWrapWidth(MenuItem item) {
        int best = 0;
        best = Math.max(best, MinecraftFontMetrics.width(item.name()));
        for (MenuBlock block : item.blocks()) {
            switch (block) {
                case MenuBlock.Lines lines when lines.wrapMode() == MenuBlock.WrapMode.SINGLE_LINE -> {
                    for (String line : lines.lines()) {
                        best = Math.max(best, MinecraftFontMetrics.width(line));
                    }
                }
                case MenuBlock.Pairs pairs when pairs.wrapMode() == MenuBlock.WrapMode.SINGLE_LINE -> {
                    for (MenuBlock.Pairs.Entry pair : pairs.pairs()) {
                        best = Math.max(best, MinecraftFontMetrics.width(pair.key() + ": " + pair.value()));
                    }
                }
                default -> {
                }
            }
        }
        int clamped = best == 0 ? DEFAULT_WRAP_WIDTH : Math.max(MIN_WRAP_WIDTH, Math.min(MAX_WRAP_WIDTH, best));
        return ((clamped + 3) / 8) * 8;
    }

    private static List<String> softWrap(String text, int wrapWidth, int continuationIndentWidth) {
        int width = MinecraftFontMetrics.width(text);
        if (width <= wrapWidth + SOFT_WRAP_BUFFER) {
            return List.of(text);
        }
        return wrapText(text, wrapWidth, continuationIndentWidth);
    }

    private static List<String> wrapText(String text, int wrapWidth, int continuationIndentWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.trim().split("\\s+");
        StringBuilder current = new StringBuilder();
        int currentWidth = 0;
        boolean continuation = false;
        for (String word : words) {
            int wordWidth = MinecraftFontMetrics.width(word);
            int spacerWidth = current.isEmpty() ? 0 : MinecraftFontMetrics.width(" ");
            int budget = continuation ? wrapWidth - continuationIndentWidth : wrapWidth;
            if (!current.isEmpty() && currentWidth + spacerWidth + wordWidth > budget) {
                lines.add(current.toString());
                current = new StringBuilder();
                currentWidth = 0;
                continuation = true;
                budget = wrapWidth - continuationIndentWidth;
            }
            if (!current.isEmpty()) {
                current.append(' ');
                currentWidth += spacerWidth;
            }
            current.append(word);
            currentWidth += wordWidth;
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }
}
