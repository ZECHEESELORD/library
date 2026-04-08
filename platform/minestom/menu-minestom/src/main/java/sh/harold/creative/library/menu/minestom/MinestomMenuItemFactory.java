package sh.harold.creative.library.menu.minestom;

import net.minestom.server.item.ItemStack;
import sh.harold.creative.library.menu.MenuIcon;

@FunctionalInterface
interface MinestomMenuItemFactory {

    ItemStack create(MenuIcon icon, int amount);
}
