package sh.harold.library.menu.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.library.menu.CanvasMenuBuilder;
import sh.harold.library.menu.ConfirmationMenuBuilder;
import sh.harold.library.menu.ListMenuBuilder;
import sh.harold.library.menu.MenuButton;
import sh.harold.library.menu.MenuDefinition;
import sh.harold.library.menu.MenuDisplayItem;
import sh.harold.library.menu.MenuIcon;
import sh.harold.library.menu.MenuItem;
import sh.harold.library.menu.MenuService;
import sh.harold.library.menu.MenuStack;
import sh.harold.library.menu.MenuTab;
import sh.harold.library.menu.MenuTabContent;
import sh.harold.library.menu.MenuTraceController;
import sh.harold.library.menu.ReactiveCanvasMenuBuilder;
import sh.harold.library.menu.ReactiveListMenuBuilder;
import sh.harold.library.menu.ReactiveMenuBuilder;
import sh.harold.library.menu.ReactiveTabsMenuBuilder;
import sh.harold.library.menu.TabsMenuBuilder;
import sh.harold.library.menu.core.StandardMenuService;
import sh.harold.library.sound.SoundTarget;
import sh.harold.library.sound.SoundCueService;
import sh.harold.library.sound.core.ScheduledCueTask;
import sh.harold.library.sound.core.SoundCueScheduler;
import sh.harold.library.sound.core.StandardSoundCueService;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class PaperMenuPlatform implements AutoCloseable {

    private final MenuService menus;
    private final JavaPlugin plugin;
    private final PaperMenuRuntime runtime;
    private final PaperMenuListener listener;
    private final SoundCueService sounds;
    private final boolean closeSounds;
    private final MenuTraceController traceController;
    private final AtomicBoolean closed = new AtomicBoolean();

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
        this.plugin = plugin;
        this.menus = Objects.requireNonNull(menus, "menus");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.closeSounds = closeSounds;
        this.traceController = new MenuTraceController();
        this.runtime = new PaperMenuRuntime(new BukkitPaperMenuAccess(), org.bukkit.Bukkit::getPlayer, new PaperMenuRenderer(), sounds,
                PaperMenuTaskScheduler.folia(plugin),
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

    public ConfirmationMenuBuilder confirmation() {
        return menus.confirmation();
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
        if (closed.get()) {
            throw new IllegalStateException("PaperMenuPlatform is closed");
        }
        Player viewer = Objects.requireNonNull(player, "player");
        MenuDefinition definition = Objects.requireNonNull(menu, "menu");
        boolean ownsRegion;
        try {
            ownsRegion = org.bukkit.Bukkit.isOwnedByCurrentRegion(viewer);
        } catch (RuntimeException ignored) {
            ownsRegion = false;
        }
        if (ownsRegion) {
            runtime.open(viewer, definition);
            return;
        }
        viewer.getScheduler().execute(
                plugin,
                () -> {
                    if (!closed.get()) {
                        runtime.open(viewer, definition);
                    }
                },
                null,
                1L);
    }

    public MenuTraceController trace() {
        return traceController;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        runtime.close();
        if (!runtime.requiresStaleInventoryGuard()) {
            HandlerList.unregisterAll(listener);
        }
        if (closeSounds) {
            sounds.close();
        }
    }

    private static SoundCueService defaultSounds(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return new StandardSoundCueService(new SoundCueScheduler() {
            @Override
            public ScheduledCueTask schedule(long delayTicks, Runnable action) {
                validateDelay(delayTicks, action);
                var scheduler = plugin.getServer().getGlobalRegionScheduler();
                var task = delayTicks == 0L
                        ? scheduler.run(plugin, ignored -> action.run())
                        : scheduler.runDelayed(plugin, ignored -> action.run(), delayTicks);
                return task::cancel;
            }

            @Override
            public ScheduledCueTask schedule(SoundTarget target, long delayTicks, Runnable action) {
                return schedule(target, delayTicks, action, () -> {
                });
            }

            @Override
            public ScheduledCueTask schedule(
                    SoundTarget target,
                    long delayTicks,
                    Runnable action,
                    Runnable onDiscard
            ) {
                validateDelay(delayTicks, action);
                Objects.requireNonNull(onDiscard, "onDiscard");
                if (target instanceof SoundTarget.AudienceTarget audience
                        && audience.audience() instanceof Player player) {
                    AtomicBoolean completed = new AtomicBoolean();
                    Runnable runAction = () -> {
                        if (completed.compareAndSet(false, true)) {
                            action.run();
                        }
                    };
                    Runnable discard = () -> {
                        if (completed.compareAndSet(false, true)) {
                            onDiscard.run();
                        }
                    };
                    var scheduler = player.getScheduler();
                    var task = delayTicks == 0L
                            ? scheduler.run(plugin, ignored -> runAction.run(), discard)
                            : scheduler.runDelayed(plugin, ignored -> runAction.run(), discard, delayTicks);
                    if (task == null) {
                        discard.run();
                        return () -> { };
                    }
                    return task::cancel;
                }
                return schedule(delayTicks, action);
            }
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

    private static void validateDelay(long delayTicks, Runnable action) {
        if (delayTicks < 0L) {
            throw new IllegalArgumentException("delayTicks cannot be negative");
        }
        Objects.requireNonNull(action, "action");
    }
}
