package sh.harold.creative.library.menu.paper;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class BukkitPaperMenuItemFactory implements PaperMenuItemFactory {

    private final Map<String, Material> materials = new ConcurrentHashMap<>();

    @Override
    public ItemStack create(String key) {
        Material material = materials.computeIfAbsent(key, BukkitPaperMenuItemFactory::resolveMaterial);
        return new ItemStack(material);
    }

    private static Material resolveMaterial(String key) {
        Material material = Material.matchMaterial(key);
        if (material == null) {
            throw new IllegalArgumentException("Unknown Paper material for menu icon: " + key);
        }
        return material;
    }
}
