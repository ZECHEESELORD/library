package sh.harold.library.npc.behavior.core;

import net.kyori.adventure.text.Component;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** One immediately visible speech bubble and its actor-tick deadline. */
public record NpcBubbleFrame(
        long id,
        Component text,
        long expiresAtTick,
        Kind kind,
        Set<UUID> excludedViewers
) {
    public NpcBubbleFrame {
        if (id < 0) {
            throw new IllegalArgumentException("id must not be negative");
        }
        text = Objects.requireNonNull(text, "text");
        if (expiresAtTick < 0) {
            throw new IllegalArgumentException("expiresAtTick must not be negative");
        }
        kind = Objects.requireNonNull(kind, "kind");
        excludedViewers = Set.copyOf(Objects.requireNonNull(excludedViewers, "excludedViewers"));
    }

    public NpcBubbleFrame(long id, Component text, long expiresAtTick, Kind kind) {
        this(id, text, expiresAtTick, kind, Set.of());
    }

    public enum Kind {
        WORLD,
        ATTENTION,
        CONVERSATION,
        INTERRUPTION
    }
}
