package sh.harold.library.menu;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Objects;

public sealed interface MenuBlock permits MenuBlock.Description, MenuBlock.Lines, MenuBlock.MutedLines,
        MenuBlock.Options, MenuBlock.ValueLines, MenuBlock.Pairs, MenuBlock.Bullets,
        MenuBlock.Checklist, MenuBlock.Progress {

    record Description(Component content) implements MenuBlock {

        public Description {
            content = MenuComponents.requireContent(Objects.requireNonNull(content, "content"), "content");
        }
    }

    record Lines(List<Component> lines) implements MenuBlock {

        public Lines {
            lines = immutableComponents(lines, "lines");
        }
    }

    record MutedLines(List<Component> lines) implements MenuBlock {

        public MutedLines {
            lines = immutableComponents(lines, "lines");
        }
    }

    record Options(List<MenuOptionLine> options, int windowSize) implements MenuBlock {

        public Options {
            Objects.requireNonNull(options, "options");
            options = List.copyOf(options);
            if (options.isEmpty()) {
                throw new IllegalArgumentException("options cannot be empty");
            }
            if (windowSize < 0) {
                throw new IllegalArgumentException("windowSize cannot be negative");
            }
        }
    }

    record ValueLines(List<Entry> lines) implements MenuBlock {

        public ValueLines {
            Objects.requireNonNull(lines, "lines");
            lines = List.copyOf(lines);
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("lines cannot be empty");
            }
        }

        public record Entry(Component prefix, Component value) {

            public Entry {
                prefix = Objects.requireNonNull(prefix, "prefix");
                value = MenuComponents.requireContent(Objects.requireNonNull(value, "value"), "value");
            }
        }
    }

    record Pairs(List<Entry> pairs) implements MenuBlock {

        public Pairs {
            Objects.requireNonNull(pairs, "pairs");
            pairs = List.copyOf(pairs);
            if (pairs.isEmpty()) {
                throw new IllegalArgumentException("pairs cannot be empty");
            }
        }

        public record Entry(Component key, Component value) {

            public Entry {
                key = MenuComponents.requireContent(Objects.requireNonNull(key, "key"), "key");
                value = MenuComponents.requireContent(Objects.requireNonNull(value, "value"), "value");
            }
        }
    }

    record Bullets(List<Component> bullets) implements MenuBlock {

        public Bullets {
            bullets = immutableComponents(bullets, "bullets");
        }
    }

    record Checklist(List<MenuChecklistEntry> entries) implements MenuBlock {

        public Checklist {
            Objects.requireNonNull(entries, "entries");
            entries = List.copyOf(entries);
            if (entries.isEmpty()) {
                throw new IllegalArgumentException("entries cannot be empty");
            }
        }
    }

    record Progress(MenuProgress progress) implements MenuBlock {

        public Progress {
            progress = Objects.requireNonNull(progress, "progress");
        }
    }

    private static List<Component> immutableComponents(List<Component> components, String label) {
        Objects.requireNonNull(components, label);
        List<Component> copy = List.copyOf(components);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be empty");
        }
        for (Component component : copy) {
            MenuComponents.requireContent(component, label + " entry");
        }
        return copy;
    }
}
