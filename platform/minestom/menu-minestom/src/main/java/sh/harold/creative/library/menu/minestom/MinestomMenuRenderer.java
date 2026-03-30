package sh.harold.creative.library.menu.minestom;

import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import sh.harold.creative.library.menu.MenuSlot;

public final class MinestomMenuRenderer {

    public ItemStack render(MenuSlot slot) {
        Material material = Material.fromKey(slot.icon().key());
        if (material == null) {
            throw new IllegalArgumentException("Unknown Minestom material for menu icon: " + slot.icon().key());
        }

        return ItemStack.of(material)
                .withCustomName(slot.title())
                .withLore(slot.lore())
                .withGlowing(slot.glow())
                .withoutExtraTooltip();
    }
}
