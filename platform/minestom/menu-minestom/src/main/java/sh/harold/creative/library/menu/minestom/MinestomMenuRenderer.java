package sh.harold.creative.library.menu.minestom;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.core.MenuTrace;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MinestomMenuRenderer {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private final Map<MenuSlot, ItemStack> cache = new ConcurrentHashMap<>();

    public ItemStack render(MenuSlot slot) {
        ItemStack cached = cache.get(slot);
        if (cached != null) {
            return cached;
        }
        long started = System.nanoTime();
        ItemStack created = createItem(slot);
        long elapsed = System.nanoTime() - started;
        ItemStack previous = cache.putIfAbsent(slot, created);
        if (previous != null) {
            return previous;
        }
        MenuTrace.incrementCount("rendererCacheMisses");
        MenuTrace.addDuration("renderer.cacheMissBuild", elapsed);
        MenuTrace.detailIfSlow("renderer-cache-miss", elapsed,
                () -> "title=" + flatten(slot.title()) + " icon=" + slot.icon().key());
        return created;
    }

    private static ItemStack createItem(MenuSlot slot) {
        Material material = Material.fromKey(slot.icon().key());
        if (material == null) {
            throw new IllegalArgumentException("Unknown Minestom material for menu icon: " + slot.icon().key());
        }

        return ItemStack.of(material, slot.amount())
                .withCustomName(slot.title())
                .withLore(slot.lore())
                .withGlowing(slot.glow())
                .withoutExtraTooltip();
    }

    private static String flatten(Component component) {
        return PLAIN_TEXT.serialize(component);
    }
}
