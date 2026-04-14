package sh.harold.creative.library.menu.fabric;

import net.kyori.adventure.text.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
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
import sh.harold.creative.library.menu.core.StandardMenuService;
import sh.harold.creative.library.sound.fabric.FabricServerSoundCuePlatform;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class FabricMenuPlatform implements AutoCloseable {

    private final MenuService menus;
    private final FabricMenuRuntime runtime;
    private final FabricServerSoundCuePlatform sounds;
    private final boolean closeSounds;
    private final MenuTraceController traceController;

    public FabricMenuPlatform() {
        this(new StandardMenuService(), new FabricServerSoundCuePlatform(), true);
    }

    public FabricMenuPlatform(MenuService menus) {
        this(menus, new FabricServerSoundCuePlatform(), true);
    }

    public FabricMenuPlatform(MenuService menus, FabricServerSoundCuePlatform sounds) {
        this(menus, sounds, false);
    }

    private FabricMenuPlatform(MenuService menus, FabricServerSoundCuePlatform sounds, boolean closeSounds) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.closeSounds = closeSounds;
        this.traceController = new MenuTraceController();
        this.runtime = new FabricMenuRuntime(new FabricMenuRenderer(), sounds, traceController, message -> { });
        FabricMenuGlobalBridge.register(runtime);
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

    public MenuButton.Builder button(ItemLike item) {
        return MenuButton.builder(icon(item));
    }

    public MenuButton.Builder button(ItemStack itemStack) {
        return applyExactPresentation(MenuButton.builder(FabricMenuIcons.fromItemStack(itemStack)), itemStack);
    }

    public MenuDisplayItem.Builder display(ItemLike item) {
        return MenuDisplayItem.builder(icon(item));
    }

    public MenuDisplayItem.Builder display(ItemStack itemStack) {
        return applyExactPresentation(MenuDisplayItem.builder(FabricMenuIcons.fromItemStack(itemStack)), itemStack);
    }

    public MenuStack.Builder stack(ItemLike item) {
        return MenuStack.builder(icon(item));
    }

    public MenuStack.Builder stack(ItemStack itemStack) {
        return applyExactPresentation(MenuStack.builder(FabricMenuIcons.fromItemStack(itemStack)), itemStack);
    }

    public MenuTab.Builder tab(String id, MenuIcon icon) {
        return MenuTab.builder(id, Objects.requireNonNull(icon, "icon"));
    }

    public MenuTab.Builder tab(String id, ItemLike item) {
        return MenuTab.builder(id, icon(item));
    }

    public MenuTab tab(String id, String name, ItemLike item, Iterable<? extends MenuItem> items) {
        return MenuTab.of(id, name, icon(item), items);
    }

    public MenuTab tab(String id, Component name, ItemLike item, Iterable<? extends MenuItem> items) {
        return MenuTab.of(id, name, icon(item), items);
    }

    public MenuTab tab(String id, String name, ItemLike item, java.util.function.Consumer<MenuTabContent.CanvasBuilder> consumer) {
        return MenuTab.canvas(id, name, icon(item), consumer);
    }

    public MenuTab tab(String id, Component name, ItemLike item, java.util.function.Consumer<MenuTabContent.CanvasBuilder> consumer) {
        return MenuTab.canvas(id, name, icon(item), consumer);
    }

    public void open(ServerPlayer player, MenuDefinition menu) {
        runtime.open(Objects.requireNonNull(player, "player"), Objects.requireNonNull(menu, "menu"));
    }

    public MenuTraceController trace() {
        return traceController;
    }

    @Override
    public void close() {
        runtime.close();
        FabricMenuGlobalBridge.unregister(runtime);
        if (closeSounds) {
            sounds.close();
        }
    }

    private static MenuIcon icon(ItemLike item) {
        return MenuIcon.vanilla(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                Objects.requireNonNull(item, "item").asItem()).toString());
    }

    private static MenuButton.Builder applyExactPresentation(MenuButton.Builder builder, ItemStack itemStack) {
        ItemStack stack = Objects.requireNonNull(itemStack, "itemStack");
        builder.literalItem();
        if (stack.getCustomName() != null) {
            builder.exactName(FabricMenuComponents.toAdventurePlain(stack.getCustomName()));
        } else {
            builder.name(stack.getHoverName().getString());
        }
        var lore = stack.get(DataComponents.LORE);
        builder.exactLore(lore == null ? List.of() : lore.lines().stream()
                .map(FabricMenuComponents::toAdventurePlain)
                .collect(Collectors.toList()));
        builder.glow(Boolean.TRUE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) || stack.isEnchanted());
        builder.amount(Math.max(1, stack.count()));
        return builder;
    }

    private static MenuDisplayItem.Builder applyExactPresentation(MenuDisplayItem.Builder builder, ItemStack itemStack) {
        ItemStack stack = Objects.requireNonNull(itemStack, "itemStack");
        builder.literalItem();
        if (stack.getCustomName() != null) {
            builder.exactName(FabricMenuComponents.toAdventurePlain(stack.getCustomName()));
        } else {
            builder.name(stack.getHoverName().getString());
        }
        var lore = stack.get(DataComponents.LORE);
        builder.exactLore(lore == null ? List.of() : lore.lines().stream()
                .map(FabricMenuComponents::toAdventurePlain)
                .collect(Collectors.toList()));
        builder.glow(Boolean.TRUE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) || stack.isEnchanted());
        builder.amount(Math.max(1, stack.count()));
        return builder;
    }

    private static MenuStack.Builder applyExactPresentation(MenuStack.Builder builder, ItemStack itemStack) {
        ItemStack stack = Objects.requireNonNull(itemStack, "itemStack");
        builder.literalItem();
        if (stack.getCustomName() != null) {
            builder.exactName(FabricMenuComponents.toAdventurePlain(stack.getCustomName()));
        } else {
            builder.name(stack.getHoverName().getString());
        }
        var lore = stack.get(DataComponents.LORE);
        builder.exactLore(lore == null ? List.of() : lore.lines().stream()
                .map(FabricMenuComponents::toAdventurePlain)
                .collect(Collectors.toList()));
        builder.glow(Boolean.TRUE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) || stack.isEnchanted());
        builder.amount(Math.max(1, stack.count()));
        return builder;
    }
}
