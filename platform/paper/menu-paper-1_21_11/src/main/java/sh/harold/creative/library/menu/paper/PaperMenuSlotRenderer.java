package sh.harold.creative.library.menu.paper;

import org.bukkit.inventory.ItemStack;
import sh.harold.creative.library.menu.MenuSlot;

interface PaperMenuSlotRenderer {

    ItemStack render(MenuSlot slot);
}
