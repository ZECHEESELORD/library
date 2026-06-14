package sh.harold.library.scoreboard;

import java.util.Objects;

final class ScoreboardValidation {
    private ScoreboardValidation() {
    }

    static String requireSectionId(String id) {
        String value = Objects.requireNonNull(id, "id");
        if (value.isBlank()) {
            throw new IllegalArgumentException("section id must not be blank");
        }
        return value;
    }
}
