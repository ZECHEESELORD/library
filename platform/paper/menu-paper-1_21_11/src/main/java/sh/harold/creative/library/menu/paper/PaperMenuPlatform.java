package sh.harold.creative.library.menu.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.creative.library.menu.CanvasMenuBuilder;
import sh.harold.creative.library.menu.ListMenuBuilder;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuDefinition;
import sh.harold.creative.library.menu.MenuDisplayItem;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuService;
import sh.harold.creative.library.menu.MenuStack;
import sh.harold.creative.library.menu.MenuTab;
import sh.harold.creative.library.menu.MenuTabContent;
import sh.harold.creative.library.menu.MenuTraceController;
import sh.harold.creative.library.menu.ReactiveCanvasMenuBuilder;
import sh.harold.creative.library.menu.ReactiveListMenuBuilder;
import sh.harold.creative.library.menu.ReactiveMenuBuilder;
import sh.harold.creative.library.menu.ReactiveTabsMenuBuilder;
import sh.harold.creative.library.menu.TabsMenuBuilder;
import sh.harold.creative.library.menu.core.MenuTickScheduler;
import sh.harold.creative.library.menu.core.StandardMenuService;
import sh.harold.creative.library.sound.SoundCueService;
import sh.harold.creative.library.sound.core.StandardSoundCueService;

import java.util.Objects;
import java.util.function.Consumer;

public final class PaperMenuPlatform implements AutoCloseable {

    private final MenuService menus;
    private final PaperMenuRuntime runtime;
    private final PaperMenuListener listener;
    private final SoundCueService sounds;
    private final boolean closeSounds;
    private final MenuTraceController traceController;

    public PaperMenuPlatform(JavaPlugin plugin) {
        this(plugin, new StandardMenuService(), defaultSounds(plugin), true);
    }

    public PaperMenuPlatform(JavaPlugin plugin, MenuService menus) {
        this(plugin, menus, defaultSounds(plugin), true);
    }

    public PaperMenuPlatform(JavaPlugin plugin, MenuService menus, SoundCueService sounds) {
        this(plugin, menus, sounds, false);
    }

    private PaperMenuPlatform(JavaPlugin plugin, MenuService menus, SoundCueService sounds, boolean closeSounds) {
        Objects.requireNonNull(plugin, "plugin");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.closeSounds = closeSounds;
        this.traceController = new MenuTraceController();
        this.runtime = new PaperMenuRuntime(new BukkitPaperMenuAccess(), org.bukkit.Bukkit::getPlayer, new PaperMenuRenderer(), sounds,
                scheduleTicks(plugin), action -> plugin.getServer().getScheduler().runTask(plugin, action)::cancel,
                traceController, message -> plugin.getLogger().info("[paper-menu-trace] " + message));
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

    public ReactiveMenuBuilder<Void> reactive() {
        return menus.reactive();
    }

    public ReactiveCanvasMenuBuilder<Void> reactiveCanvas() {
        return menus.reactiveCanvas();
    }

    public ReactiveListMenuBuilder<Void> reactiveList() {
        return menus.reactiveList();
    }

    public ReactiveTabsMenuBuilder<Void> reactiveTabs() {
        return menus.reactiveTabs();
    }

    public MenuButton.Builder button(Material material) {
        return MenuButton.builder(icon(material));
    }

    public MenuButton.Builder button(ItemStack itemStack) {
        return applyExactPresentation(MenuButton.builder(icon(itemStack)), itemStack);
    }

    public MenuDisplayItem.Builder display(Material material) {
        return MenuDisplayItem.builder(icon(material));
    }

    public MenuDisplayItem.Builder display(ItemStack itemStack) {
        return applyExactPresentation(MenuDisplayItem.builder(icon(itemStack)), itemStack);
    }

    public MenuStack.Builder stack(Material material) {
        return MenuStack.builder(icon(material));
    }

    public MenuStack.Builder stack(ItemStack itemStack) {
        return MenuStack.builder(icon(itemStack));
    }

    public MenuTab.Builder tab(String id, MenuIcon icon) {
        return MenuTab.builder(id, Objects.requireNonNull(icon, "icon"));
    }

    public MenuTab.Builder tab(String id, Material material) {
        return MenuTab.builder(id, icon(material));
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

    public void open(Player player, MenuDefinition menu) {
        runtime.open(Objects.requireNonNull(player, "player"), Objects.requireNonNull(menu, "menu"));
    }

    public MenuTraceController trace() {
        return traceController;
    }

    @Override
    public void close() {
        runtime.close();
        HandlerList.unregisterAll(listener);
        if (closeSounds) {
            sounds.close();
        }
    }

    private static SoundCueService defaultSounds(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return new StandardSoundCueService((delayTicks, action) -> {
            if (delayTicks < 0) {
                throw new IllegalArgumentException("delayTicks cannot be negative");
            }
            var task = plugin.getServer().getScheduler().runTaskLater(plugin, Objects.requireNonNull(action, "action"), delayTicks);
            return task::cancel;
        });
    }

    private static MenuIcon icon(Material material) {
        return MenuIcon.vanilla(Objects.requireNonNull(material, "material").getKey().asString());
    }

    private static MenuIcon icon(ItemStack itemStack) {
        return PaperMenuIcons.fromItemStack(Objects.requireNonNull(itemStack, "itemStack"));
    }

    private static MenuButton.Builder applyExactPresentation(MenuButton.Builder builder, ItemStack itemStack) {
        ItemStack stack = Objects.requireNonNull(itemStack, "itemStack");
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.displayName() != null) {
            builder.exactName(meta.displayName());
        } else {
            builder.name(fallbackName(stack.getType()));
        }
        builder.exactLore(meta != null && meta.lore() != null ? meta.lore() : java.util.List.of());
        builder.glow(resolveGlow(stack, meta));
        builder.amount(Math.max(1, stack.getAmount()));
        return builder;
    }

    private static MenuDisplayItem.Builder applyExactPresentation(MenuDisplayItem.Builder builder, ItemStack itemStack) {
        ItemStack stack = Objects.requireNonNull(itemStack, "itemStack");
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.displayName() != null) {
            builder.exactName(meta.displayName());
        } else {
            builder.name(fallbackName(stack.getType()));
        }
        builder.exactLore(meta != null && meta.lore() != null ? meta.lore() : java.util.List.of());
        builder.glow(resolveGlow(stack, meta));
        builder.amount(Math.max(1, stack.getAmount()));
        return builder;
    }

    private static boolean resolveGlow(ItemStack itemStack, ItemMeta meta) {
        Boolean override = meta != null && meta.hasEnchantmentGlintOverride()
                ? meta.getEnchantmentGlintOverride()
                : null;
        if (override != null) {
            return override;
        }
        return !Objects.requireNonNull(itemStack, "itemStack").getEnchantments().isEmpty();
    }

    private static String fallbackName(Material material) {
        String[] parts = Objects.requireNonNull(material, "material").name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private static MenuTickScheduler scheduleTicks(JavaPlugin plugin) {
        return (intervalTicks, action) -> {
            if (intervalTicks <= 0L) {
                throw new IllegalArgumentException("intervalTicks must be greater than zero");
            }
            var task = plugin.getServer().getScheduler().runTaskTimer(plugin, Objects.requireNonNull(action, "action"),
                    intervalTicks, intervalTicks);
            return task::cancel;
        };
    }
}
