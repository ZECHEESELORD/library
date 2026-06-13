package sh.harold.creative.library.scoreboard;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ScoreboardSpec {
    public static final int DEFAULT_MAX_LINES = 15;

    private final Key key;
    private final Component title;
    private final List<ScoreboardSection> sections;
    private final List<Component> footerLines;
    private final int maxLines;

    private ScoreboardSpec(
            Key key,
            Component title,
            List<ScoreboardSection> sections,
            List<Component> footerLines,
            int maxLines
    ) {
        this.key = Objects.requireNonNull(key, "key");
        this.title = Objects.requireNonNull(title, "title");
        this.sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        this.footerLines = List.copyOf(Objects.requireNonNull(footerLines, "footerLines"));
        if (maxLines <= 0) {
            throw new IllegalArgumentException("maxLines must be positive");
        }
        if (this.footerLines.size() > maxLines) {
            throw new IllegalArgumentException("footer lines must fit within maxLines");
        }
        ensureUniqueSections(this.sections);
        this.maxLines = maxLines;
    }

    public static Builder builder(Key key) {
        return new Builder(key);
    }

    public Key key() {
        return key;
    }

    public Component title() {
        return title;
    }

    public List<ScoreboardSection> sections() {
        return sections;
    }

    public List<Component> footerLines() {
        return footerLines;
    }

    public int maxLines() {
        return maxLines;
    }

    private static void ensureUniqueSections(List<ScoreboardSection> sections) {
        Set<String> ids = new HashSet<>();
        for (ScoreboardSection section : sections) {
            if (!ids.add(section.id())) {
                throw new IllegalArgumentException("duplicate scoreboard section id: " + section.id());
            }
        }
    }

    public static final class Builder {
        private final Key key;
        private Component title = Component.empty();
        private final List<ScoreboardSection> sections = new ArrayList<>();
        private final List<Component> footerLines = new ArrayList<>();
        private int maxLines = DEFAULT_MAX_LINES;

        private Builder(Key key) {
            this.key = Objects.requireNonNull(key, "key");
        }

        public Builder title(Component title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        public Builder section(ScoreboardSection section) {
            this.sections.add(Objects.requireNonNull(section, "section"));
            return this;
        }

        public Builder section(String id, ScoreboardContent content) {
            return section(ScoreboardSection.dynamic(id, content));
        }

        public Builder fixedSection(String id, Component... lines) {
            return section(ScoreboardSection.fixed(id, lines));
        }

        public Builder footer(Component... lines) {
            Objects.requireNonNull(lines, "lines");
            this.footerLines.clear();
            this.footerLines.addAll(List.of(lines));
            return this;
        }

        public Builder footer(List<Component> lines) {
            this.footerLines.clear();
            this.footerLines.addAll(List.copyOf(Objects.requireNonNull(lines, "lines")));
            return this;
        }

        public Builder maxLines(int maxLines) {
            this.maxLines = maxLines;
            return this;
        }

        public ScoreboardSpec build() {
            return new ScoreboardSpec(key, title, sections, footerLines, maxLines);
        }
    }
}
