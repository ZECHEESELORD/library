package sh.harold.library.menu.paper;

import org.bukkit.inventory.ItemStack;
import sh.harold.library.menu.MenuSlot;

interface PaperMenuSlotRenderer {

    ItemStack render(MenuSlot slot);
}
