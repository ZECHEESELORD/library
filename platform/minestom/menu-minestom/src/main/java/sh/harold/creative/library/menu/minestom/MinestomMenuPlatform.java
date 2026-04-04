package sh.harold.creative.library.menu.minestom;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.timer.TaskSchedule;
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
import java.util.UUID;
import java.util.function.Consumer;

public final class MinestomMenuPlatform implements AutoCloseable {

    private final MenuService menus;
    private final MinestomMenuRuntime runtime;
    private final EventNode<Event> parentNode;
    private final EventNode<Event> runtimeNode;
    private final SoundCueService sounds;
    private final boolean closeSounds;
    private final MenuTraceController traceController;

    public MinestomMenuPlatform() {
        this(new StandardMenuService(), MinecraftServer.getGlobalEventHandler(), defaultSounds(), true);
    }

    public MinestomMenuPlatform(MenuService menus) {
        this(menus, MinecraftServer.getGlobalEventHandler(), defaultSounds(), true);
    }

    public MinestomMenuPlatform(MenuService menus, EventNode<Event> parentNode) {
        this(menus, parentNode, defaultSounds(), true);
    }

    public MinestomMenuPlatform(MenuService menus, EventNode<Event> parentNode, SoundCueService sounds) {
        this(menus, parentNode, sounds, false);
    }

    private MinestomMenuPlatform(MenuService menus, EventNode<Event> parentNode, SoundCueService sounds, boolean closeSounds) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.parentNode = Objects.requireNonNull(parentNode, "parentNode");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.closeSounds = closeSounds;
        this.traceController = new MenuTraceController();
        this.runtime = new MinestomMenuRuntime(new MinestomMenuRenderer(), sounds, scheduleTicks(),
                traceController, MinestomMenuPlatform::logTrace);
        this.runtimeNode = runtime.createEventNode("menu-runtime-" + UUID.randomUUID());
        this.parentNode.addChild(runtimeNode);
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
        return MenuButton.builder(icon(itemStack));
    }

    public MenuDisplayItem.Builder display(Material material) {
        return MenuDisplayItem.builder(icon(material));
    }

    public MenuDisplayItem.Builder display(ItemStack itemStack) {
        return MenuDisplayItem.builder(icon(itemStack));
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
        parentNode.removeChild(runtimeNode);
        if (closeSounds) {
            sounds.close();
        }
    }

    private static SoundCueService defaultSounds() {
        return new StandardSoundCueService((delayTicks, action) -> {
            if (delayTicks < 0) {
                throw new IllegalArgumentException("delayTicks cannot be negative");
            }
            var task = MinecraftServer.getSchedulerManager().scheduleTask(
                    Objects.requireNonNull(action, "action"),
                    delayTicks == 0L ? TaskSchedule.immediate() : TaskSchedule.tick(Math.toIntExact(delayTicks)),
                    TaskSchedule.stop());
            return task::cancel;
        });
    }

    private static MenuIcon icon(Material material) {
        return MenuIcon.vanilla(Objects.requireNonNull(material, "material").key().asString());
    }

    private static MenuIcon icon(ItemStack itemStack) {
        return MinestomMenuIcons.fromItemStack(Objects.requireNonNull(itemStack, "itemStack"));
    }

    private static MenuTickScheduler scheduleTicks() {
        return (intervalTicks, action) -> {
            if (intervalTicks <= 0L) {
                throw new IllegalArgumentException("intervalTicks must be greater than zero");
            }
            var task = MinecraftServer.getSchedulerManager().scheduleTask(
                    Objects.requireNonNull(action, "action"),
                    TaskSchedule.tick(Math.toIntExact(intervalTicks)),
                    TaskSchedule.tick(Math.toIntExact(intervalTicks)));
            return task::cancel;
        };
    }

    private static void logTrace(String message) {
        System.out.println("[minestom-menu-trace] " + message);
    }
}
