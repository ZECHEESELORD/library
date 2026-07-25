package sh.harold.library.npc.behavior;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A point-in-time immutable view. The acquisition stack is oldest to newest.
 */
public record NpcBehaviorSnapshot(
        boolean configured,
        NpcBehaviorActivity activity,
        Optional<UUID> canonicalTarget,
        List<UUID> acquisitionStack,
        Optional<Key> activeRoutine,
        Optional<Component> visibleSpeech,
        int queuedSpeech,
        boolean conversationLocked,
        long revision
) {
    public NpcBehaviorSnapshot {
        Objects.requireNonNull(activity, "activity");
        canonicalTarget = Objects.requireNonNull(canonicalTarget, "canonicalTarget");
        acquisitionStack = List.copyOf(Objects.requireNonNull(acquisitionStack, "acquisitionStack"));
        activeRoutine = Objects.requireNonNull(activeRoutine, "activeRoutine");
        visibleSpeech = Objects.requireNonNull(visibleSpeech, "visibleSpeech");
        if (queuedSpeech < 0) {
            throw new IllegalArgumentException("queuedSpeech cannot be negative");
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("revision cannot be negative");
        }
    }

    public static NpcBehaviorSnapshot inert() {
        return new NpcBehaviorSnapshot(
                false,
                NpcBehaviorActivity.INERT,
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                0,
                false,
                0L
        );
    }
}
