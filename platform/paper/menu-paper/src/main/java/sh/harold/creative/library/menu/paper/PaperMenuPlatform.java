package sh.harold.creative.library.menu.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import sh.harold.creative.library.menu.CanvasMenuBuilder;
import sh.harold.creative.library.menu.ListMenuBuilder;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuDisplayItem;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuService;
import sh.harold.creative.library.menu.MenuTab;
import sh.harold.creative.library.menu.TabsMenuBuilder;
import sh.harold.creative.library.menu.core.StandardMenuService;

import java.util.Objects;

public final class PaperMenuPlatform {

    private final MenuService menus;

    public PaperMenuPlatform() {
        this(new StandardMenuService());
    }

    public PaperMenuPlatform(MenuService menus) {
        this.menus = Objects.requireNonNull(menus, "menus");
    }

    public ListMenuBuilder list() {
        return menus.list();
    }

    public TabsMenuBuilder tabs() {
        return menus.tabs();
    }

    public CanvasMenuBuilder canvas() {
        return menus.canvas();
    }

    public MenuButton.Builder button(Material material) {
        return MenuButton.builder(icon(material));
    }

    public MenuButton.Builder button(ItemStack itemStack) {
        return MenuButton.builder(icon(itemStack));
    }

    public MenuDisplayItem.Builder display(Material material) {
        return MenuDisplayItem.builder(icon(material));
    }

    public MenuDisplayItem.Builder display(ItemStack itemStack) {
        return MenuDisplayItem.builder(icon(itemStack));
    }

    public MenuTab tab(String id, String name, Material material, Iterable<? extends MenuItem> items) {
        return MenuTab.of(id, name, icon(material), items);
    }

    public MenuTab tab(String id, Component name, Material material, Iterable<? extends MenuItem> items) {
        return MenuTab.of(id, name, icon(material), items);
    }

    public void open(Player player, Menu menu) {
        throw new UnsupportedOperationException("Paper menu opening is not implemented yet");
    }

    private static MenuIcon icon(Material material) {
        return MenuIcon.vanilla(Objects.requireNonNull(material, "material").getKey().asString());
    }

    private static MenuIcon icon(ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack");
        return icon(itemStack.getType());
    }
}
