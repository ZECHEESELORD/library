package sh.harold.creative.library.menu.minestom;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.click.Click;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuDisplayItem;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuStack;
import sh.harold.creative.library.menu.MenuTab;
import sh.harold.creative.library.menu.MenuTraceController;
import sh.harold.creative.library.menu.ReactiveMenu;
import sh.harold.creative.library.menu.ReactiveMenuInput;
import sh.harold.creative.library.menu.ReactiveMenuResult;
import sh.harold.creative.library.menu.ReactiveMenuView;
import sh.harold.creative.library.menu.core.StandardMenuService;
import sh.harold.creative.library.sound.CuePlayback;
import sh.harold.creative.library.sound.SoundCue;
import sh.harold.creative.library.sound.SoundCueKeys;
import sh.harold.creative.library.sound.SoundCuePacks;
import sh.harold.creative.library.sound.SoundCueRegistry;
import sh.harold.creative.library.sound.SoundCueService;
import sh.harold.creative.library.sound.core.StandardSoundCueRegistry;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinestomMenuRuntimeTest {

    private static boolean serverInitialized;
    private static final Key SPECIAL_SOUND = Key.key("test", "menu/special");

    @BeforeAll
    static void initServer() {
        if (!serverInitialized) {
            MinecraftServer.init();
            serverInitialized = true;
        }
    }

    @Test
    void openClickNavigateAndCloseUsesOwnedInventoryIdentity() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer(), new RecordingSoundCueService());
        Menu menu = pagedMenu();

        runtime.open(player, menu);

        Inventory inventory = player.lastOpenedInventory();

        assertEquals("Close", slotTitle(inventory, 49));
        assertEquals("Next Page", slotTitle(inventory, 53));
        assertEquals(List.of("Page 2"), slotLore(inventory, 53));

        InventoryPreClickEvent nextPage = new InventoryPreClickEvent(inventory, player, new Click.Left(53));
        runtime.onInventoryPreClick(nextPage);

        assertTrue(nextPage.isCancelled());
        Inventory secondPageInventory = player.lastOpenedInventory();
        assertNotSame(inventory, secondPageInventory);
        assertEquals("Previous Page", slotTitle(secondPageInventory, 45));
        assertEquals(List.of("Page 1"), slotLore(secondPageInventory, 45));
        assertEquals("Close", slotTitle(secondPageInventory, 49));
        assertEquals("Next Page", slotTitle(secondPageInventory, 53));
        assertEquals(List.of("Page 3"), slotLore(secondPageInventory, 53));

        InventoryPreClickEvent close = new InventoryPreClickEvent(secondPageInventory, player, new Click.Left(49));
        runtime.onInventoryPreClick(close);
        assertTrue(close.isCancelled());
        assertEquals(1, player.closeCount());
    }

    @Test
    void actionCanReplaceCurrentMenuAndRefreshRenderedContents() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer(), new RecordingSoundCueService());
        AtomicBoolean enabled = new AtomicBoolean(false);

        runtime.open(player, toggleMenu(enabled));
        Inventory inventory = player.lastOpenedInventory();
        assertEquals("Disabled", slotTitle(inventory, 10));

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(10)));

        assertEquals("Enabled", slotTitle(inventory, 10));
        assertEquals(1, player.openCount());
    }

    @Test
    void closeAndSpoofedInventoriesDoNotRouteByTitle() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer(), new RecordingSoundCueService());
        runtime.open(player, pagedMenu());
        Inventory inventory = player.lastOpenedInventory();

        Inventory spoofedInventory = new Inventory(inventory.getInventoryType(), Component.text("Profiles"));
        InventoryPreClickEvent spoofedClick = new InventoryPreClickEvent(spoofedInventory, player, new Click.Left(53));
        runtime.onInventoryPreClick(spoofedClick);
        assertTrue(spoofedClick.isCancelled());
        assertEquals(1, player.openCount());

        runtime.onInventoryClose(new InventoryCloseEvent(inventory, player, true));

        InventoryPreClickEvent staleClick = new InventoryPreClickEvent(inventory, player, new Click.Left(53));
        runtime.onInventoryPreClick(staleClick);
        assertFalse(staleClick.isCancelled());
    }

    @Test
    void disconnectCleansUpViewerSession() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer(), new RecordingSoundCueService());
        runtime.open(player, pagedMenu());
        Inventory inventory = player.lastOpenedInventory();

        runtime.onPlayerDisconnect(new PlayerDisconnectEvent(player));

        InventoryPreClickEvent click = new InventoryPreClickEvent(inventory, player, new Click.Left(53));
        runtime.onInventoryPreClick(click);
        assertFalse(click.isCancelled());
    }

    @Test
    void childBackUsesHistoryAndTabSwitchesPushFrameHistory() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer(), new RecordingSoundCueService());

        runtime.open(player, launcherMenu());
        Inventory rootInventory = player.lastOpenedInventory();
        assertEquals("Open Gallery", slotTitle(rootInventory, 10));

        InventoryPreClickEvent openChild = new InventoryPreClickEvent(rootInventory, player, new Click.Left(10));
        runtime.onInventoryPreClick(openChild);

        assertTrue(openChild.isCancelled());
        Inventory inventory = player.lastOpenedInventory();

        assertEquals("Go Back", slotTitle(inventory, 48));
        assertEquals("Profiles", slotTitle(inventory, 3));
        assertEquals("Progress", slotTitle(inventory, 4));
        assertEquals("Your SkyBlock Profile", slotTitle(inventory, 19));

        InventoryPreClickEvent switchTab = new InventoryPreClickEvent(inventory, player, new Click.Left(4));
        runtime.onInventoryPreClick(switchTab);

        assertTrue(switchTab.isCancelled());
        assertEquals("Profiles", slotTitle(inventory, 3));
        assertEquals("Progress", slotTitle(inventory, 4));
        assertEquals("Farming XLIX", slotTitle(inventory, 19));

        InventoryPreClickEvent backToPreviousFrame = new InventoryPreClickEvent(inventory, player, new Click.Left(48));
        runtime.onInventoryPreClick(backToPreviousFrame);

        assertTrue(backToPreviousFrame.isCancelled());
        Inventory afterFrameBack = player.lastOpenedInventory();
        assertEquals("Go Back", slotTitle(afterFrameBack, 48));
        assertEquals("Your SkyBlock Profile", slotTitle(afterFrameBack, 19));

        InventoryPreClickEvent backToRoot = new InventoryPreClickEvent(afterFrameBack, player, new Click.Left(48));
        runtime.onInventoryPreClick(backToRoot);

        assertTrue(backToRoot.isCancelled());
        Inventory finalInventory = player.lastOpenedInventory();
        assertEquals("Open Gallery", slotTitle(finalInventory, 10));
    }

    @Test
    void navArrowsScrollStripWithoutChangingActiveContent() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer(), new RecordingSoundCueService());

        runtime.open(player, overflowGalleryMenu());
        Inventory inventory = player.lastOpenedInventory();

        assertEquals("Previous Tab", slotTitle(inventory, 0));
        assertEquals(List.of("Page 1"), slotLore(inventory, 0));
        assertEquals("Next Tab", slotTitle(inventory, 8));
        assertEquals(List.of("Page 2"), slotLore(inventory, 8));
        assertEquals("Tab 0", slotTitle(inventory, 1));
        assertEquals("Tab 6", slotTitle(inventory, 7));
        assertEquals("Tab 0 Item 0", slotTitle(inventory, 19));

        InventoryPreClickEvent scrollRight = new InventoryPreClickEvent(inventory, player, new Click.Left(8));
        runtime.onInventoryPreClick(scrollRight);

        assertTrue(scrollRight.isCancelled());
        assertEquals("Tab 1", slotTitle(inventory, 1));
        assertEquals("Tab 7", slotTitle(inventory, 7));
        assertEquals("Tab 0 Item 0", slotTitle(inventory, 19));

        InventoryPreClickEvent jumpEnd = new InventoryPreClickEvent(inventory, player, new Click.Right(8));
        runtime.onInventoryPreClick(jumpEnd);

        assertTrue(jumpEnd.isCancelled());
        assertEquals("Tab 3", slotTitle(inventory, 1));
        assertEquals("Tab 9", slotTitle(inventory, 7));
        assertEquals("Tab 0 Item 0", slotTitle(inventory, 19));

        InventoryPreClickEvent switchTab = new InventoryPreClickEvent(inventory, player, new Click.Left(7));
        runtime.onInventoryPreClick(switchTab);

        assertTrue(switchTab.isCancelled());
        assertEquals("Tab 9 Item 0", slotTitle(inventory, 19));
    }

    @Test
    void pagedTabContentUsesFooterArrowsForLargeTabs() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer(), new RecordingSoundCueService());

        runtime.open(player, pagedTabGalleryMenu());
        Inventory inventory = player.lastOpenedInventory();

        assertEquals("Profile Item 0", slotTitle(inventory, 19));
        assertEquals("Next Page", slotTitle(inventory, 53));
        assertEquals(List.of("Page 2"), slotLore(inventory, 53));

        InventoryPreClickEvent nextPage = new InventoryPreClickEvent(inventory, player, new Click.Left(53));
        runtime.onInventoryPreClick(nextPage);

        assertTrue(nextPage.isCancelled());
        assertEquals("Previous Page", slotTitle(inventory, 45));
        assertEquals(List.of("Page 1"), slotLore(inventory, 45));
        assertEquals("Profile Item 21", slotTitle(inventory, 19));
        assertEquals("Profile Item 28", slotTitle(inventory, 28));
    }

    @Test
    void canvasRoutesPlacedItemsThroughOwnedInventoryIdentity() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer(), new RecordingSoundCueService());
        AtomicBoolean opened = new AtomicBoolean(false);

        runtime.open(player, canvasMenu(opened));
        Inventory inventory = player.lastOpenedInventory();

        assertEquals("Museum Rewards", slotTitle(inventory, 13));

        InventoryPreClickEvent open = new InventoryPreClickEvent(inventory, player, new Click.Left(13));
        runtime.onInventoryPreClick(open);

        assertTrue(open.isCancelled());
        assertTrue(opened.get());
    }

    @Test
    void interactionSoundsUseDefaultAndOverrideMappings() {
        TestPlayer player = player();
        RecordingSoundCueService sounds = new RecordingSoundCueService();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer(), sounds);

        runtime.open(player, soundMenu());
        Inventory inventory = player.lastOpenedInventory();

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(10)));
        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(11)));
        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(12)));

        runtime.open(player, pagedMenu());
        Inventory pagedInventory = player.lastOpenedInventory();
        runtime.onInventoryPreClick(new InventoryPreClickEvent(pagedInventory, player, new Click.Left(53)));

        assertEquals(List.of(
                SoundCueKeys.MENU_CLICK,
                SoundCueKeys.RESULT_CONFIRM,
                SPECIAL_SOUND,
                SoundCueKeys.MENU_SCROLL
        ), sounds.playedKeys());
    }

    @Test
    void reactiveMenusCanMirrorBottomInventoryClicks() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer(), new RecordingSoundCueService());

        runtime.open(player, reactiveInventoryMirrorMenu());
        Inventory inventory = player.lastOpenedInventory();
        player.getInventory().setItemStack(0, namedMinestomItem(Material.STONE, "Bottom Item", 3));

        InventoryPreClickEvent click = new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(0));

        runtime.onInventoryPreClick(click);

        assertTrue(click.isCancelled());
        assertEquals("Bottom Item", slotTitle(inventory, 31));
    }

    @Test
    void reactiveMenusCanMirrorDraggedCursorStacks() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer(), new RecordingSoundCueService());

        runtime.open(player, reactiveDragMirrorMenu());
        Inventory inventory = player.lastOpenedInventory();
        player.getInventory().setCursorItem(namedMinestomItem(Material.EMERALD, "Cursor Item", 2));

        InventoryPreClickEvent drag = new InventoryPreClickEvent(inventory, player, new Click.LeftDrag(List.of(31)));

        runtime.onInventoryPreClick(drag);

        assertTrue(drag.isCancelled());
        assertEquals("Cursor Item", slotTitle(inventory, 31));
    }

    @Test
    void reactiveMenusIgnoreInertBaseChromeClicks() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer(), new RecordingSoundCueService());

        runtime.open(player, reactiveClickRoutingMenu());
        Inventory inventory = player.lastOpenedInventory();
        assertEquals("Placed Clicks: 0", slotTitle(inventory, 22));

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(0)));
        assertEquals("Placed Clicks: 0", slotTitle(inventory, 22));

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(22)));
        assertEquals("Placed Clicks: 1", slotTitle(inventory, 22));
    }

    @Test
    void traceLogsReactiveOpenAndClickSummariesWhenEnabled() {
        TestPlayer player = player();
        MenuTraceController trace = new MenuTraceController();
        trace.traceAll();
        List<String> logs = new ArrayList<>();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer(), new RecordingSoundCueService(),
                sh.harold.creative.library.menu.core.MenuTickScheduler.unsupported(), trace, logs::add);

        runtime.open(player, reactiveClickRoutingMenu());

        String openSummary = summaryLine(logs, "open");
        assertTrue(openSummary.contains("host=\"minestom\""));
        assertTrue(openSummary.contains("menu=\"Reactive Routing\""));
        assertTrue(openSummary.contains("placementCount=\"1\""));
        assertTrue(openSummary.contains("changedSlots="));
        assertTrue(openSummary.contains("runtime.inventoryPatch="));

        logs.clear();
        Inventory inventory = player.lastOpenedInventory();
        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(22)));

        String clickSummary = summaryLine(logs, "click");
        assertTrue(clickSummary.contains("menu=\"Reactive Routing\""));
        assertTrue(clickSummary.contains("button=\"LEFT\""));
        assertTrue(clickSummary.contains("runtime.reactiveDispatch="));
    }

    @Test
    void traceFiltersByMenuTitle() {
        TestPlayer player = player();
        MenuTraceController trace = new MenuTraceController();
        trace.traceMenuTitles(List.of("Reactive Routing"));
        List<String> logs = new ArrayList<>();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer(), new RecordingSoundCueService(),
                sh.harold.creative.library.menu.core.MenuTickScheduler.unsupported(), trace, logs::add);

        runtime.open(player, pagedMenu());
        assertTrue(logs.isEmpty());

        runtime.open(player, reactiveClickRoutingMenu());
        assertTrue(logs.stream().anyMatch(line -> line.startsWith("summary ") && line.contains("menu=\"Reactive Routing\"")));
    }

    @Test
    void inertCompiledChromeClicksDoNotEmitTraceSummaries() {
        TestPlayer player = player();
        MenuTraceController trace = new MenuTraceController();
        trace.traceAll();
        List<String> logs = new ArrayList<>();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(new MinestomMenuRenderer(), new RecordingSoundCueService(),
                sh.harold.creative.library.menu.core.MenuTickScheduler.unsupported(), trace, logs::add);

        runtime.open(player, overflowGalleryMenu());
        Inventory inventory = player.lastOpenedInventory();
        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Right(8)));

        assertEquals("Tab 3", slotTitle(inventory, 1));
        assertEquals("Tab 0 Item 0", slotTitle(inventory, 19));

        logs.clear();
        InventoryPreClickEvent inert = new InventoryPreClickEvent(inventory, player, new Click.Left(8));
        runtime.onInventoryPreClick(inert);

        assertTrue(inert.isCancelled());
        assertTrue(logs.isEmpty());
        assertEquals("Tab 3", slotTitle(inventory, 1));
        assertEquals("Tab 0 Item 0", slotTitle(inventory, 19));
    }

    private static Menu pagedMenu() {
        return new StandardMenuService().list()
                .title("Profiles")
                .addItems(IntStream.range(0, 73).mapToObj(i -> MenuButton.builder(MenuIcon.vanilla("stone"))
                        .name("Item " + i)
                        .action(ActionVerb.VIEW, context -> { })
                        .build()).toList())
                .build();
    }

    private static Menu toggleMenu(AtomicBoolean enabled) {
        return new StandardMenuService().list()
                .title("Toggle")
                .addItem(MenuButton.builder(MenuIcon.vanilla("lever"))
                        .name(enabled.get() ? "Enabled" : "Disabled")
                        .action(ActionVerb.TOGGLE, context -> {
                            enabled.set(!enabled.get());
                            context.open(toggleMenu(enabled));
                        })
                        .build())
                .build();
    }

    private static Menu launcherMenu() {
        return new StandardMenuService().list()
                .title("Launcher")
                .addItem(MenuButton.builder(MenuIcon.vanilla("stone"))
                        .name("Open Gallery")
                        .action(ActionVerb.OPEN, context -> context.open(galleryMenu()))
                        .build())
                .build();
    }

    private static Menu galleryMenu() {
        return new StandardMenuService().tabs()
                .title("Gallery")
                .defaultTab("profiles")
                .addTab(MenuTab.of("profiles", "Profiles", MenuIcon.vanilla("player_head"), List.of(
                        MenuButton.builder(MenuIcon.vanilla("player_head"))
                                .name("Your SkyBlock Profile")
                                .action(ActionVerb.VIEW, context -> { })
                                .build(),
                        MenuButton.builder(MenuIcon.vanilla("gray_dye"))
                                .name("Profile Slot #5")
                                .action(ActionVerb.OPEN, context -> { })
                                .build()
                )))
                .addTab(MenuTab.of("progress", "Progress", MenuIcon.vanilla("experience_bottle"), List.of(
                        MenuButton.builder(MenuIcon.vanilla("golden_hoe"))
                                .name("Farming XLIX")
                                .action(ActionVerb.VIEW, context -> { })
                                .build(),
                        MenuButton.builder(MenuIcon.vanilla("book"))
                                .name("Museum Rewards")
                                .action(ActionVerb.VIEW, context -> { })
                                .build()
                )))
                .build();
    }

    private static Menu overflowGalleryMenu() {
        StandardMenuService menus = new StandardMenuService();
        var builder = menus.tabs()
                .title("Overflow")
                .defaultTab("tab-0");
        for (int i = 0; i < 10; i++) {
            int index = i;
            builder.addTab(MenuTab.of("tab-" + index, "Tab " + index, MenuIcon.vanilla("stone"), List.of(
                    MenuButton.builder(MenuIcon.vanilla("stone"))
                            .name("Tab " + index + " Item 0")
                            .action(ActionVerb.VIEW, context -> { })
                            .build()
            )));
        }
        return builder.build();
    }

    private static Menu pagedTabGalleryMenu() {
        return new StandardMenuService().tabs()
                .title("Paged Tabs")
                .defaultTab("profiles")
                .addTab(MenuTab.of("profiles", "Profiles", MenuIcon.vanilla("player_head"),
                        IntStream.range(0, 29)
                                .mapToObj(i -> MenuButton.builder(MenuIcon.vanilla("player_head"))
                                        .name("Profile Item " + i)
                                        .action(ActionVerb.VIEW, context -> { })
                                        .build())
                                .toList()))
                .addTab(MenuTab.of("progress", "Progress", MenuIcon.vanilla("experience_bottle"), List.of(
                        MenuButton.builder(MenuIcon.vanilla("golden_hoe"))
                                .name("Farming XLIX")
                                .action(ActionVerb.VIEW, context -> { })
                                .build()
                )))
                .build();
    }

    private static Menu canvasMenu(AtomicBoolean opened) {
        return new StandardMenuService().canvas()
                .title("Canvas")
                .place(13, MenuButton.builder(MenuIcon.vanilla("book"))
                        .name("Museum Rewards")
                        .action(ActionVerb.VIEW, context -> opened.set(true))
                        .build())
                .build();
    }

    private static Menu soundMenu() {
        return new StandardMenuService().list()
                .title("Sounds")
                .addItem(MenuButton.builder(MenuIcon.vanilla("stone"))
                        .name("View Profile")
                        .action(ActionVerb.VIEW, context -> { })
                        .build())
                .addItem(MenuButton.builder(MenuIcon.vanilla("chest"))
                        .name("Claim Delivery")
                        .action(ActionVerb.CLAIM, context -> { })
                        .build())
                .addItem(MenuButton.builder(MenuIcon.vanilla("gray_dye"))
                        .name("Unavailable")
                        .action(ActionVerb.OPEN, context -> { })
                        .sound(SPECIAL_SOUND)
                        .build())
                .build();
    }

    private static ReactiveMenu reactiveInventoryMirrorMenu() {
        return new StandardMenuService().reactive()
                .state(new StoredState(null))
                .render(state -> ReactiveMenuView.builder("Reactive Mirror")
                        .place(13, MenuDisplayItem.builder(MenuIcon.vanilla("hopper"))
                                .name("Inventory Click Demo")
                                .description("Bottom-inventory clicks should patch the center slot.")
                                .build())
                        .place(31, state.stored() != null
                                ? state.stored()
                                : MenuDisplayItem.builder(MenuIcon.vanilla("barrel"))
                                        .name("Input Slot")
                                        .description("Click an item in the bottom inventory to mirror it here.")
                                        .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.InventoryClick click && click.item() != null) {
                        return ReactiveMenuResult.stay(new StoredState(click.item()));
                    }
                    return ReactiveMenuResult.stay(state);
                })
                .build();
    }

    private static ReactiveMenu reactiveDragMirrorMenu() {
        return new StandardMenuService().reactive()
                .state(new StoredState(null))
                .render(state -> ReactiveMenuView.builder("Reactive Drag")
                        .place(31, state.stored() != null
                                ? state.stored()
                                : MenuDisplayItem.builder(MenuIcon.vanilla("barrel"))
                                        .name("Drop Target")
                                        .description("Dragging a cursor stack across slot 31 should mirror it here.")
                                        .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.Drag drag
                            && drag.cursor() != null
                            && drag.slots().contains(31)) {
                        return ReactiveMenuResult.stay(new StoredState(drag.cursor()));
                    }
                    return ReactiveMenuResult.stay(state);
                })
                .build();
    }

    private static ReactiveMenu reactiveClickRoutingMenu() {
        return new StandardMenuService().reactive()
                .state(new StoredState(null))
                .render(state -> ReactiveMenuView.builder("Reactive Routing")
                        .place(22, MenuDisplayItem.builder(MenuIcon.vanilla("stone"))
                                .name("Placed Clicks: " + (state.stored() == null ? 0 : state.stored().amount()))
                                .description("Only the authored slot should reach the reducer.")
                                .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.Click click && click.slot() == 22) {
                        int nextCount = state.stored() == null ? 1 : state.stored().amount() + 1;
                        MenuStack counter = MenuStack.builder(MenuIcon.vanilla("stone"))
                                .name("Count " + nextCount)
                                .amount(nextCount)
                                .build();
                        return ReactiveMenuResult.stay(new StoredState(counter));
                    }
                    return ReactiveMenuResult.stay(state);
                })
                .build();
    }

    private static TestPlayer player() {
        return new TestPlayer(UUID.randomUUID());
    }

    private static ItemStack namedMinestomItem(Material material, String name, int amount) {
        return ItemStack.of(material, amount)
                .withCustomName(Component.text(name))
                .withoutExtraTooltip();
    }

    private static String slotTitle(Inventory inventory, int slot) {
        return flatten(inventory.getItemStack(slot).get(DataComponents.CUSTOM_NAME));
    }

    private static List<String> slotLore(Inventory inventory, int slot) {
        var lore = inventory.getItemStack(slot).get(DataComponents.LORE);
        if (lore == null) {
            return List.of();
        }
        return lore.stream().map(MinestomMenuRuntimeTest::flatten).toList();
    }

    private static String flatten(Component component) {
        StringBuilder builder = new StringBuilder();
        append(builder, component);
        return builder.toString();
    }

    private static void append(StringBuilder builder, Component component) {
        if (component instanceof TextComponent textComponent) {
            builder.append(textComponent.content());
        }
        for (Component child : component.children()) {
            append(builder, child);
        }
    }

    private static String summaryLine(List<String> logs, String cause) {
        return logs.stream()
                .filter(line -> line.startsWith("summary "))
                .filter(line -> line.contains("cause=\"" + cause + "\""))
                .findFirst()
                .orElseThrow();
    }

    private static final class TestPlayer extends Player {

        private final List<Inventory> openedInventories = new ArrayList<>();
        private Inventory openInventory;
        private int closeCount;

        private TestPlayer(UUID uuid) {
            super(new TestPlayerConnection(), new GameProfile(uuid, "menu-test"));
        }

        @Override
        public boolean openInventory(Inventory inventory) {
            openedInventories.add(inventory);
            openInventory = inventory;
            return true;
        }

        @Override
        public void closeInventory() {
            openInventory = null;
            closeCount++;
        }

        @Override
        public net.minestom.server.inventory.AbstractInventory getOpenInventory() {
            return openInventory;
        }

        private Inventory lastOpenedInventory() {
            return openedInventories.getLast();
        }

        private int openCount() {
            return openedInventories.size();
        }

        private int closeCount() {
            return closeCount;
        }
    }

    private record StoredState(MenuStack stored) {
    }

    private static final class RecordingSoundCueService implements SoundCueService {

        private final StandardSoundCueRegistry registry = new StandardSoundCueRegistry();
        private final IdentityHashMap<SoundCue, Key> keysByCue = new IdentityHashMap<>();
        private final List<Key> playedKeys = new ArrayList<>();

        private RecordingSoundCueService() {
            register(SoundCueKeys.NAMESPACE, SoundCueKeys.MENU_CLICK, "test:menu_click");
            register(SoundCueKeys.NAMESPACE, SoundCueKeys.MENU_SCROLL, "test:menu_scroll");
            register(SoundCueKeys.NAMESPACE, SoundCueKeys.RESULT_CONFIRM, "test:result_confirm");
            register(SoundCueKeys.NAMESPACE, SoundCueKeys.RESULT_DENY, "test:result_deny");
            register("test", SPECIAL_SOUND, "test:menu_special");
        }

        @Override
        public SoundCueRegistry registry() {
            return registry;
        }

        @Override
        public CuePlayback play(Audience audience, SoundCue cue) {
            playedKeys.add(keysByCue.get(cue));
            return CuePlayback.noop();
        }

        @Override
        public void close() {
        }

        private List<Key> playedKeys() {
            return List.copyOf(playedKeys);
        }

        private void register(String namespace, Key key, String soundKey) {
            SoundCue cue = sh.harold.creative.library.sound.SoundCues.sound(soundKey, 0.5f, 1.0f);
            keysByCue.put(cue, key);
            registry.register(SoundCuePacks.pack(namespace).cue(key, cue).build());
        }
    }

    private static final class TestPlayerConnection extends PlayerConnection {

        @Override
        public void sendPacket(SendablePacket packet) {
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 25565);
        }
    }
}
