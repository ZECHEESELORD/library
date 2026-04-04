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
import net.minestom.server.network.player.ResolvableProfile;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.AbstractInventory;
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
import sh.harold.creative.library.menu.MenuTabGroup;
import sh.harold.creative.library.menu.MenuTraceController;
import sh.harold.creative.library.menu.ReactiveGeometryAction;
import sh.harold.creative.library.menu.ReactiveListView;
import sh.harold.creative.library.menu.ReactiveMenu;
import sh.harold.creative.library.menu.ReactiveMenuEffect;
import sh.harold.creative.library.menu.ReactiveMenuInput;
import sh.harold.creative.library.menu.ReactiveMenuResult;
import sh.harold.creative.library.menu.ReactiveTabsView;
import sh.harold.creative.library.menu.ReactiveMenuView;
import sh.harold.creative.library.menu.core.StandardMenuService;
import sh.harold.creative.library.menu.core.MenuTickHandle;
import sh.harold.creative.library.sound.CuePlayback;
import sh.harold.creative.library.sound.SoundCue;
import sh.harold.creative.library.sound.SoundCueKeys;
import sh.harold.creative.library.sound.SoundCuePacks;
import sh.harold.creative.library.sound.SoundCueRegistry;
import sh.harold.creative.library.sound.SoundCueService;
import sh.harold.creative.library.sound.core.StandardSoundCueRegistry;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
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

    private static MinestomMenuRuntime runtime() {
        return runtime(new RecordingSoundCueService());
    }

    private static MinestomMenuRuntime runtime(SoundCueService sounds) {
        return runtime(sounds, new ArrayDeque<>());
    }

    private static MinestomMenuRuntime runtime(SoundCueService sounds, Deque<Runnable> scheduled) {
        return new MinestomMenuRuntime(
                new MinestomMenuRenderer(),
                sounds,
                sh.harold.creative.library.menu.core.MenuTickScheduler.unsupported(),
                nextTickScheduler(scheduled),
                new MenuTraceController(),
                message -> { });
    }

    private static MinestomMenuRuntime runtime(SoundCueService sounds, MenuTraceController trace, List<String> logs,
                                               Deque<Runnable> scheduled) {
        return new MinestomMenuRuntime(
                new MinestomMenuRenderer(),
                sounds,
                sh.harold.creative.library.menu.core.MenuTickScheduler.unsupported(),
                nextTickScheduler(scheduled),
                trace,
                logs::add);
    }

    private static Function<Runnable, MenuTickHandle> nextTickScheduler(Deque<Runnable> scheduled) {
        return action -> {
            scheduled.addLast(action);
            return () -> scheduled.remove(action);
        };
    }

    private static void drainScheduled(Deque<Runnable> scheduled) {
        while (!scheduled.isEmpty()) {
            scheduled.removeFirst().run();
        }
    }

    private static InventoryPreClickEvent click(Player player, Inventory inventory, Click click) {
        return new InventoryPreClickEvent(inventory, player, click);
    }

    @Test
    void openClickNavigateAndCloseUsesOwnedInventoryIdentity() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        Menu menu = pagedMenu();

        runtime.open(player, menu);

        Inventory inventory = player.lastOpenedInventory();

        assertEquals("Close", slotTitle(inventory, 49));
        assertEquals("Next Page", slotTitle(inventory, 53));
        assertEquals(List.of("Page 2"), slotLore(inventory, 53));

        InventoryPreClickEvent nextPage = new InventoryPreClickEvent(inventory, player, new Click.Left(53));
        runtime.onInventoryPreClick(nextPage);

        assertTrue(nextPage.isCancelled());
        drainScheduled(scheduled);
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
        MinestomMenuRuntime runtime = runtime();
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
        MinestomMenuRuntime runtime = runtime();
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
        MinestomMenuRuntime runtime = runtime();
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
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);

        runtime.open(player, launcherMenu());
        Inventory rootInventory = player.lastOpenedInventory();
        assertEquals("Open Gallery", slotTitle(rootInventory, 10));

        InventoryPreClickEvent openChild = new InventoryPreClickEvent(rootInventory, player, new Click.Left(10));
        runtime.onInventoryPreClick(openChild);

        assertTrue(openChild.isCancelled());
        drainScheduled(scheduled);
        Inventory inventory = player.lastOpenedInventory();

        assertEquals("Go Back", slotTitle(inventory, 48));
        assertEquals("Profiles", slotTitle(inventory, 3));
        assertEquals("Progress", slotTitle(inventory, 4));
        assertEquals("Your SkyBlock Profile", slotTitle(inventory, 19));

        InventoryPreClickEvent switchTab = new InventoryPreClickEvent(inventory, player, new Click.Left(4));
        runtime.onInventoryPreClick(switchTab);

        assertTrue(switchTab.isCancelled());
        drainScheduled(scheduled);
        assertEquals("Profiles", slotTitle(inventory, 3));
        assertEquals("Progress", slotTitle(inventory, 4));
        assertEquals("Farming XLIX", slotTitle(inventory, 19));

        InventoryPreClickEvent backToPreviousFrame = new InventoryPreClickEvent(inventory, player, new Click.Left(48));
        runtime.onInventoryPreClick(backToPreviousFrame);

        assertTrue(backToPreviousFrame.isCancelled());
        drainScheduled(scheduled);
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
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);

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
        drainScheduled(scheduled);
        assertEquals("Tab 1", slotTitle(inventory, 1));
        assertEquals("Tab 7", slotTitle(inventory, 7));
        assertEquals("Tab 0 Item 0", slotTitle(inventory, 19));

        InventoryPreClickEvent jumpEnd = new InventoryPreClickEvent(inventory, player, new Click.Right(8));
        runtime.onInventoryPreClick(jumpEnd);

        assertTrue(jumpEnd.isCancelled());
        drainScheduled(scheduled);
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
        MinestomMenuRuntime runtime = runtime();

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
        MinestomMenuRuntime runtime = runtime();
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
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(sounds, scheduled);

        runtime.open(player, soundMenu());
        Inventory inventory = player.lastOpenedInventory();

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(10)));
        drainScheduled(scheduled);
        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(11)));
        drainScheduled(scheduled);
        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(12)));
        drainScheduled(scheduled);

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
    void compiledMenusRequireLiteralClickVariants() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();
        AtomicInteger triggered = new AtomicInteger();

        runtime.open(player, new StandardMenuService().list()
                .title("Compiled Clicks")
                .addItem(MenuButton.builder(MenuIcon.vanilla("stone"))
                        .name("Action")
                        .action(ActionVerb.VIEW, context -> triggered.incrementAndGet())
                        .build())
                .build());
        Inventory inventory = player.lastOpenedInventory();

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.LeftShift(10)));
        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Double(10)));
        assertEquals(0, triggered.get());

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(10)));
        assertEquals(1, triggered.get());
    }

    @Test
    void compiledMenuClicksAreCappedToOnePerTick() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        AtomicInteger triggered = new AtomicInteger();

        runtime.open(player, new StandardMenuService().list()
                .title("Tick Cap")
                .addItem(MenuButton.builder(MenuIcon.vanilla("stone"))
                        .name("First")
                        .action(ActionVerb.VIEW, context -> triggered.incrementAndGet())
                        .build())
                .addItem(MenuButton.builder(MenuIcon.vanilla("book"))
                        .name("Second")
                        .action(ActionVerb.VIEW, context -> triggered.incrementAndGet())
                        .build())
                .build());
        Inventory inventory = player.lastOpenedInventory();

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(10)));
        assertEquals(1, triggered.get());

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(11)));
        assertEquals(1, triggered.get());

        drainScheduled(scheduled);

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(11)));
        assertEquals(2, triggered.get());
    }

    @Test
    void reactiveMenusCanMoveInventoryStacksWithoutDuplicatingThem() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);

        runtime.open(player, reactiveClickInsertMenu(false));
        Inventory inventory = player.lastOpenedInventory();
        player.getInventory().setItemStack(0, namedMinestomItem(Material.STONE, "Bottom Item", 3));

        InventoryPreClickEvent click = new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(0));

        runtime.onInventoryPreClick(click);

        assertTrue(click.isCancelled());
        drainScheduled(scheduled);
        assertEquals(Material.AIR, player.getInventory().getItemStack(0).material());
        assertEquals("Bottom Item", slotTitle(inventory, 31));

        InventoryPreClickEvent returnClick = new InventoryPreClickEvent(inventory, player, new Click.Left(31));
        runtime.onInventoryPreClick(returnClick);

        assertTrue(returnClick.isCancelled());
        drainScheduled(scheduled);
        assertEquals("Bottom Item", slotTitle(player.getInventory(), 0));
        assertEquals("Click An Inventory Stack", slotTitle(inventory, 31));
    }

    @Test
    void reactiveMenuClicksAreCappedToOnePerTick() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);

        runtime.open(player, reactiveClickRoutingMenu());
        Inventory inventory = player.lastOpenedInventory();

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(22)));
        assertEquals("Placed Clicks: 1", slotTitle(inventory, 22));

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(22)));
        assertEquals("Placed Clicks: 1", slotTitle(inventory, 22));

        drainScheduled(scheduled);

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(22)));
        assertEquals("Placed Clicks: 2", slotTitle(inventory, 22));
    }

    @Test
    void reactiveMenusCanMoveDraggedStacksWithoutDuplicatingThem() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);

        runtime.open(player, reactiveDragInsertMenu(false));
        Inventory inventory = player.lastOpenedInventory();
        player.getInventory().setItemStack(4, namedMinestomItem(Material.EMERALD, "Dragged Item", 2));

        InventoryPreClickEvent pickup = new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(4));
        runtime.onInventoryPreClick(pickup);

        assertTrue(pickup.isCancelled());
        drainScheduled(scheduled);
        assertEquals(Material.AIR, player.getInventory().getItemStack(4).material());
        assertEquals("Dragged Item", itemTitle(player.getInventory().getCursorItem()));
        assertEquals(Material.AIR, inventory.getItemStack(31).material());

        InventoryPreClickEvent drag = new InventoryPreClickEvent(inventory, player, new Click.LeftDrag(List.of(31)));

        runtime.onInventoryPreClick(drag);

        assertTrue(drag.isCancelled());
        drainScheduled(scheduled);
        assertEquals(Material.AIR, player.getInventory().getCursorItem().material());
        assertEquals("Dragged Item", slotTitle(inventory, 31));

        InventoryPreClickEvent returnClick = new InventoryPreClickEvent(inventory, player, new Click.LeftShift(31));
        runtime.onInventoryPreClick(returnClick);

        assertTrue(returnClick.isCancelled());
        drainScheduled(scheduled);
        assertEquals("Dragged Item", slotTitle(player.getInventory(), 4));
        assertEquals(Material.AIR, inventory.getItemStack(31).material());
    }

    @Test
    void reactiveMenusCanPlacePickedUpCenterStacksIntoEmptyInventorySlots() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);

        runtime.open(player, reactiveDragInsertMenu(false));
        Inventory inventory = player.lastOpenedInventory();
        player.getInventory().setItemStack(4, namedMinestomItem(Material.EMERALD, "Dragged Item", 2));

        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(4)));
        drainScheduled(scheduled);
        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.LeftDrag(List.of(31))));
        drainScheduled(scheduled);

        InventoryPreClickEvent pickupFromCenter = new InventoryPreClickEvent(inventory, player, new Click.Left(31));
        runtime.onInventoryPreClick(pickupFromCenter);

        assertTrue(pickupFromCenter.isCancelled());
        drainScheduled(scheduled);
        assertEquals("Dragged Item", itemTitle(player.getInventory().getCursorItem()));
        assertEquals(Material.AIR, inventory.getItemStack(31).material());

        InventoryPreClickEvent placeIntoInventory = new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(8));
        runtime.onInventoryPreClick(placeIntoInventory);

        assertTrue(placeIntoInventory.isCancelled());
        drainScheduled(scheduled);
        assertEquals("Dragged Item", slotTitle(player.getInventory(), 8));
        assertEquals(Material.AIR, player.getInventory().getCursorItem().material());
        assertEquals(Material.AIR, inventory.getItemStack(31).material());
    }

    @Test
    void reactiveMenusDoNotMutateLockedInsertTargets() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();

        runtime.open(player, reactiveClickInsertMenu(true));
        Inventory inventory = player.lastOpenedInventory();
        player.getInventory().setItemStack(2, namedMinestomItem(Material.DIAMOND, "Locked Item", 1));

        InventoryPreClickEvent click = new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(2));
        runtime.onInventoryPreClick(click);

        assertTrue(click.isCancelled());
        assertEquals("Locked Item", slotTitle(player.getInventory(), 2));
        assertEquals("Click An Inventory Stack", slotTitle(inventory, 31));
    }

    @Test
    void reactiveMenusIgnoreInertBaseChromeClicks() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();

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
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), trace, logs, new ArrayDeque<>());

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
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), trace, logs, new ArrayDeque<>());

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
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), trace, logs, new ArrayDeque<>());

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

    @Test
    void reactiveListUsesHousePagingChrome() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);

        runtime.open(player, reactiveListMenu(0));
        Inventory firstPage = player.lastOpenedInventory();

        assertEquals("Profiles (1/2)", flatten(firstPage.getTitle()));
        assertEquals("Item Item 0", slotTitle(firstPage, 10));
        assertEquals("Next Page", slotTitle(firstPage, 53));

        runtime.onInventoryPreClick(new InventoryPreClickEvent(firstPage, player, new Click.Left(53)));
        Inventory secondPage = player.lastOpenedInventory();

        assertEquals("Profiles (2/2)", flatten(secondPage.getTitle()));
        assertEquals("Item Item 28", slotTitle(secondPage, 10));
        assertEquals("Previous Page", slotTitle(secondPage, 45));

        drainScheduled(scheduled);
        runtime.onInventoryPreClick(new InventoryPreClickEvent(secondPage, player, new Click.Left(45)));
        Inventory returnedFirstPage = player.lastOpenedInventory();

        assertEquals("Profiles (1/2)", flatten(returnedFirstPage.getTitle()));
        assertEquals("Item Item 0", slotTitle(returnedFirstPage, 10));
        assertEquals("Next Page", slotTitle(returnedFirstPage, 53));
    }

    @Test
    void reactiveTabsScrollVisibleStripWithoutChangingActiveTabContent() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);

        runtime.open(player, reactiveTabsMenu("tab-0", 1, 1));
        Inventory inventory = player.lastOpenedInventory();

        assertEquals("Reactive Tabs", flatten(inventory.getTitle()));
        assertEquals("Tab 1", slotTitle(inventory, 1));
        assertEquals("Tab 7", slotTitle(inventory, 7));
        assertEquals("Tab 0 Item 21", slotTitle(inventory, 19));
        assertEquals("Previous Page", slotTitle(inventory, 45));

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(0)));

        assertEquals("Tab 0", slotTitle(inventory, 1));
        assertEquals("Tab 6", slotTitle(inventory, 7));
        assertEquals("Tab 0 Item 21", slotTitle(inventory, 19));

        drainScheduled(scheduled);
        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(45)));

        assertEquals("Tab 0 Item 0", slotTitle(inventory, 19));
        assertEquals("Tab 0 Item 20", slotTitle(inventory, 43));
        assertEquals("Next Page", slotTitle(inventory, 53));
    }

    @Test
    void tabsRenderCustomHeadProfilesInTheStrip() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();

        runtime.open(player, customHeadGalleryMenu());
        Inventory inventory = player.lastOpenedInventory();

        assertEquals("dG9vbC10ZXh0dXJl", textureValue(inventory.getItemStack(3).get(DataComponents.PROFILE)));
        assertEquals("Y2hhbWJlci10ZXh0dXJl", textureValue(inventory.getItemStack(4).get(DataComponents.PROFILE)));
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

    private static Menu customHeadGalleryMenu() {
        return new StandardMenuService().tabs()
                .title("Custom Heads")
                .defaultTab("tools")
                .addTab(MenuTab.of("tools", "Tools", MenuIcon.customHead("dG9vbC10ZXh0dXJl"), List.of(
                        MenuButton.builder(MenuIcon.vanilla("book"))
                                .name("Tool Item")
                                .action(ActionVerb.VIEW, context -> { })
                                .build()
                )))
                .addTab(MenuTab.of("chambers", "Chambers", MenuIcon.customHead("Y2hhbWJlci10ZXh0dXJl"), List.of(
                        MenuButton.builder(MenuIcon.vanilla("book"))
                                .name("Chamber Item")
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

    private static ReactiveMenu reactiveClickInsertMenu(boolean locked) {
        return new StandardMenuService().reactiveCanvas()
                .state(new ClickInsertState(null, -1, locked))
                .render(state -> ReactiveMenuView.builder("Reactive Click")
                        .place(13, MenuDisplayItem.builder(MenuIcon.vanilla("hopper"))
                                .name("Click Insert")
                                .description("Click a bottom inventory stack to load it into the center slot, then click the loaded slot to return it to the same source slot.")
                                .build())
                        .place(31, state.stored() != null
                                ? state.stored()
                                : MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                        .name("Click An Inventory Stack")
                                        .description("The source slot clears when the stack loads.")
                                        .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.InventoryClick click && click.item() != null) {
                        if (state.locked() || state.stored() != null) {
                            return ReactiveMenuResult.stay(state);
                        }
                        return ReactiveMenuResult.of(
                                new ClickInsertState(click.item(), click.slot(), false),
                                new ReactiveMenuEffect.SetViewerInventorySlot(click.slot(), null));
                    }
                    if (input instanceof ReactiveMenuInput.Click click && click.slot() == 31) {
                        if (state.locked() || state.stored() == null) {
                            return ReactiveMenuResult.stay(state);
                        }
                        return ReactiveMenuResult.of(
                                new ClickInsertState(null, -1, false),
                                new ReactiveMenuEffect.SetViewerInventorySlot(state.storedSourceSlot(), state.stored()));
                    }
                    return ReactiveMenuResult.stay(state);
                })
                .build();
    }

    private static ReactiveMenu reactiveDragInsertMenu(boolean locked) {
        return new StandardMenuService().reactiveCanvas()
                .fillWithBlackPane(false)
                .state(new DragInsertState(null, -1, null, -1, locked))
                .render(state -> {
                    ReactiveMenuView.Builder builder = ReactiveMenuView.builder("Reactive Drag")
                            .cursor(state.cursor())
                            .place(13, MenuDisplayItem.builder(MenuIcon.vanilla("hopper"))
                                    .name("Shift Or Drag")
                                    .description("Shift-click or click a bottom inventory stack to claim it by source slot, then drag or click it into the center slot.")
                                    .build());
                    if (state.stored() != null) {
                        builder.place(31, state.stored());
                    }
                    return builder.build();
                })
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.InventoryClick click) {
                        if (state.cursor() != null) {
                            if (click.item() == null) {
                                return ReactiveMenuResult.of(
                                        new DragInsertState(state.stored(), state.storedSourceSlot(), null, -1, state.locked()),
                                        new ReactiveMenuEffect.SetViewerInventorySlot(click.slot(), state.cursor()));
                            }
                            return ReactiveMenuResult.stay(state);
                        }
                        if (click.item() == null) {
                            return ReactiveMenuResult.stay(state);
                        }
                        if (state.locked()) {
                            return ReactiveMenuResult.stay(state);
                        }
                        if (click.shift()) {
                            if (state.stored() != null) {
                                return ReactiveMenuResult.stay(state);
                            }
                            return ReactiveMenuResult.of(
                                    new DragInsertState(click.item(), click.slot(), state.cursor(), state.cursorSourceSlot(), false),
                                    new ReactiveMenuEffect.SetViewerInventorySlot(click.slot(), null));
                        }
                        return ReactiveMenuResult.of(
                                new DragInsertState(state.stored(), state.storedSourceSlot(), click.item(), click.slot(), false),
                                new ReactiveMenuEffect.SetViewerInventorySlot(click.slot(), null));
                    }
                    if (input instanceof ReactiveMenuInput.Drag drag
                            && drag.cursor() != null
                            && drag.slots().contains(31)) {
                        if (state.locked() || state.cursor() == null || state.stored() != null) {
                            return ReactiveMenuResult.stay(state);
                        }
                        return ReactiveMenuResult.stay(new DragInsertState(state.cursor(), state.cursorSourceSlot(), null, -1, false));
                    }
                    if (input instanceof ReactiveMenuInput.Click click && click.slot() == 31) {
                        if (state.locked()) {
                            return ReactiveMenuResult.stay(state);
                        }
                        if (state.cursor() != null) {
                            return ReactiveMenuResult.stay(new DragInsertState(state.cursor(), state.cursorSourceSlot(), null, -1, false));
                        }
                        if (state.stored() == null) {
                            return ReactiveMenuResult.stay(state);
                        }
                        if (click.shift()) {
                            return ReactiveMenuResult.of(
                                    new DragInsertState(null, -1, null, -1, false),
                                    new ReactiveMenuEffect.SetViewerInventorySlot(state.storedSourceSlot(), state.stored()));
                        }
                        return ReactiveMenuResult.stay(new DragInsertState(null, -1, state.stored(), state.storedSourceSlot(), false));
                    }
                    if (input instanceof ReactiveMenuInput.DropCursor) {
                        if (state.cursor() == null) {
                            return ReactiveMenuResult.stay(state);
                        }
                        return ReactiveMenuResult.of(
                                new DragInsertState(state.stored(), state.storedSourceSlot(), null, -1, state.locked()),
                                new ReactiveMenuEffect.SetViewerInventorySlot(state.cursorSourceSlot(), state.cursor()));
                    }
                    return ReactiveMenuResult.stay(state);
                })
                .build();
    }

    private static ReactiveMenu reactiveClickRoutingMenu() {
        return new StandardMenuService().reactiveCanvas()
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

    private static ReactiveMenu reactiveListMenu(int pageIndex) {
        return new StandardMenuService().reactiveList()
                .state(pageIndex)
                .render(currentPage -> ReactiveListView.builder("Profiles")
                        .page(currentPage)
                        .addItems(sampleReactiveButtons("Item", 29))
                        .build())
                .reduce((currentPage, input) -> {
                    if (!(input instanceof ReactiveMenuInput.Click click)) {
                        return ReactiveMenuResult.stay(currentPage);
                    }
                    if (click.message() instanceof ReactiveGeometryAction.PreviousPage) {
                        return ReactiveMenuResult.stay(currentPage - 1);
                    }
                    if (click.message() instanceof ReactiveGeometryAction.NextPage) {
                        return ReactiveMenuResult.stay(currentPage + 1);
                    }
                    return ReactiveMenuResult.stay(currentPage);
                })
                .build();
    }

    private static ReactiveMenu reactiveTabsMenu(String activeTabId, int navStart, int pageIndex) {
        return new StandardMenuService().reactiveTabs()
                .state(new ReactiveTabsState(activeTabId, navStart, pageIndex))
                .render(state -> ReactiveTabsView.builder("Reactive Tabs")
                        .activeTab(state.activeTabId())
                        .navStart(state.navStart())
                        .page(state.pageIndex())
                        .addGroup(MenuTabGroup.of("all", IntStream.range(0, 10)
                                .mapToObj(index -> MenuTab.of(
                                        "tab-" + index,
                                        "Tab " + index,
                                        MenuIcon.vanilla("stone"),
                                        sampleReactiveButtons("Tab " + index, index == 0 ? 29 : 1)))
                                .toList()))
                        .build())
                .reduce((state, input) -> {
                    if (!(input instanceof ReactiveMenuInput.Click click)) {
                        return ReactiveMenuResult.stay(state);
                    }
                    if (click.message() instanceof ReactiveGeometryAction.PreviousTabs) {
                        return ReactiveMenuResult.stay(new ReactiveTabsState(
                                state.activeTabId(),
                                Math.max(0, state.navStart() - 1),
                                state.pageIndex()));
                    }
                    if (click.message() instanceof ReactiveGeometryAction.NextTabs) {
                        return ReactiveMenuResult.stay(new ReactiveTabsState(
                                state.activeTabId(),
                                state.navStart() + 1,
                                state.pageIndex()));
                    }
                    if (click.message() instanceof ReactiveGeometryAction.PreviousPage) {
                        return ReactiveMenuResult.stay(new ReactiveTabsState(
                                state.activeTabId(),
                                state.navStart(),
                                Math.max(0, state.pageIndex() - 1)));
                    }
                    if (click.message() instanceof ReactiveGeometryAction.NextPage) {
                        return ReactiveMenuResult.stay(new ReactiveTabsState(
                                state.activeTabId(),
                                state.navStart(),
                                state.pageIndex() + 1));
                    }
                    if (click.message() instanceof ReactiveGeometryAction.SwitchTab switchTab) {
                        return ReactiveMenuResult.stay(new ReactiveTabsState(switchTab.tabId(), state.navStart(), 0));
                    }
                    return ReactiveMenuResult.stay(state);
                })
                .build();
    }

    private static List<MenuButton> sampleReactiveButtons(String prefix, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> MenuButton.builder(MenuIcon.vanilla("stone"))
                        .name(prefix + " Item " + index)
                        .action(ActionVerb.VIEW, context -> { })
                        .build())
                .toList();
    }

    private static TestPlayer player() {
        return new TestPlayer(UUID.randomUUID());
    }

    private static ItemStack namedMinestomItem(Material material, String name, int amount) {
        return ItemStack.of(material, amount)
                .withCustomName(Component.text(name))
                .withoutExtraTooltip();
    }

    private static String slotTitle(AbstractInventory inventory, int slot) {
        return flatten(inventory.getItemStack(slot).get(DataComponents.CUSTOM_NAME));
    }

    private static String itemTitle(ItemStack itemStack) {
        return flatten(itemStack.get(DataComponents.CUSTOM_NAME));
    }

    private static String textureValue(ResolvableProfile profile) {
        return profile.profile()
                .unify(GameProfile::properties, ResolvableProfile.Partial::properties)
                .stream()
                .filter(property -> "textures".equals(property.name()))
                .map(GameProfile.Property::value)
                .findFirst()
                .orElseThrow();
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

    private record ClickInsertState(MenuStack stored, int storedSourceSlot, boolean locked) {
    }

    private record DragInsertState(
            MenuStack stored,
            int storedSourceSlot,
            MenuStack cursor,
            int cursorSourceSlot,
            boolean locked
    ) {
    }

    private record ReactiveTabsState(String activeTabId, int navStart, int pageIndex) {
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
