package sh.harold.library.scoreboard;

import net.kyori.adventure.text.Component;

import java.util.Objects;

public record ScoreboardLine(int index, Component content) {

    public ScoreboardLine {
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
        content = Objects.requireNonNull(content, "content");
    }
}
