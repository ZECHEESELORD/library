package sh.harold.library.npc.behavior;

import java.util.Objects;

public record NpcIdleEntry(NpcRoutine routine, int weight, NpcCooldownRange cooldown) {
    public NpcIdleEntry {
        Objects.requireNonNull(routine, "routine");
        Objects.requireNonNull(cooldown, "cooldown");
        if (weight < 1) {
            throw new IllegalArgumentException("weight must be positive");
        }
    }

    public NpcIdleEntry(NpcRoutine routine, int weight) {
        this(routine, weight, NpcCooldownRange.NONE);
    }
}
