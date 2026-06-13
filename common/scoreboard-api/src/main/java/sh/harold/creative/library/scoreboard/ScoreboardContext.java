package sh.harold.creative.library.scoreboard;

import net.kyori.adventure.key.Key;

import java.util.Objects;
import java.util.UUID;

public record ScoreboardContext(UUID viewerId, Key scoreboardKey, long tick) {

    public ScoreboardContext {
        viewerId = Objects.requireNonNull(viewerId, "viewerId");
        scoreboardKey = Objects.requireNonNull(scoreboardKey, "scoreboardKey");
        if (tick < 0L) {
            throw new IllegalArgumentException("tick must not be negative");
        }
    }
}
