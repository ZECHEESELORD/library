package sh.harold.creative.library.menu.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.creative.library.menu.CanvasMenuBuilder;
import sh.harold.creative.library.menu.ListMenuBuilder;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuDisplayItem;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuService;
import sh.harold.creative.library.menu.MenuTab;
import sh.harold.creative.library.menu.MenuTabContent;
import sh.harold.creative.library.menu.TabsMenuBuilder;
import sh.harold.creative.library.menu.core.StandardMenuService;

import java.util.Objects;
import java.util.function.Consumer;

public final class PaperMenuPlatform implements AutoCloseable {

    private final MenuService menus;
    private final PaperMenuRuntime runtime;
    private final PaperMenuListener listener;

    public PaperMenuPlatform(JavaPlugin plugin) {
        this(plugin, new StandardMenuService());
    }

    public PaperMenuPlatform(JavaPlugin plugin, MenuService menus) {
        Objects.requireNonNull(plugin, "plugin");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.runtime = new PaperMenuRuntime(new BukkitPaperMenuAccess(), org.bukkit.Bukkit::getPlayer, new PaperMenuRenderer());
        this.listener = new PaperMenuListener(runtime);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
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

    public MenuTab tab(String id, String name, Material material, Consumer<MenuTabContent.CanvasBuilder> consumer) {
        return MenuTab.canvas(id, name, icon(material), consumer);
    }

    public MenuTab tab(String id, Component name, Material material, Consumer<MenuTabContent.CanvasBuilder> consumer) {
        return MenuTab.canvas(id, name, icon(material), consumer);
    }

    public void open(Player player, Menu menu) {
        runtime.open(Objects.requireNonNull(player, "player"), Objects.requireNonNull(menu, "menu"));
    }

    @Override
    public void close() {
        runtime.close();
        HandlerList.unregisterAll(listener);
    }

    private static MenuIcon icon(Material material) {
        return MenuIcon.vanilla(Objects.requireNonNull(material, "material").getKey().asString());
    }

    private static MenuIcon icon(ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack");
        return icon(itemStack.getType());
    }
}
