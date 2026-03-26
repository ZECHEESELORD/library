package sh.harold.creative.library.menu.paper;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import sh.harold.creative.library.menu.MenuSlot;

public final class PaperMenuRenderer {

    public ItemStack render(MenuSlot slot) {
        Material material = Material.matchMaterial(slot.icon().key());
        if (material == null) {
            throw new IllegalArgumentException("Unknown Paper material for menu icon: " + slot.icon().key());
        }

        ItemStack itemStack = new ItemStack(material);
        itemStack.editMeta(meta -> applyMeta(meta, slot));
        return itemStack;
    }

    private static void applyMeta(ItemMeta meta, MenuSlot slot) {
        meta.displayName(slot.title());
        meta.lore(slot.lore());
        meta.setEnchantmentGlintOverride(slot.glow() ? Boolean.TRUE : null);
    }
}
