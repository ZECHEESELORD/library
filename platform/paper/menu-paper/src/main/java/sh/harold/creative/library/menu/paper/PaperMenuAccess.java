package sh.harold.creative.library.menu.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

interface PaperMenuAccess {

    Inventory createInventory(InventoryHolder holder, int size, Component title);

    void openInventory(Player player, Inventory inventory);

    void closeInventory(Player player);
}
