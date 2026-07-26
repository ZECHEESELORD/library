package sh.harold.library.npc.behavior.core;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import sh.harold.library.npc.behavior.NpcCooldownRange;
import sh.harold.library.npc.behavior.NpcIdleEntry;
import sh.harold.library.npc.behavior.NpcRoutine;
import sh.harold.library.npc.behavior.NpcTimingBand;

import java.util.ArrayDeque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcIdleSelectorTest {

    @Test
    void weightedDrawsHonorEligibilityAndCompletedCooldowns() {
        ScriptedRandom random = new ScriptedRandom(0.0, 0.26, 0.0, 0.0);
        NpcIdleSelector selector = new NpcIdleSelector(random);
        NpcIdleEntry quiet = entry("quiet", 1, 5, 5);
        NpcIdleEntry expressive = entry("expressive", 3, 0, 0);
        List<NpcIdleEntry> entries = List.of(quiet, expressive);

        assertSame(quiet, selector.select(entries, 10, ignored -> true).orElseThrow());
        assertSame(expressive, selector.select(entries, 10, ignored -> true).orElseThrow());

        selector.completed(quiet, 10);
        assertEquals(15L, selector.cooldowns().get(quiet));
        assertSame(expressive, selector.select(entries, 14, ignored -> true).orElseThrow());
        assertSame(quiet, selector.select(entries, 15, ignored -> true).orElseThrow());
        assertTrue(selector.select(entries, 15, ignored -> false).isEmpty());
    }

    @Test
    void cancellationDoesNotStartCooldownAndFreshSelectionRestartsTheRoutine() {
        NpcIdleSelector selector = new NpcIdleSelector(new ScriptedRandom(0.0, 0.0));
        NpcIdleEntry idle = entry("restartable", 1, 20, 20);

        NpcIdleEntry canceled = selector.select(List.of(idle), 100, ignored -> true).orElseThrow();
        // Cancellation deliberately does not call completed: a later ambient selection starts
        // the immutable sequence from step zero instead of resuming the canceled step.
        NpcIdleEntry selectedAgain = selector.select(List.of(idle), 101, ignored -> true).orElseThrow();

        assertSame(canceled, selectedAgain);
        assertSame(idle.routine().steps().getFirst(), selectedAgain.routine().steps().getFirst());
        assertTrue(selector.cooldowns().isEmpty());
    }

    private static NpcIdleEntry entry(
            String key,
            int weight,
            long minimumCooldown,
            long maximumCooldown
    ) {
        return new NpcIdleEntry(
                NpcRoutine.builder(Key.key("test", key)).wait(NpcTimingBand.QUICK).build(),
                weight,
                NpcCooldownRange.ticks(minimumCooldown, maximumCooldown)
        );
    }

    private static final class ScriptedRandom implements NpcBehaviorRandom {
        private final ArrayDeque<Double> doubles;

        private ScriptedRandom(double... doubles) {
            this.doubles = new ArrayDeque<>();
            for (double value : doubles) {
                this.doubles.add(value);
            }
        }

        @Override
        public int nextInt(int originInclusive, int boundExclusive) {
            return originInclusive;
        }

        @Override
        public double nextDouble() {
            return doubles.removeFirst();
        }
    }
}
