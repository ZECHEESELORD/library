package sh.harold.creative.library.menu.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class BukkitPaperMenuAccess implements PaperMenuAccess {

    @Override
    public Inventory createInventory(InventoryHolder holder, int size, Component title) {
        return Bukkit.createInventory(holder, size, title);
    }

    @Override
    public void openInventory(Player player, Inventory inventory) {
        player.openInventory(inventory);
    }

    @Override
    public void closeInventory(Player player) {
        player.closeInventory();
    }

    @Override
    public Inventory topInventory(Player player) {
        return player.getOpenInventory().getTopInventory();
    }
}
