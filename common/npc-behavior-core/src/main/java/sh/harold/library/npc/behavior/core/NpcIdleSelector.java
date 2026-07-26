package sh.harold.library.npc.behavior.core;

import sh.harold.library.npc.behavior.NpcIdleEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Weighted shuffle-free idle selection with per-entry cooldown deadlines. */
public final class NpcIdleSelector {

    private final NpcBehaviorRandom random;
    private final Map<NpcIdleEntry, Long> cooldownUntil = new HashMap<>();

    public NpcIdleSelector(NpcBehaviorRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public synchronized Optional<NpcIdleEntry> select(
            List<NpcIdleEntry> entries,
            long tick,
            java.util.function.Predicate<NpcIdleEntry> eligibility
    ) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(eligibility, "eligibility");
        List<NpcIdleEntry> eligible = new ArrayList<>();
        long totalWeight = 0L;
        for (NpcIdleEntry entry : entries) {
            Objects.requireNonNull(entry, "entries contains null");
            if (tick < cooldownUntil.getOrDefault(entry, 0L) || !eligibility.test(entry)) {
                continue;
            }
            eligible.add(entry);
            totalWeight = Math.addExact(totalWeight, entry.weight());
        }
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        // Public weights are ints, but accumulate in long to avoid overflow.
        double draw = random.nextDouble() * totalWeight;
        long cursor = 0L;
        for (NpcIdleEntry entry : eligible) {
            cursor += entry.weight();
            if (draw < cursor) {
                return Optional.of(entry);
            }
        }
        return Optional.of(eligible.get(eligible.size() - 1));
    }

    public synchronized void completed(NpcIdleEntry entry, long tick) {
        Objects.requireNonNull(entry, "entry");
        long minimum = entry.cooldown().minimumTicks();
        long maximum = entry.cooldown().maximumTicks();
        long duration;
        if (minimum == maximum) {
            duration = minimum;
        } else {
            double draw = random.nextDouble();
            duration = minimum + (long) Math.floor(draw * (maximum - minimum + 1L));
        }
        cooldownUntil.put(entry, Math.addExact(tick, duration));
    }

    public synchronized void reset() {
        cooldownUntil.clear();
    }

    public synchronized Map<NpcIdleEntry, Long> cooldowns() {
        return Map.copyOf(cooldownUntil);
    }
}
