package sh.harold.creative.library.menu.minestom;

import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import sh.harold.creative.library.menu.MenuSlot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MinestomMenuRenderer {

    private final Map<MenuSlot, ItemStack> cache = new ConcurrentHashMap<>();

    public ItemStack render(MenuSlot slot) {
        return cache.computeIfAbsent(slot, MinestomMenuRenderer::createItem);
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
}
