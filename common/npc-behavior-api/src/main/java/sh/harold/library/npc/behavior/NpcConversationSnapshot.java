package sh.harold.library.npc.behavior;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record NpcConversationSnapshot(
        NpcConversationState state,
        List<UUID> cast,
        Optional<UUID> speaker,
        Optional<Component> currentLine,
        Optional<NpcConversationStagingMode> stagingMode,
        int completedTurns,
        int plannedTurns,
        long revision
) {
    public NpcConversationSnapshot {
        Objects.requireNonNull(state, "state");
        cast = List.copyOf(Objects.requireNonNull(cast, "cast"));
        speaker = Objects.requireNonNull(speaker, "speaker");
        currentLine = Objects.requireNonNull(currentLine, "currentLine");
        stagingMode = Objects.requireNonNull(stagingMode, "stagingMode");
        if (completedTurns < 0 || plannedTurns < 0 || completedTurns > plannedTurns) {
            throw new IllegalArgumentException("turn counts must be non-negative and ordered");
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("revision cannot be negative");
        }
    }
}
