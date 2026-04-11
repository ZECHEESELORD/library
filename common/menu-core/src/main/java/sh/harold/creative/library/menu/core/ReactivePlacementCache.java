package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import sh.harold.creative.library.menu.MenuBlock;
import sh.harold.creative.library.menu.MenuClick;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuInteraction;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuSlot;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ReactivePlacementCache {

    private static final int CACHE_LIMIT = 512;

    private final Map<MenuVisualKey, CompiledMenuPresentation> compiledSlots =
            new LinkedHashMap<>(CACHE_LIMIT, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<MenuVisualKey, CompiledMenuPresentation> eldest) {
                    return size() > CACHE_LIMIT;
                }
            };
    private int hits;
    private int misses;

    void beginRender() {
        hits = 0;
        misses = 0;
    }

    MenuSlot compile(int slot, MenuItem item) {
        MenuVisualKey cacheKey = MenuVisualKey.from(item);
        CompiledMenuPresentation compiled = compiledSlots.get(cacheKey);
        if (compiled != null) {
            hits++;
            return compiled.toMenuSlot(slot, item.interactions());
        }
        misses++;
        long started = System.nanoTime();
        compiled = HouseMenuCompiler.compilePresentation(item);
        long elapsed = System.nanoTime() - started;
        compiledSlots.put(cacheKey, compiled);
        CompiledMenuPresentation presentation = compiled;
        MenuTrace.addDuration("state.reactive.compilePlacements", elapsed);
        MenuTrace.detailIfSlow("placement-compile", elapsed,
                () -> "slot=" + slot + " title=" + ComponentText.flatten(presentation.title()));
        return presentation.toMenuSlot(slot, item.interactions());
    }

    int hits() {
        return hits;
    }

    int misses() {
        return misses;
    }

    private record MenuVisualKey(
            MenuIcon icon,
            Component title,
            String secondary,
            List<MenuBlock> blocks,
            List<Component> exactLore,
            boolean glow,
            int amount,
            boolean promptSuppressed,
            Map<MenuClick, String> prompts
    ) {

        private MenuVisualKey {
            icon = Objects.requireNonNull(icon, "icon");
            title = Objects.requireNonNull(title, "title");
            blocks = List.copyOf(blocks);
            exactLore = List.copyOf(exactLore);
            prompts = Map.copyOf(prompts);
        }

        static MenuVisualKey from(MenuItem item) {
            Map<MenuClick, String> prompts = new EnumMap<>(MenuClick.class);
            for (Map.Entry<MenuClick, MenuInteraction> entry : item.interactions().entrySet()) {
                prompts.put(entry.getKey(), entry.getValue().promptLabel());
            }
            return new MenuVisualKey(item.icon(), item.name(), item.secondary().orElse(null), item.blocks(),
                    item.exactLore().orElse(List.of()),
                    item.glow(), item.amount(), item.promptSuppressed(), prompts);
        }
    }
}
