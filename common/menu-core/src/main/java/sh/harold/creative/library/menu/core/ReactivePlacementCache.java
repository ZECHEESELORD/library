package sh.harold.creative.library.menu.core;

import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

final class ReactivePlacementCache {

    private final Map<ReactivePlacementKey, MenuSlot> compiledSlots = new HashMap<>();
    private int hits;
    private int misses;

    void beginRender() {
        hits = 0;
        misses = 0;
    }

    MenuSlot compile(int slot, MenuItem item) {
        ReactivePlacementKey cacheKey = new ReactivePlacementKey(slot, item);
        MenuSlot compiled = compiledSlots.get(cacheKey);
        if (compiled != null) {
            hits++;
            return compiled;
        }
        misses++;
        long started = System.nanoTime();
        compiled = HouseMenuCompiler.compile(slot, item);
        long elapsed = System.nanoTime() - started;
        compiledSlots.put(cacheKey, compiled);
        MenuTrace.addDuration("state.reactive.compilePlacements", elapsed);
        MenuSlot compiledSlot = compiled;
        MenuTrace.detailIfSlow("placement-compile", elapsed,
                () -> "slot=" + slot + " title=" + ComponentText.flatten(compiledSlot.title()));
        return compiled;
    }

    int hits() {
        return hits;
    }

    int misses() {
        return misses;
    }

    private record ReactivePlacementKey(int slot, MenuItem item) {

        private ReactivePlacementKey {
            Objects.requireNonNull(item, "item");
        }

        @Override
        public int hashCode() {
            return 31 * slot + System.identityHashCode(item);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ReactivePlacementKey key && slot == key.slot && item == key.item;
        }
    }
}
