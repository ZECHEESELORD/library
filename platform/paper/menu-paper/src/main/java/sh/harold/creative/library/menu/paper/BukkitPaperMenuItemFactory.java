package sh.harold.creative.library.menu.paper;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

final class BukkitPaperMenuItemFactory implements PaperMenuItemFactory {

    @Override
    public ItemStack create(String key) {
        Material material = Material.matchMaterial(key);
        if (material == null) {
            throw new IllegalArgumentException("Unknown Paper material for menu icon: " + key);
        }
        return new ItemStack(material);
    }
}
