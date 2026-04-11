package sh.harold.creative.library.menu.paper;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import java.util.Objects;

public final class PaperMenuInventories {

    private PaperMenuInventories() {
    }

    public static boolean isMenuInventory(Inventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        return inventory.getHolder() instanceof PaperMenuSession;
    }

    public static boolean isMenuView(InventoryView view) {
        Objects.requireNonNull(view, "view");
        return isMenuInventory(view.getTopInventory());
    }
}
