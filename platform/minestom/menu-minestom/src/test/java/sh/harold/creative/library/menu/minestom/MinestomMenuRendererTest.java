package sh.harold.creative.library.menu.minestom;

import net.kyori.adventure.text.Component;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.MenuClick;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuInteraction;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.MenuSlotAction;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MinestomMenuRendererTest {

    @Test
    void cachesByVisualStateOnly() {
        AtomicInteger createCalls = new AtomicInteger();
        MinestomMenuRenderer renderer = new MinestomMenuRenderer((icon, amount) -> {
            createCalls.incrementAndGet();
            return ItemStack.of(Material.STONE, amount);
        });

        MenuSlot first = slot(1, Map.of(MenuClick.LEFT,
                MenuInteraction.of(ActionVerb.OPEN, "Inspect", new MenuSlotAction.Close())));
        MenuSlot second = slot(22, Map.of(MenuClick.RIGHT,
                MenuInteraction.of(ActionVerb.SELECT, "Inspect", new MenuSlotAction.Close())));

        ItemStack firstRendered = renderer.render(first);
        ItemStack secondRendered = renderer.render(second);

        assertEquals(1, createCalls.get());
        assertSame(firstRendered, secondRendered);
    }

    @Test
    void evictsOldEntriesWhenCacheIsBounded() {
        AtomicInteger createCalls = new AtomicInteger();
        MinestomMenuRenderer renderer = new MinestomMenuRenderer((icon, amount) -> {
            createCalls.incrementAndGet();
            return ItemStack.of(Material.STONE, amount);
        });

        for (int index = 0; index <= 4_096; index++) {
            renderer.render(slot(0, "Item " + index, Map.of()));
        }
        renderer.render(slot(0, "Item 0", Map.of()));

        assertEquals(4_098, createCalls.get());
    }

    private static MenuSlot slot(int slot, Map<MenuClick, MenuInteraction> interactions) {
        return slot(slot, "Shared Visual", interactions);
    }

    private static MenuSlot slot(int slot, String title, Map<MenuClick, MenuInteraction> interactions) {
        return new MenuSlot(
                slot,
                MenuIcon.vanilla("stone"),
                Component.text(title),
                List.of(Component.text("Lore")),
                false,
                interactions);
    }
}
