package sh.harold.library.scoreboard;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Objects;

public record ScoreboardFrame(Key scoreboardKey, Component title, List<ScoreboardLine> lines) {

    public ScoreboardFrame {
        scoreboardKey = Objects.requireNonNull(scoreboardKey, "scoreboardKey");
        title = Objects.requireNonNull(title, "title");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    }

    public boolean empty() {
        return lines.isEmpty();
    }
}
