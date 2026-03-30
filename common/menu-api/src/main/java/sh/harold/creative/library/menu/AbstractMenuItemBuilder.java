package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import sh.harold.creative.library.ui.value.UiValue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

abstract class AbstractMenuItemBuilder<B extends AbstractMenuItemBuilder<B>> {

    private final MenuIcon icon;
    private final List<MenuBlock> blocks = new ArrayList<>();
    private Component name;
    private String secondary;
    private boolean glow;

    AbstractMenuItemBuilder(MenuIcon icon) {
        this.icon = Objects.requireNonNull(icon, "icon");
    }

    public B name(String name) {
        return name(Component.text(Objects.requireNonNull(name, "name")));
    }

    public B name(ComponentLike name) {
        this.name = Objects.requireNonNull(name, "name").asComponent().decoration(TextDecoration.ITALIC, false);
        return self();
    }

    /**
     * Adds the short subtitle/state line directly under the title.
     * The shared compiler rebalances it into the fewest reasonably even lore lines under the
     * standard character-count cap, and ignores the item title when placing breaks.
     */
    public B secondary(String secondary) {
        this.secondary = requireText(secondary, "secondary");
        return self();
    }

    /**
     * Adds the main wrapped paragraph block.
     * The shared compiler uses balanced character-count wrapping and ignores the item title when
     * placing description breaks.
     */
    public B description(String description) {
        blocks.add(new MenuBlock.Description(requireText(description, "description")));
        return self();
    }

    public B line(String line) {
        return lines(MenuBlock.WrapMode.SINGLE_LINE, List.of(requireText(line, "line")));
    }

    /**
     * Adds one long line that may rebalance into wrapped lore when it no longer fits cleanly
     * within the shared character-count cap.
     */
    public B softLine(String line) {
        return lines(MenuBlock.WrapMode.SOFT, List.of(requireText(line, "line")));
    }

    public B lines(String... lines) {
        return lines(List.of(lines));
    }

    /**
     * Adds a list-style line block that only wraps when it contains a single long entry.
     */
    public B softLines(String... lines) {
        return softLines(List.of(lines));
    }

    public B lines(Iterable<String> lines) {
        return lines(MenuBlock.WrapMode.SINGLE_LINE, copyText(lines, "lines"));
    }

    /**
     * Adds a list-style line block.
     * Multi-entry line blocks stay one entry per lore line even when authored through a soft variant.
     */
    public B softLines(Iterable<String> lines) {
        return lines(MenuBlock.WrapMode.SOFT, copyText(lines, "lines"));
    }

    public B valueLine(String prefix, Object value) {
        return valueLines(MenuBlock.WrapMode.SINGLE_LINE, List.of(new MenuBlock.ValueLines.Entry(
                requireText(prefix, "prefix"),
                toUiValue(value, "value"))));
    }

    public B softValueLine(String prefix, Object value) {
        return valueLines(MenuBlock.WrapMode.SOFT, List.of(new MenuBlock.ValueLines.Entry(
                requireText(prefix, "prefix"),
                toUiValue(value, "value"))));
    }

    public B valueLines(MenuValueLine... lines) {
        return valueLines(List.of(lines));
    }

    public B softValueLines(MenuValueLine... lines) {
        return softValueLines(List.of(lines));
    }

    public B valueLines(Iterable<MenuValueLine> lines) {
        return valueLines(MenuBlock.WrapMode.SINGLE_LINE, copyValueLines(lines));
    }

    public B softValueLines(Iterable<MenuValueLine> lines) {
        return valueLines(MenuBlock.WrapMode.SOFT, copyValueLines(lines));
    }

    public B pair(String key, Object value) {
        return pairs(MenuBlock.WrapMode.SINGLE_LINE, List.of(new MenuBlock.Pairs.Entry(requireText(key, "key"), toUiValue(value, "value"))));
    }

    /**
     * Adds one long key/value fact that may rebalance into wrapped lore when it no longer fits
     * cleanly within the shared character-count cap.
     */
    public B softPair(String key, Object value) {
        return pairs(MenuBlock.WrapMode.SOFT, List.of(new MenuBlock.Pairs.Entry(requireText(key, "key"), toUiValue(value, "value"))));
    }

    public B pairs(String... rawPairs) {
        return pairs(MenuBlock.WrapMode.SINGLE_LINE, rawPairs);
    }

    public B pairs(MenuPair... pairs) {
        return pairs(List.of(pairs));
    }

    public B softPairs(MenuPair... pairs) {
        return softPairs(List.of(pairs));
    }

    public B pairs(Iterable<MenuPair> pairs) {
        return pairs(MenuBlock.WrapMode.SINGLE_LINE, copyPairs(pairs));
    }

    public B softPairs(Iterable<MenuPair> pairs) {
        return pairs(MenuBlock.WrapMode.SOFT, copyPairs(pairs));
    }

    /**
     * Adds a grouped key/value block that only wraps when it contains a single long pair.
     */
    public B softPairs(String... rawPairs) {
        return pairs(MenuBlock.WrapMode.SOFT, rawPairs);
    }

    public B pairs(Map<?, ?> entries) {
        return pairs(MenuBlock.WrapMode.SINGLE_LINE, entries);
    }

    /**
     * Adds a grouped key/value block.
     * Multi-entry pair blocks stay one entry per lore line even when authored through a soft variant.
     */
    public B softPairs(Map<?, ?> entries) {
        return pairs(MenuBlock.WrapMode.SOFT, entries);
    }

    public <T> B pairs(Iterable<T> items, Function<T, String> keyMapper, Function<T, ?> valueMapper) {
        return pairs(MenuBlock.WrapMode.SINGLE_LINE, items, keyMapper, valueMapper);
    }

    public <T> B softPairs(Iterable<T> items, Function<T, String> keyMapper, Function<T, ?> valueMapper) {
        return pairs(MenuBlock.WrapMode.SOFT, items, keyMapper, valueMapper);
    }

    private B pairs(MenuBlock.WrapMode wrapMode, String... rawPairs) {
        Objects.requireNonNull(rawPairs, "rawPairs");
        if (rawPairs.length == 0 || rawPairs.length % 2 != 0) {
            throw new IllegalArgumentException("rawPairs must contain an even number of entries");
        }
        List<MenuBlock.Pairs.Entry> entries = new ArrayList<>();
        for (int i = 0; i < rawPairs.length; i += 2) {
            entries.add(new MenuBlock.Pairs.Entry(requireText(rawPairs[i], "key"), UiValue.of(requireText(rawPairs[i + 1], "value"))));
        }
        return pairs(wrapMode, entries);
    }

    private B pairs(MenuBlock.WrapMode wrapMode, Map<?, ?> entries) {
        Objects.requireNonNull(entries, "entries");
        List<MenuBlock.Pairs.Entry> pairs = new ArrayList<>();
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            pairs.add(new MenuBlock.Pairs.Entry(stringify(entry.getKey(), "key"), toUiValue(entry.getValue(), "value")));
        }
        return pairs(wrapMode, pairs);
    }

    private <T> B pairs(MenuBlock.WrapMode wrapMode, Iterable<T> items, Function<T, String> keyMapper, Function<T, ?> valueMapper) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(keyMapper, "keyMapper");
        Objects.requireNonNull(valueMapper, "valueMapper");
        List<MenuBlock.Pairs.Entry> pairs = new ArrayList<>();
        for (T item : items) {
            pairs.add(new MenuBlock.Pairs.Entry(requireText(keyMapper.apply(item), "key"), toUiValue(valueMapper.apply(item), "value")));
        }
        return pairs(wrapMode, pairs);
    }

    public B bullet(String bullet) {
        blocks.add(new MenuBlock.Bullets(List.of(requireText(bullet, "bullet"))));
        return self();
    }

    public B bullets(String... bullets) {
        return bullets(List.of(bullets));
    }

    public B bullets(Iterable<String> bullets) {
        blocks.add(new MenuBlock.Bullets(copyText(bullets, "bullets")));
        return self();
    }

    public <T> B bullets(Iterable<T> items, Function<T, String> formatter) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(formatter, "formatter");
        List<String> bullets = new ArrayList<>();
        for (T item : items) {
            bullets.add(requireText(formatter.apply(item), "bullet"));
        }
        blocks.add(new MenuBlock.Bullets(bullets));
        return self();
    }

    public B progress(String label, Number current, Number max) {
        return progress(label, current, max, AccentFamily.GOLD);
    }

    public B progress(String label, Number current, Number max, AccentFamily accentFamily) {
        blocks.add(new MenuBlock.Progress(
                requireText(label, "label"),
                asBigDecimal(current, "current"),
                asBigDecimal(max, "max"),
                Objects.requireNonNull(accentFamily, "accentFamily")));
        return self();
    }

    public B glow(boolean glow) {
        this.glow = glow;
        return self();
    }

    public B glow() {
        return glow(true);
    }

    protected MenuIcon icon() {
        return icon;
    }

    protected Component name() {
        if (name == null) {
            throw new IllegalStateException("name is required");
        }
        return name;
    }

    protected String secondary() {
        return secondary;
    }

    protected List<MenuBlock> blocks() {
        return List.copyOf(blocks);
    }

    protected boolean isGlowing() {
        return glow;
    }

    private B lines(MenuBlock.WrapMode wrapMode, List<String> lines) {
        blocks.add(new MenuBlock.Lines(lines, wrapMode));
        return self();
    }

    private B valueLines(MenuBlock.WrapMode wrapMode, List<MenuBlock.ValueLines.Entry> lines) {
        blocks.add(new MenuBlock.ValueLines(lines, wrapMode));
        return self();
    }

    private B pairs(MenuBlock.WrapMode wrapMode, List<MenuBlock.Pairs.Entry> pairs) {
        blocks.add(new MenuBlock.Pairs(pairs, wrapMode));
        return self();
    }

    private static List<String> copyText(Iterable<String> lines, String label) {
        Objects.requireNonNull(lines, label);
        List<String> copy = new ArrayList<>();
        for (String line : lines) {
            copy.add(requireText(line, label));
        }
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be empty");
        }
        return List.copyOf(copy);
    }

    private static List<MenuBlock.ValueLines.Entry> copyValueLines(Iterable<MenuValueLine> lines) {
        Objects.requireNonNull(lines, "lines");
        List<MenuBlock.ValueLines.Entry> copy = new ArrayList<>();
        for (MenuValueLine line : lines) {
            Objects.requireNonNull(line, "line");
            copy.add(new MenuBlock.ValueLines.Entry(line.prefix(), line.value()));
        }
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("lines cannot be empty");
        }
        return List.copyOf(copy);
    }

    private static List<MenuBlock.Pairs.Entry> copyPairs(Iterable<MenuPair> pairs) {
        Objects.requireNonNull(pairs, "pairs");
        List<MenuBlock.Pairs.Entry> copy = new ArrayList<>();
        for (MenuPair pair : pairs) {
            Objects.requireNonNull(pair, "pair");
            copy.add(new MenuBlock.Pairs.Entry(pair.key(), pair.value()));
        }
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("pairs cannot be empty");
        }
        return List.copyOf(copy);
    }

    private static BigDecimal asBigDecimal(Number value, String label) {
        Objects.requireNonNull(value, label);
        return new BigDecimal(String.valueOf(value));
    }

    private static UiValue toUiValue(Object value, String label) {
        Objects.requireNonNull(value, label);
        if (value instanceof UiValue uiValue) {
            return uiValue;
        }
        if (value instanceof ComponentLike componentLike) {
            return UiValue.of(flatten(componentLike.asComponent()));
        }
        return UiValue.of(requireText(String.valueOf(value), label));
    }

    private static String stringify(Object value, String label) {
        Objects.requireNonNull(value, label);
        if (value instanceof ComponentLike componentLike) {
            return flatten(componentLike.asComponent());
        }
        return requireText(String.valueOf(value), label);
    }

    private static String flatten(Component component) {
        StringBuilder builder = new StringBuilder();
        appendPlain(builder, component);
        return requireText(builder.toString(), "component");
    }

    private static void appendPlain(StringBuilder builder, Component component) {
        if (component instanceof TextComponent textComponent) {
            builder.append(textComponent.content());
        }
        for (Component child : component.children()) {
            appendPlain(builder, child);
        }
    }

    private static String requireText(String text, String label) {
        Objects.requireNonNull(text, label);
        if (text.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return text;
    }

    protected abstract B self();
}
