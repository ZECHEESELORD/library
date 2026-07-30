package sh.harold.library.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

abstract class AbstractMenuItemBuilder<B extends AbstractMenuItemBuilder<B>> {

    private MenuIcon icon;
    private final List<MenuSection> sections = new ArrayList<>();
    private Component name;
    private Component secondary;
    private List<Component> statusLines = List.of();
    private List<Component> exactLore;
    private boolean glow;
    private MenuTooltipBehavior tooltipBehavior = MenuTooltipBehavior.CHROME;

    AbstractMenuItemBuilder(MenuIcon icon) {
        this.icon = Objects.requireNonNull(icon, "icon");
    }

    public B icon(MenuIcon icon) {
        this.icon = Objects.requireNonNull(icon, "icon");
        return self();
    }

    public B name(String name) {
        return name(Component.text(MenuComponents.requireText(name, "name")));
    }

    public B name(ComponentLike name) {
        this.name = MenuComponents.requireContent(Objects.requireNonNull(name, "name").asComponent(), "name")
                .decoration(TextDecoration.ITALIC, false);
        return self();
    }

    public B exactName(ComponentLike name) {
        this.name = MenuComponents.requireContent(Objects.requireNonNull(name, "name").asComponent(), "name");
        return self();
    }

    public B exactLore(ComponentLike... lines) {
        Objects.requireNonNull(lines, "lines");
        return exactLore(List.of(lines));
    }

    public B exactLore(Iterable<? extends ComponentLike> lines) {
        Objects.requireNonNull(lines, "lines");
        List<Component> copied = new ArrayList<>();
        for (ComponentLike line : lines) {
            copied.add(Objects.requireNonNull(line, "line").asComponent());
        }
        this.exactLore = List.copyOf(copied);
        return self();
    }

    public B secondary(String secondary) {
        return secondary(Component.text(MenuComponents.requireText(secondary, "secondary")));
    }

    public B secondary(ComponentLike secondary) {
        this.secondary = MenuComponents.requireContent(
                Objects.requireNonNull(secondary, "secondary").asComponent(), "secondary");
        return self();
    }

    public B section(Consumer<MenuSection.Builder> author) {
        Objects.requireNonNull(author, "author");
        MenuSection.Builder builder = MenuSection.builder();
        author.accept(builder);
        sections.add(builder.build());
        return self();
    }

    public B description(String description) {
        return addSection(builder -> builder.paragraph(description));
    }

    public B description(ComponentLike description) {
        return addSection(builder -> builder.paragraph(description));
    }

    public B line(String line) {
        return addSection(builder -> builder.line(line));
    }

    public B line(ComponentLike line) {
        return addSection(builder -> builder.line(line));
    }

    public B lines(String... lines) {
        return addSection(builder -> builder.lines(lines));
    }

    public B lines(Iterable<String> lines) {
        return addSection(builder -> builder.lines(lines));
    }

    public B componentLines(ComponentLike... lines) {
        return addSection(builder -> builder.componentLines(lines));
    }

    public B mutedLine(String line) {
        return addSection(builder -> builder.mutedLine(line));
    }

    public B mutedLine(ComponentLike line) {
        return addSection(builder -> builder.mutedLine(line));
    }

    public B mutedLines(String... lines) {
        return addSection(builder -> builder.mutedLines(lines));
    }

    public B mutedLines(Iterable<String> lines) {
        return addSection(builder -> builder.mutedLines(lines));
    }

    public B mutedComponents(ComponentLike... lines) {
        return addSection(builder -> builder.mutedComponents(lines));
    }

    public B options(MenuOptionLine... options) {
        return addSection(builder -> builder.options(options));
    }

    public B options(Iterable<MenuOptionLine> options) {
        return addSection(builder -> builder.options(options));
    }

    public B options(int windowSize, Iterable<MenuOptionLine> options) {
        return addSection(builder -> builder.options(windowSize, options));
    }

    public B valueLine(String prefix, Object value) {
        return addSection(builder -> builder.valueLine(prefix, value));
    }

    public B valueLine(ComponentLike prefix, ComponentLike value) {
        return addSection(builder -> builder.valueLine(prefix, value));
    }

    public B valueLines(MenuValueLine... lines) {
        return addSection(builder -> builder.valueLines(lines));
    }

    public B valueLines(Iterable<MenuValueLine> lines) {
        return addSection(builder -> builder.valueLines(lines));
    }

    public B pair(String key, Object value) {
        return addSection(builder -> builder.pair(key, value));
    }

    public B pair(ComponentLike key, ComponentLike value) {
        return addSection(builder -> builder.pair(key, value));
    }

    public B pairs(String... rawPairs) {
        return addSection(builder -> builder.pairs(rawPairs));
    }

    public B pairs(MenuPair... pairs) {
        return addSection(builder -> builder.pairs(pairs));
    }

    public B pairs(Iterable<MenuPair> pairs) {
        return addSection(builder -> builder.pairs(pairs));
    }

    public B pairs(Map<?, ?> entries) {
        return addSection(builder -> builder.pairs(entries));
    }

    public <T> B pairs(Iterable<T> items, Function<T, ?> keyMapper, Function<T, ?> valueMapper) {
        return addSection(builder -> builder.pairs(items, keyMapper, valueMapper));
    }

    public B bullet(String bullet) {
        return addSection(builder -> builder.bullet(bullet));
    }

    public B bullet(ComponentLike bullet) {
        return addSection(builder -> builder.bullet(bullet));
    }

    public B bullets(String... bullets) {
        return addSection(builder -> builder.bullets(bullets));
    }

    public B bullets(Iterable<String> bullets) {
        return addSection(builder -> builder.bullets(bullets));
    }

    public B componentBullets(ComponentLike... bullets) {
        return addSection(builder -> builder.componentBullets(bullets));
    }

    public <T> B bullets(Iterable<T> items, Function<T, String> formatter) {
        return addSection(builder -> builder.bullets(items, formatter));
    }

    public B checklist(MenuChecklistEntry... entries) {
        return addSection(builder -> builder.checklist(entries));
    }

    public B checklist(Iterable<MenuChecklistEntry> entries) {
        return addSection(builder -> builder.checklist(entries));
    }

    public B progress(MenuProgress progress) {
        return addSection(builder -> builder.progress(progress));
    }

    public B progress(String label, Number current, Number max) {
        return progress(MenuProgress.of(label, current, max));
    }

    public B progress(String label, Number current, Number max, String unit) {
        return progress(MenuProgress.builder(label, current, max).unit(unit).build());
    }

    public B progress(String label, Number current, Number max, MenuProgressPalette palette) {
        return progress(MenuProgress.builder(label, current, max).palette(palette).build());
    }

    public B progress(String label, Number current, Number max, String unit, MenuProgressPalette palette) {
        return progress(MenuProgress.builder(label, current, max).unit(unit).palette(palette).build());
    }

    public B status(ComponentLike... lines) {
        Objects.requireNonNull(lines, "lines");
        this.statusLines = MenuComponents.copyComponents(List.of(lines), "status");
        return self();
    }

    public B status(Iterable<? extends ComponentLike> lines) {
        this.statusLines = MenuComponents.copyComponents(lines, "status");
        return self();
    }

    public B glow(boolean glow) {
        this.glow = glow;
        return self();
    }

    public B glow() {
        return glow(true);
    }

    public B tooltipBehavior(MenuTooltipBehavior tooltipBehavior) {
        this.tooltipBehavior = Objects.requireNonNull(tooltipBehavior, "tooltipBehavior");
        return self();
    }

    public B literalItem() {
        return tooltipBehavior(MenuTooltipBehavior.LITERAL);
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

    protected Component secondary() {
        return secondary;
    }

    protected List<MenuSection> sections() {
        return List.copyOf(sections);
    }

    protected List<Component> statusLines() {
        return statusLines;
    }

    protected List<Component> exactLore() {
        return exactLore;
    }

    protected boolean isGlowing() {
        return glow;
    }

    protected MenuTooltipBehavior tooltipBehavior() {
        return tooltipBehavior;
    }

    private B addSection(Consumer<MenuSection.Builder> author) {
        return section(author);
    }

    protected abstract B self();
}
