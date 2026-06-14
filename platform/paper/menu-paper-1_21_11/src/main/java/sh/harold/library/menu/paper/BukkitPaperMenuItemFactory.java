package sh.harold.library.menu.paper;

import org.bukkit.inventory.ItemStack;
import sh.harold.library.menu.MenuIcon;

final class BukkitPaperMenuItemFactory implements PaperMenuItemFactory {

    @Override
    public ItemStack create(MenuIcon icon) {
        return PaperMenuIcons.createItem(icon);
    }
}
