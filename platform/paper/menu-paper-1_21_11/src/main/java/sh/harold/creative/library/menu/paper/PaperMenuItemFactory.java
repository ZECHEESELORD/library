package sh.harold.creative.library.menu.paper;

import org.bukkit.inventory.ItemStack;
import sh.harold.creative.library.menu.MenuIcon;

interface PaperMenuItemFactory {

    ItemStack create(MenuIcon icon);
}
