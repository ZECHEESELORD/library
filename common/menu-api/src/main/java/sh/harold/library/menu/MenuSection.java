package sh.harold.library.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public record MenuSection(List<MenuBlock> blocks) {

    public MenuSection {
        Objects.requireNonNull(blocks, "blocks");
        blocks = List.copyOf(blocks);
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("blocks cannot be empty");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<MenuBlock> blocks = new ArrayList<>();

        public Builder paragraph(String text) {
            return paragraph(Component.text(MenuComponents.requireText(text, "text")));
        }

        public Builder paragraph(ComponentLike content) {
            blocks.add(new MenuBlock.Description(Objects.requireNonNull(content, "content").asComponent()));
            return this;
        }

        public Builder line(String line) {
            return line(Component.text(MenuComponents.requireText(line, "line")));
        }

        public Builder line(ComponentLike line) {
            blocks.add(new MenuBlock.Lines(List.of(Objects.requireNonNull(line, "line").asComponent())));
            return this;
        }

        public Builder lines(String... lines) {
            Objects.requireNonNull(lines, "lines");
            return lines(List.of(lines));
        }

        public Builder lines(Iterable<String> lines) {
            blocks.add(new MenuBlock.Lines(textComponents(lines, "lines")));
            return this;
        }

        public Builder componentLines(ComponentLike... lines) {
            Objects.requireNonNull(lines, "lines");
            return componentLines(List.of(lines));
        }

        public Builder componentLines(Iterable<? extends ComponentLike> lines) {
            blocks.add(new MenuBlock.Lines(MenuComponents.copyComponents(lines, "lines")));
            return this;
        }

        public Builder mutedLine(String line) {
            return mutedLine(Component.text(MenuComponents.requireText(line, "line")));
        }

        public Builder mutedLine(ComponentLike line) {
            blocks.add(new MenuBlock.MutedLines(List.of(Objects.requireNonNull(line, "line").asComponent())));
            return this;
        }

        public Builder mutedLines(String... lines) {
            Objects.requireNonNull(lines, "lines");
            return mutedLines(List.of(lines));
        }

        public Builder mutedLines(Iterable<String> lines) {
            blocks.add(new MenuBlock.MutedLines(textComponents(lines, "lines")));
            return this;
        }

        public Builder mutedComponents(ComponentLike... lines) {
            Objects.requireNonNull(lines, "lines");
            blocks.add(new MenuBlock.MutedLines(MenuComponents.copyComponents(List.of(lines), "lines")));
            return this;
        }

        public Builder options(MenuOptionLine... options) {
            Objects.requireNonNull(options, "options");
            return options(List.of(options));
        }

        public Builder options(Iterable<MenuOptionLine> options) {
            return options(0, options);
        }

        public Builder options(int windowSize, Iterable<MenuOptionLine> options) {
            blocks.add(new MenuBlock.Options(copyOptions(options), windowSize));
            return this;
        }

        public Builder valueLine(String prefix, Object value) {
            return valueLine(Component.text(Objects.requireNonNull(prefix, "prefix")), MenuComponents.component(value));
        }

        public Builder valueLine(ComponentLike prefix, ComponentLike value) {
            blocks.add(new MenuBlock.ValueLines(List.of(new MenuBlock.ValueLines.Entry(
                    Objects.requireNonNull(prefix, "prefix").asComponent(),
                    Objects.requireNonNull(value, "value").asComponent()))));
            return this;
        }

        public Builder valueLines(MenuValueLine... lines) {
            Objects.requireNonNull(lines, "lines");
            return valueLines(List.of(lines));
        }

        public Builder valueLines(Iterable<MenuValueLine> lines) {
            Objects.requireNonNull(lines, "lines");
            List<MenuBlock.ValueLines.Entry> entries = new ArrayList<>();
            for (MenuValueLine line : lines) {
                MenuValueLine valueLine = Objects.requireNonNull(line, "line");
                entries.add(new MenuBlock.ValueLines.Entry(valueLine.prefix(), valueLine.value()));
            }
            blocks.add(new MenuBlock.ValueLines(entries));
            return this;
        }

        public Builder pair(String key, Object value) {
            return pair(Component.text(MenuComponents.requireText(key, "key")), MenuComponents.component(value));
        }

        public Builder pair(ComponentLike key, ComponentLike value) {
            blocks.add(new MenuBlock.Pairs(List.of(new MenuBlock.Pairs.Entry(
                    Objects.requireNonNull(key, "key").asComponent(),
                    Objects.requireNonNull(value, "value").asComponent()))));
            return this;
        }

        public Builder pairs(String... rawPairs) {
            Objects.requireNonNull(rawPairs, "rawPairs");
            if (rawPairs.length == 0 || rawPairs.length % 2 != 0) {
                throw new IllegalArgumentException("rawPairs must contain a non-empty even number of entries");
            }
            List<MenuBlock.Pairs.Entry> entries = new ArrayList<>();
            for (int index = 0; index < rawPairs.length; index += 2) {
                entries.add(new MenuBlock.Pairs.Entry(
                        Component.text(MenuComponents.requireText(rawPairs[index], "key")),
                        Component.text(MenuComponents.requireText(rawPairs[index + 1], "value"))));
            }
            blocks.add(new MenuBlock.Pairs(entries));
            return this;
        }

        public Builder pairs(MenuPair... pairs) {
            Objects.requireNonNull(pairs, "pairs");
            return pairs(List.of(pairs));
        }

        public Builder pairs(Iterable<MenuPair> pairs) {
            Objects.requireNonNull(pairs, "pairs");
            List<MenuBlock.Pairs.Entry> entries = new ArrayList<>();
            for (MenuPair pair : pairs) {
                MenuPair value = Objects.requireNonNull(pair, "pair");
                entries.add(new MenuBlock.Pairs.Entry(value.key(), value.value()));
            }
            blocks.add(new MenuBlock.Pairs(entries));
            return this;
        }

        public Builder pairs(Map<?, ?> entries) {
            Objects.requireNonNull(entries, "entries");
            List<MenuBlock.Pairs.Entry> pairs = new ArrayList<>();
            entries.forEach((key, value) -> pairs.add(new MenuBlock.Pairs.Entry(
                    MenuComponents.component(key), MenuComponents.component(value))));
            blocks.add(new MenuBlock.Pairs(pairs));
            return this;
        }

        public <T> Builder pairs(Iterable<T> items, Function<T, ?> keyMapper, Function<T, ?> valueMapper) {
            Objects.requireNonNull(items, "items");
            Objects.requireNonNull(keyMapper, "keyMapper");
            Objects.requireNonNull(valueMapper, "valueMapper");
            List<MenuBlock.Pairs.Entry> pairs = new ArrayList<>();
            for (T item : items) {
                pairs.add(new MenuBlock.Pairs.Entry(
                        MenuComponents.component(keyMapper.apply(item)),
                        MenuComponents.component(valueMapper.apply(item))));
            }
            blocks.add(new MenuBlock.Pairs(pairs));
            return this;
        }

        public Builder bullet(String bullet) {
            return bullet(Component.text(MenuComponents.requireText(bullet, "bullet")));
        }

        public Builder bullet(ComponentLike bullet) {
            blocks.add(new MenuBlock.Bullets(List.of(Objects.requireNonNull(bullet, "bullet").asComponent())));
            return this;
        }

        public Builder bullets(String... bullets) {
            Objects.requireNonNull(bullets, "bullets");
            return bullets(List.of(bullets));
        }

        public Builder bullets(Iterable<String> bullets) {
            blocks.add(new MenuBlock.Bullets(textComponents(bullets, "bullets")));
            return this;
        }

        public Builder componentBullets(ComponentLike... bullets) {
            Objects.requireNonNull(bullets, "bullets");
            blocks.add(new MenuBlock.Bullets(MenuComponents.copyComponents(List.of(bullets), "bullets")));
            return this;
        }

        public <T> Builder bullets(Iterable<T> items, Function<T, String> formatter) {
            Objects.requireNonNull(items, "items");
            Objects.requireNonNull(formatter, "formatter");
            List<String> bullets = new ArrayList<>();
            for (T item : items) {
                bullets.add(MenuComponents.requireText(formatter.apply(item), "bullet"));
            }
            return bullets(bullets);
        }

        public Builder checklist(MenuChecklistEntry... entries) {
            Objects.requireNonNull(entries, "entries");
            return checklist(List.of(entries));
        }

        public Builder checklist(Iterable<MenuChecklistEntry> entries) {
            Objects.requireNonNull(entries, "entries");
            List<MenuChecklistEntry> copy = new ArrayList<>();
            entries.forEach(entry -> copy.add(Objects.requireNonNull(entry, "entry")));
            blocks.add(new MenuBlock.Checklist(copy));
            return this;
        }

        public Builder progress(MenuProgress progress) {
            blocks.add(new MenuBlock.Progress(Objects.requireNonNull(progress, "progress")));
            return this;
        }

        public Builder progress(String label, Number current, Number max) {
            return progress(MenuProgress.of(label, current, max));
        }

        public Builder progress(String label, Number current, Number max, String unit) {
            return progress(MenuProgress.builder(label, current, max).unit(unit).build());
        }

        public MenuSection build() {
            return new MenuSection(blocks);
        }

        private static List<Component> textComponents(Iterable<String> values, String label) {
            Objects.requireNonNull(values, label);
            List<Component> components = new ArrayList<>();
            for (String value : values) {
                components.add(Component.text(MenuComponents.requireText(value, label + " entry")));
            }
            if (components.isEmpty()) {
                throw new IllegalArgumentException(label + " cannot be empty");
            }
            return List.copyOf(components);
        }

        private static List<MenuOptionLine> copyOptions(Iterable<MenuOptionLine> options) {
            Objects.requireNonNull(options, "options");
            List<MenuOptionLine> copy = new ArrayList<>();
            options.forEach(option -> copy.add(Objects.requireNonNull(option, "option")));
            if (copy.isEmpty()) {
                throw new IllegalArgumentException("options cannot be empty");
            }
            return List.copyOf(copy);
        }
    }
}
