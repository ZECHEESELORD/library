package sh.harold.library.menu.paper;

import org.bukkit.inventory.ItemStack;
import sh.harold.library.menu.MenuIcon;

interface PaperMenuItemFactory {

    ItemStack create(MenuIcon icon);
}
