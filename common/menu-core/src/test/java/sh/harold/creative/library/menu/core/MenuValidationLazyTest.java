package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.menu.MenuFrame;
import sh.harold.creative.library.menu.MenuGeometry;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuInteraction;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.MenuSlotAction;
import sh.harold.creative.library.menu.MenuClick;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuValidationLazyTest {

    @Test
    void validateOnlyResolvesInitialFrameAndKeepsFrameIdsLazy() {
        CountingFrameIds frameIds = new CountingFrameIds("page:0", "page:1");
        AtomicInteger resolverCalls = new AtomicInteger();
        StandardMenu menu = new StandardMenu(
                Component.text("Menu"),
                MenuGeometry.LIST,
                6,
                "page:0",
                frameIds,
                frameId -> {
                    resolverCalls.incrementAndGet();
                    return frame(frameId);
                });

        assertEquals(0, frameIds.iteratorCalls());

        MenuValidator.validate(menu);

        assertEquals(0, frameIds.iteratorCalls());
        assertEquals(1, resolverCalls.get());

        menu.frame("page:1");
        assertEquals(2, resolverCalls.get());
    }

    @Test
    void standardMenuDoesNotMaterializeFrameIdsOnConstruction() {
        CountingFrameIds frameIds = new CountingFrameIds("page:0", "page:1");

        new StandardMenu(
                Component.text("Menu"),
                MenuGeometry.LIST,
                6,
                "page:0",
                frameIds,
                frameId -> frame(frameId));

        assertEquals(0, frameIds.iteratorCalls());
    }

    @Test
    void reactivePlacementCacheReusesVisualCompilationAcrossSlotAndInteractionChanges() {
        ReactivePlacementCache cache = new ReactivePlacementCache();
        MenuSlot first = cache.compile(10, firstButton());
        MenuSlot second = cache.compile(22, secondButton());

        assertEquals(1, cache.misses());
        assertEquals(1, cache.hits());
        assertEquals(10, first.slot());
        assertEquals(22, second.slot());
        assertEquals(first.title(), second.title());
        assertEquals(first.lore(), second.lore());
    }

    @Test
    void reactivePlacementCacheEvictsOldVisualEntriesWhenBounded() {
        ReactivePlacementCache cache = new ReactivePlacementCache();
        cache.beginRender();

        MenuSlot first = null;
        for (int i = 0; i < 513; i++) {
            MenuSlot slot = cache.compile(0, displayItem("Item " + i));
            if (i == 0) {
                first = slot;
            }
        }

        assertEquals(513, cache.misses());
        assertEquals(0, cache.hits());

        MenuSlot recompiledFirst = cache.compile(0, displayItem("Item 0"));
        assertEquals(514, cache.misses());
        assertEquals(0, cache.hits());
        assertEquals(first.title(), recompiledFirst.title());
    }

    private static MenuFrame frame(String frameId) {
        return new MenuFrame(Component.text(frameId), List.of(new MenuSlot(
                0,
                MenuIcon.vanilla("stone"),
                Component.text(frameId),
                List.of(),
                false,
                Map.of(MenuClick.LEFT, MenuInteraction.of(
                        sh.harold.creative.library.menu.ActionVerb.VIEW,
                        new MenuSlotAction.Close())))));
    }

    private static sh.harold.creative.library.menu.MenuButton firstButton() {
        return sh.harold.creative.library.menu.MenuButton.builder(MenuIcon.vanilla("stone"))
                .name("Shared Visual")
                .action(sh.harold.creative.library.menu.ActionVerb.VIEW, "Inspect", context -> { })
                .build();
    }

    private static sh.harold.creative.library.menu.MenuButton secondButton() {
        return sh.harold.creative.library.menu.MenuButton.builder(MenuIcon.vanilla("stone"))
                .name("Shared Visual")
                .action(sh.harold.creative.library.menu.ActionVerb.OPEN, "Inspect", context -> { })
                .build();
    }

    private static sh.harold.creative.library.menu.MenuDisplayItem displayItem(String name) {
        return sh.harold.creative.library.menu.MenuDisplayItem.builder(MenuIcon.vanilla("stone"))
                .name(name)
                .build();
    }

    private static final class CountingFrameIds extends AbstractSet<String> {

        private final List<String> ids;
        private int iteratorCalls;

        private CountingFrameIds(String... ids) {
            this.ids = List.of(ids);
        }

        @Override
        public Iterator<String> iterator() {
            iteratorCalls++;
            return new Iterator<>() {
                private final Iterator<String> delegate = ids.iterator();

                @Override
                public boolean hasNext() {
                    return delegate.hasNext();
                }

                @Override
                public String next() {
                    if (!delegate.hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return delegate.next();
                }
            };
        }

        @Override
        public int size() {
            return ids.size();
        }

        @Override
        public boolean contains(Object object) {
            return ids.contains(object);
        }

        int iteratorCalls() {
            return iteratorCalls;
        }
    }
}
