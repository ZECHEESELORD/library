package sh.harold.library.scoreboard;

import net.kyori.adventure.text.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record ScoreboardSection(String id, ScoreboardContent content) {

    public ScoreboardSection {
        id = ScoreboardValidation.requireSectionId(id);
        content = Objects.requireNonNull(content, "content");
    }

    public static ScoreboardSection dynamic(String id, ScoreboardContent content) {
        return new ScoreboardSection(id, content);
    }

    public static ScoreboardSection fixed(String id, Component... lines) {
        Objects.requireNonNull(lines, "lines");
        return fixed(id, Arrays.asList(lines));
    }

    public static ScoreboardSection fixed(String id, List<Component> lines) {
        List<Component> fixedLines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        return new ScoreboardSection(id, ignored -> fixedLines);
    }
}
