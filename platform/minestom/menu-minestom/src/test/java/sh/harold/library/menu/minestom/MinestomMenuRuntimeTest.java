package sh.harold.library.menu.minestom;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.instance.AddEntityToInstanceEvent;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerDeathEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.network.player.ResolvableProfile;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventListener;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.inventory.click.Click;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sh.harold.library.menu.ActionVerb;
import sh.harold.library.menu.Menu;
import sh.harold.library.menu.MenuButton;
import sh.harold.library.menu.MenuClick;
import sh.harold.library.menu.MenuCustodyDecision;
import sh.harold.library.menu.MenuCustodyDestination;
import sh.harold.library.menu.MenuCustodyFailure;
import sh.harold.library.menu.MenuCustodyGesture;
import sh.harold.library.menu.MenuCustodySnapshot;
import sh.harold.library.menu.MenuDisplayItem;
import sh.harold.library.menu.MenuFrame;
import sh.harold.library.menu.MenuGeometry;
import sh.harold.library.menu.MenuIcon;
import sh.harold.library.menu.MenuInteraction;
import sh.harold.library.menu.MenuSlot;
import sh.harold.library.menu.MenuSlotAction;
import sh.harold.library.menu.MenuStack;
import sh.harold.library.menu.MenuTab;
import sh.harold.library.menu.MenuTabGroup;
import sh.harold.library.menu.MenuTraceController;
import sh.harold.library.menu.ReactiveGeometryAction;
import sh.harold.library.menu.ReactiveListView;
import sh.harold.library.menu.ReactiveMenu;
import sh.harold.library.menu.ReactiveMenuEffect;
import sh.harold.library.menu.ReactiveMenuInput;
import sh.harold.library.menu.ReactiveMenuResult;
import sh.harold.library.menu.ReactiveTabsView;
import sh.harold.library.menu.ReactiveMenuView;
import sh.harold.library.menu.core.MenuSessionState;
import sh.harold.library.menu.core.StandardMenuService;
import sh.harold.library.menu.core.MenuTickHandle;
import sh.harold.library.sound.CuePlayback;
import sh.harold.library.sound.SoundCue;
import sh.harold.library.sound.SoundCueKeys;
import sh.harold.library.sound.SoundCuePacks;
import sh.harold.library.sound.SoundCueRegistry;
import sh.harold.library.sound.SoundCueService;
import sh.harold.library.sound.SoundTarget;
import sh.harold.library.sound.core.StandardSoundCueRegistry;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
                sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
                nextTickScheduler(scheduled),
                new MenuTraceController(),
                message -> { });
    }

    private static MinestomMenuRuntime runtime(SoundCueService sounds, MenuTraceController trace, List<String> logs,
                                               Deque<Runnable> scheduled) {
        return new MinestomMenuRuntime(
                new MinestomMenuRenderer(),
                sounds,
                sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
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

    private static PlayerDeathEvent deathEvent(Player player) {
        return new PlayerDeathEvent(player, Component.empty(), Component.empty());
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
        assertEquals(2, player.openCount());
        assertEquals("Profiles (2/3)", flatten(secondPageInventory.getTitle()));
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
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        AtomicBoolean enabled = new AtomicBoolean(false);

        runtime.open(player, toggleMenu(enabled));
        Inventory inventory = player.lastOpenedInventory();
        assertEquals("Disabled", slotTitle(inventory, 10));

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(10)));
        drainScheduled(scheduled);

        Inventory replacedInventory = player.lastOpenedInventory();
        assertNotSame(inventory, replacedInventory);
        assertEquals("Enabled", slotTitle(replacedInventory, 10));
        assertEquals(2, player.openCount());
    }

    @Test
    void refreshRebuildsReactiveMenuAfterExternalStateMutation() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        AtomicBoolean enabled = new AtomicBoolean(false);

        runtime.open(player, reactiveRefreshMenu(enabled));
        Inventory inventory = player.lastOpenedInventory();
        assertEquals("Reactive Refresh: Off", slotTitle(inventory, 22));

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(22)));
        drainScheduled(scheduled);

        assertEquals("Reactive Refresh: On", slotTitle(inventory, 22));
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
        assertTrue(staleClick.isCancelled());
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
    void childBackUsesBreadcrumbHistoryWhileTabSwitchesStayInPlace() {
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
        inventory = player.lastOpenedInventory();
        assertEquals("Profiles", slotTitle(inventory, 3));
        assertEquals("Progress", slotTitle(inventory, 4));
        assertEquals("Farming XLIX", slotTitle(inventory, 19));

        InventoryPreClickEvent backToRoot = new InventoryPreClickEvent(inventory, player, new Click.Left(48));
        runtime.onInventoryPreClick(backToRoot);

        assertTrue(backToRoot.isCancelled());
        drainScheduled(scheduled);
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
        inventory = player.lastOpenedInventory();
        assertEquals("Tab 1", slotTitle(inventory, 1));
        assertEquals("Tab 7", slotTitle(inventory, 7));
        assertEquals("Tab 0 Item 0", slotTitle(inventory, 19));

        InventoryPreClickEvent jumpEnd = new InventoryPreClickEvent(inventory, player, new Click.Right(8));
        runtime.onInventoryPreClick(jumpEnd);

        assertTrue(jumpEnd.isCancelled());
        drainScheduled(scheduled);
        inventory = player.lastOpenedInventory();
        assertEquals("Tab 3", slotTitle(inventory, 1));
        assertEquals("Tab 9", slotTitle(inventory, 7));
        assertEquals("Tab 0 Item 0", slotTitle(inventory, 19));

        InventoryPreClickEvent switchTab = new InventoryPreClickEvent(inventory, player, new Click.Left(7));
        runtime.onInventoryPreClick(switchTab);

        assertTrue(switchTab.isCancelled());
        inventory = player.lastOpenedInventory();
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
        Inventory secondPage = player.lastOpenedInventory();
        assertNotSame(inventory, secondPage);
        assertEquals("Previous Page", slotTitle(secondPage, 45));
        assertEquals(List.of("Page 1"), slotLore(secondPage, 45));
        assertEquals("Profile Item 21", slotTitle(secondPage, 19));
        assertEquals("Profile Item 28", slotTitle(secondPage, 28));
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
    void compiledMenusMapShiftClickVariantsToTheirButtons() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        AtomicInteger triggered = new AtomicInteger();

        runtime.open(player, new StandardMenuService().list()
                .title("Compiled Clicks")
                .addItem(MenuButton.builder(MenuIcon.vanilla("stone"))
                        .name("Action")
                        .action(ActionVerb.VIEW, context -> triggered.incrementAndGet())
                        .onRightClick(ActionVerb.VIEW, context -> triggered.incrementAndGet())
                        .build())
                .build());
        Inventory inventory = player.lastOpenedInventory();

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.LeftShift(10)));
        assertEquals(1, triggered.get());
        drainScheduled(scheduled);

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.RightShift(10)));
        assertEquals(2, triggered.get());
        drainScheduled(scheduled);

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Double(10)));
        assertEquals(2, triggered.get());

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(10)));
        assertEquals(3, triggered.get());
    }

    @Test
    void managedReplacementCloseDoesNotDetachTheOpeningSession() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        AtomicInteger childClicks = new AtomicInteger();
        Menu child = new StandardMenuService().canvas()
                .title("Child")
                .rows(3)
                .place(10, MenuButton.builder(MenuIcon.vanilla("stone"))
                        .name("Child Action")
                        .action(ActionVerb.VIEW, context -> childClicks.incrementAndGet())
                        .build())
                .build();
        Menu root = new StandardMenuService().canvas()
                .title("Root")
                .rows(6)
                .place(10, MenuButton.builder(MenuIcon.vanilla("chest"))
                        .name("Open Child")
                        .action(ActionVerb.OPEN, context -> context.open(child))
                        .build())
                .build();

        runtime.open(player, root);
        Inventory rootInventory = player.lastOpenedInventory();
        player.beforeNextOpen(ignored ->
                runtime.onInventoryClose(new InventoryCloseEvent(rootInventory, player, true)));

        runtime.onInventoryPreClick(new InventoryPreClickEvent(rootInventory, player, new Click.Left(10)));
        drainScheduled(scheduled);

        Inventory childInventory = player.lastOpenedInventory();
        assertEquals("Child", flatten(childInventory.getTitle()));
        InventoryPreClickEvent childClick = new InventoryPreClickEvent(childInventory, player, new Click.Left(10));
        runtime.onInventoryPreClick(childClick);

        assertTrue(childClick.isCancelled());
        assertEquals(1, childClicks.get());
    }

    @Test
    void cancelledReplacementKeepsTheOldInventoryAndLogicalState() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        AtomicInteger rootClicks = new AtomicInteger();
        Menu child = new StandardMenuService().canvas()
                .title("Child")
                .rows(3)
                .place(10, MenuDisplayItem.builder(MenuIcon.vanilla("stone"))
                        .name("Child Content")
                        .build())
                .build();
        Menu root = new StandardMenuService().canvas()
                .title("Root")
                .rows(6)
                .place(10, MenuButton.builder(MenuIcon.vanilla("chest"))
                        .name("Open Child")
                        .action(ActionVerb.OPEN, context -> context.open(child))
                        .build())
                .place(11, MenuButton.builder(MenuIcon.vanilla("lever"))
                        .name("Root Action")
                        .action(ActionVerb.VIEW, context -> rootClicks.incrementAndGet())
                        .build())
                .build();

        runtime.open(player, root);
        Inventory rootInventory = player.lastOpenedInventory();
        player.rejectNextOpen();

        runtime.onInventoryPreClick(new InventoryPreClickEvent(rootInventory, player, new Click.Left(10)));
        drainScheduled(scheduled);

        assertSame(rootInventory, player.getOpenInventory());
        assertEquals("Root", flatten(rootInventory.getTitle()));
        assertEquals(1, player.openCount());

        InventoryPreClickEvent rootClick = new InventoryPreClickEvent(rootInventory, player, new Click.Left(11));
        runtime.onInventoryPreClick(rootClick);
        assertTrue(rootClick.isCancelled());
        assertEquals(1, rootClicks.get());
    }

    @Test
    void failedRootReplacementKeepsThePreviousSessionOwned() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();
        AtomicInteger rootClicks = new AtomicInteger();
        Menu root = new StandardMenuService().canvas()
                .title("Root")
                .rows(3)
                .place(10, MenuButton.builder(MenuIcon.vanilla("lever"))
                        .name("Root Action")
                        .action(ActionVerb.VIEW, context -> rootClicks.incrementAndGet())
                        .build())
                .build();
        Menu replacement = new StandardMenuService().canvas()
                .title("Replacement")
                .rows(3)
                .build();
        runtime.open(player, root);
        Inventory rootInventory = player.lastOpenedInventory();
        player.rejectNextOpen();

        runtime.open(player, replacement);

        assertSame(rootInventory, player.getOpenInventory());
        assertEquals(1, player.openCount());
        InventoryPreClickEvent rootClick = new InventoryPreClickEvent(rootInventory, player, new Click.Left(10));
        runtime.onInventoryPreClick(rootClick);
        assertTrue(rootClick.isCancelled());
        assertEquals(1, rootClicks.get());
    }

    @Test
    void failedRootReplacementReconcilesTheSettledCustodyView() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        runtime.open(player, reactiveClickInsertMenu(false));
        Inventory inventory = player.lastOpenedInventory();
        ItemStack first = richMinestomItem(Material.DIAMOND, "First Custody", 2);
        player.getInventory().setItemStack(0, first);
        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(0)));
        drainScheduled(scheduled);
        assertEquals(first, inventory.getItemStack(31));
        player.rejectNextOpen();

        runtime.open(player, new StandardMenuService().canvas()
                .title("Rejected Replacement")
                .rows(3)
                .build());

        assertSame(inventory, player.getOpenInventory());
        assertEquals(first, player.getInventory().getItemStack(0));
        assertEquals("Click An Inventory Stack", slotTitle(inventory, 31));
        ItemStack second = richMinestomItem(Material.EMERALD, "Second Custody", 3);
        player.getInventory().setItemStack(1, second);
        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(1)));
        drainScheduled(scheduled);
        assertEquals(second, inventory.getItemStack(31));
    }

    @Test
    void managedRootReplacementCloseCannotDetachTheNewSession() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();
        AtomicInteger replacementClicks = new AtomicInteger();
        Menu root = new StandardMenuService().canvas().title("Root").rows(3).build();
        Menu replacement = new StandardMenuService().canvas()
                .title("Replacement")
                .rows(3)
                .place(10, MenuButton.builder(MenuIcon.vanilla("lever"))
                        .name("Replacement Action")
                        .action(ActionVerb.VIEW, context -> replacementClicks.incrementAndGet())
                        .build())
                .build();
        runtime.open(player, root);
        Inventory rootInventory = player.lastOpenedInventory();
        player.beforeNextOpen(ignored ->
                runtime.onInventoryClose(new InventoryCloseEvent(rootInventory, player, true)));

        runtime.open(player, replacement);

        Inventory replacementInventory = player.lastOpenedInventory();
        InventoryPreClickEvent click = new InventoryPreClickEvent(
                replacementInventory,
                player,
                new Click.Left(10));
        runtime.onInventoryPreClick(click);
        assertTrue(click.isCancelled());
        assertEquals(1, replacementClicks.get());
    }

    @Test
    void delayedInputFromAReplacedInventoryRemainsCancelled() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();
        runtime.open(player, pagedMenu());
        Inventory firstPage = player.lastOpenedInventory();
        runtime.onInventoryPreClick(new InventoryPreClickEvent(firstPage, player, new Click.Left(53)));
        Inventory secondPage = player.lastOpenedInventory();
        assertNotSame(firstPage, secondPage);

        InventoryPreClickEvent delayed = new InventoryPreClickEvent(firstPage, player, new Click.Left(53));
        runtime.onInventoryPreClick(delayed);

        assertTrue(delayed.isCancelled());
        assertSame(secondPage, player.getOpenInventory());
        assertEquals("Profiles (2/3)", flatten(secondPage.getTitle()));
    }

    @Test
    void promptCompletionReopensTheRetainedInventory() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);

        runtime.open(player, reactivePromptMenu());
        Inventory inventory = player.lastOpenedInventory();
        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(22)));

        assertNull(player.getOpenInventory());
        PlayerChatEvent response = new PlayerChatEvent(player, List.of(), "Updated");
        runtime.onPlayerChat(response);
        drainScheduled(scheduled);

        assertTrue(response.isCancelled());
        assertSame(inventory, player.getOpenInventory());
        assertSame(inventory, player.lastOpenedInventory());
        assertEquals(2, player.openCount());
        assertEquals("Value: Updated", slotTitle(inventory, 22));
    }

    @Test
    void promptCompletionDoesNotReplaceAForeignInventory() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);

        runtime.open(player, reactivePromptMenu());
        Inventory menuInventory = player.lastOpenedInventory();
        runtime.onInventoryPreClick(new InventoryPreClickEvent(menuInventory, player, new Click.Left(22)));
        Inventory foreignInventory = new Inventory(menuInventory.getInventoryType(), Component.text("Foreign"));
        player.openInventory(foreignInventory);

        runtime.onPlayerChat(new PlayerChatEvent(player, List.of(), "Updated"));
        drainScheduled(scheduled);

        assertSame(foreignInventory, player.getOpenInventory());
        assertEquals(2, player.openCount());

        player.openInventory(menuInventory);
        InventoryPreClickEvent staleClick = new InventoryPreClickEvent(menuInventory, player, new Click.Left(22));
        runtime.onInventoryPreClick(staleClick);
        assertTrue(staleClick.isCancelled());
    }

    @Test
    void promptReducerFailureQuarantinesTheHeadlessSession() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        runtime.open(player, reactiveFaultyPromptMenu());
        Inventory inventory = player.lastOpenedInventory();
        ItemStack retained = richMinestomItem(Material.DIAMOND, "Unrelated Inventory", 2);
        player.getInventory().setItemStack(5, retained);

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(22)));
        runtime.onPlayerChat(new PlayerChatEvent(player, List.of(), "explode"));
        drainScheduled(scheduled);

        assertNull(player.getOpenInventory());
        assertEquals(retained, player.getInventory().getItemStack(5));
        InventoryPreClickEvent staleClick = new InventoryPreClickEvent(inventory, player, new Click.Left(22));
        runtime.onInventoryPreClick(staleClick);
        assertTrue(staleClick.isCancelled());
    }

    @Test
    void activePromptSuppressesTickEffectsUntilTheMenuReopens() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        AtomicReference<Runnable> tickAction = new AtomicReference<>();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(
                new MinestomMenuRenderer(),
                new RecordingSoundCueService(),
                (intervalTicks, action) -> {
                    tickAction.set(action);
                    return () -> tickAction.compareAndSet(action, null);
                },
                nextTickScheduler(scheduled),
                new MenuTraceController(),
                message -> { });
        Menu successor = new StandardMenuService().canvas()
                .title("Post Prompt Tick")
                .rows(3)
                .build();
        AtomicInteger tickReductions = new AtomicInteger();
        ReactiveMenu menu = new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> "Initial")
                .tickEvery(1L)
                .render(state -> ReactiveMenuView.builder("Prompt Tick Fence")
                        .place(22, MenuButton.builder(MenuIcon.vanilla("name_tag"))
                                .name("Open Prompt")
                                .emit(ActionVerb.SELECT, "prompt")
                                .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.Click click
                            && "prompt".equals(click.message())) {
                        return ReactiveMenuResult.effect(new ReactiveMenuEffect.RequestTextPrompt(
                                sh.harold.library.menu.ReactiveTextPromptRequest.chat(
                                        "value",
                                        "Enter a value.",
                                        state)));
                    }
                    if (input instanceof ReactiveMenuInput.Tick) {
                        tickReductions.incrementAndGet();
                        return ReactiveMenuResult.effect(new ReactiveMenuEffect.Open(successor));
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
        runtime.open(player, menu);
        Inventory inventory = player.lastOpenedInventory();

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(22)));
        assertNull(player.getOpenInventory());

        tickAction.get().run();

        assertEquals(0, tickReductions.get());
        assertNull(player.getOpenInventory());
        assertEquals(1, player.openCount());

        PlayerChatEvent response = new PlayerChatEvent(player, List.of(), "Updated");
        runtime.onPlayerChat(response);

        scheduled.removeFirst().run();
        scheduled.removeFirst().run();
        tickAction.get().run();

        assertEquals(0, tickReductions.get());
        assertNull(player.getOpenInventory());
        assertEquals(1, player.openCount());

        drainScheduled(scheduled);

        assertTrue(response.isCancelled());
        assertEquals(0, tickReductions.get());
        assertSame(inventory, player.getOpenInventory());
        assertEquals(2, player.openCount());

        tickAction.get().run();

        assertEquals(1, tickReductions.get());
        assertEquals("Post Prompt Tick", flatten(player.lastOpenedInventory().getTitle()));
        assertEquals(3, player.openCount());
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
        ItemStack original = richMinestomItem(Material.STONE, "Bottom Item", 3);
        player.getInventory().setItemStack(0, original);

        InventoryPreClickEvent click = new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(0));

        runtime.onInventoryPreClick(click);

        assertTrue(click.isCancelled());
        drainScheduled(scheduled);
        assertEquals(Material.AIR, player.getInventory().getItemStack(0).material());
        assertEquals(original, inventory.getItemStack(31));
        assertEquals("Bottom Item", slotTitle(inventory, 31));

        InventoryPreClickEvent returnClick = new InventoryPreClickEvent(inventory, player, new Click.Left(31));
        runtime.onInventoryPreClick(returnClick);

        assertTrue(returnClick.isCancelled());
        drainScheduled(scheduled);
        assertEquals(original, player.getInventory().getItemStack(0));
        assertEquals("Bottom Item", slotTitle(player.getInventory(), 0));
        assertEquals("Click An Inventory Stack", slotTitle(inventory, 31));
    }

    @Test
    void unchangedCustodyReducerRestoresTheTargetBaseBeforeASecondInsert() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        runtime.open(player, reactiveUnchangedCustodyMenu());
        Inventory inventory = player.lastOpenedInventory();
        ItemStack original = richMinestomItem(Material.DIAMOND, "Repeat Custody", 5);
        player.getInventory().setItemStack(2, original);

        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(2)));
        drainScheduled(scheduled);

        assertEquals(Material.AIR, player.getInventory().getItemStack(2).material());
        assertEquals(original, inventory.getItemStack(31));

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(31)));
        drainScheduled(scheduled);

        assertEquals(original, player.getInventory().getItemStack(2));
        assertEquals("Empty Custody Target", slotTitle(inventory, 31));
        assertEquals(Material.AIR, player.getInventory().getCursorItem().material());
        assertSame(inventory, player.getOpenInventory());

        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(2)));
        drainScheduled(scheduled);

        assertEquals(Material.AIR, player.getInventory().getItemStack(2).material());
        assertEquals(Material.AIR, player.getInventory().getCursorItem().material());
        assertEquals(original, inventory.getItemStack(31));
        assertSame(inventory, player.getOpenInventory());
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
        ItemStack original = richMinestomItem(Material.EMERALD, "Dragged Item", 2);
        player.getInventory().setItemStack(4, original);

        InventoryPreClickEvent pickup = new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(4));
        runtime.onInventoryPreClick(pickup);

        assertTrue(pickup.isCancelled());
        drainScheduled(scheduled);
        assertEquals(Material.AIR, player.getInventory().getItemStack(4).material());
        assertEquals(original, player.getInventory().getCursorItem());
        assertEquals("Dragged Item", itemTitle(player.getInventory().getCursorItem()));
        assertEquals(Material.AIR, inventory.getItemStack(31).material());

        InventoryPreClickEvent drag = new InventoryPreClickEvent(inventory, player, new Click.LeftDrag(List.of(31)));

        runtime.onInventoryPreClick(drag);

        assertTrue(drag.isCancelled());
        drainScheduled(scheduled);
        assertEquals(Material.AIR, player.getInventory().getCursorItem().material());
        assertEquals(original, inventory.getItemStack(31));
        assertEquals("Dragged Item", slotTitle(inventory, 31));

        InventoryPreClickEvent returnClick = new InventoryPreClickEvent(inventory, player, new Click.LeftShift(31));
        runtime.onInventoryPreClick(returnClick);

        assertTrue(returnClick.isCancelled());
        drainScheduled(scheduled);
        assertEquals(original, player.getInventory().getItemStack(4));
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
        ItemStack original = richMinestomItem(Material.EMERALD, "Dragged Item", 2);
        player.getInventory().setItemStack(4, original);

        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(4)));
        drainScheduled(scheduled);
        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.LeftDrag(List.of(31))));
        drainScheduled(scheduled);

        InventoryPreClickEvent pickupFromCenter = new InventoryPreClickEvent(inventory, player, new Click.Left(31));
        runtime.onInventoryPreClick(pickupFromCenter);

        assertTrue(pickupFromCenter.isCancelled());
        drainScheduled(scheduled);
        assertEquals(original, player.getInventory().getCursorItem());
        assertEquals("Dragged Item", itemTitle(player.getInventory().getCursorItem()));
        assertEquals(Material.AIR, inventory.getItemStack(31).material());

        InventoryPreClickEvent placeIntoInventory = new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(8));
        runtime.onInventoryPreClick(placeIntoInventory);

        assertTrue(placeIntoInventory.isCancelled());
        drainScheduled(scheduled);
        assertEquals(original, player.getInventory().getItemStack(8));
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
    void custodySettlementOnCloseReturnsTheExactNativeStack() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        runtime.open(player, reactiveClickInsertMenu(false));
        Inventory inventory = player.lastOpenedInventory();
        ItemStack original = richMinestomItem(Material.DIAMOND, "Close Custody", 7);
        player.getInventory().setItemStack(3, original);

        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(3)));
        drainScheduled(scheduled);
        assertEquals(original, inventory.getItemStack(31));
        assertEquals(Material.AIR, player.getInventory().getItemStack(3).material());

        runtime.onInventoryClose(new InventoryCloseEvent(inventory, player, true));

        assertEquals(original, player.getInventory().getItemStack(3));
        assertEquals(Material.AIR, player.getInventory().getCursorItem().material());
        assertFalse(inventory.getItemStack(31).equals(original));
        InventoryPreClickEvent staleClick = new InventoryPreClickEvent(inventory, player, new Click.Left(31));
        runtime.onInventoryPreClick(staleClick);
        assertTrue(staleClick.isCancelled());
    }

    @Test
    void custodySettlementOnDisconnectReturnsTheExactCursorStack() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();
        runtime.open(player, reactiveDragInsertMenu(false));
        Inventory inventory = player.lastOpenedInventory();
        ItemStack original = richMinestomItem(Material.EMERALD, "Disconnect Custody", 5);
        player.getInventory().setItemStack(6, original);

        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(6)));
        assertEquals(original, player.getInventory().getCursorItem());

        runtime.onPlayerDisconnect(new PlayerDisconnectEvent(player));

        assertEquals(original, player.getInventory().getItemStack(6));
        assertEquals(Material.AIR, player.getInventory().getCursorItem().material());
        assertEquals(Material.AIR, inventory.getItemStack(31).material());
    }

    @Test
    void deathEventReturnsExactTargetCustodyAndRetiresTheMenu() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        runtime.open(player, reactiveClickInsertMenu(false));
        Inventory inventory = player.lastOpenedInventory();
        ItemStack original = richMinestomItem(Material.DIAMOND, "Death Target", 5);
        player.getInventory().setItemStack(4, original);
        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(4)));
        drainScheduled(scheduled);
        assertEquals(original, inventory.getItemStack(31));

        runtime.createEventNode("death-target-test").call(deathEvent(player));

        assertEquals(original, player.getInventory().getItemStack(4));
        assertEquals(Material.AIR, player.getInventory().getCursorItem().material());
        assertFalse(inventory.getItemStack(31).equals(original));
        assertNull(player.getOpenInventory());
        InventoryPreClickEvent stale = new InventoryPreClickEvent(inventory, player, new Click.Left(31));
        runtime.onInventoryPreClick(stale);
        assertTrue(stale.isCancelled());
    }

    @Test
    void deathReturnsExactCursorCustodyToItsOrigin() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();
        runtime.open(player, reactiveDragInsertMenu(false));
        Inventory inventory = player.lastOpenedInventory();
        ItemStack original = richMinestomItem(Material.EMERALD, "Death Cursor", 6);
        player.getInventory().setItemStack(7, original);
        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(7)));
        assertEquals(original, player.getInventory().getCursorItem());

        runtime.onPlayerDeath(deathEvent(player));

        assertEquals(original, player.getInventory().getItemStack(7));
        assertEquals(Material.AIR, player.getInventory().getCursorItem().material());
        assertEquals(Material.AIR, inventory.getItemStack(31).material());
        assertNull(player.getOpenInventory());
    }

    @Test
    void deathWithFullStorageSpawnsOneExactOverflowItem() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();
        InstanceContainer instance = overflowInstance();
        player.placeIn(instance, new Pos(2.0, 30.0, 3.0));
        try {
            runtime.open(player, reactiveClickInsertMenu(false));
            Inventory inventory = player.lastOpenedInventory();
            ItemStack original = richMinestomItem(Material.NETHER_STAR, "Death Overflow", 7);
            player.getInventory().setItemStack(0, original);
            runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(0)));
            fillStorage(player);

            runtime.onPlayerDeath(deathEvent(player));

            List<ItemEntity> drops = itemEntities(instance);
            assertEquals(1, drops.size());
            assertEquals(original, drops.getFirst().getItemStack());
            assertEquals(new Pos(2.0, 30.5, 3.0), drops.getFirst().getPosition());
            assertCustodyCopyAbsent(player, inventory, original);
            assertNull(player.getOpenInventory());
        } finally {
            destroyInstance(instance);
        }
    }

    @Test
    void deathCancelsAnActivePromptWithoutReopeningItsMenu() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        runtime.open(player, reactivePromptMenu());
        Inventory inventory = player.lastOpenedInventory();
        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(22)));
        assertNull(player.getOpenInventory());

        runtime.onPlayerDeath(deathEvent(player));
        PlayerChatEvent response = new PlayerChatEvent(player, List.of(), "Too Late");
        runtime.onPlayerChat(response);
        drainScheduled(scheduled);

        assertFalse(response.isCancelled());
        assertNull(player.getOpenInventory());
        assertEquals(1, player.openCount());
        InventoryPreClickEvent stale = new InventoryPreClickEvent(inventory, player, new Click.Left(22));
        runtime.onInventoryPreClick(stale);
        assertTrue(stale.isCancelled());
    }

    @Test
    void deathMakesOldTicksAndLateCloseInertAfterAReplacementOpens() {
        AtomicReference<Runnable> tickAction = new AtomicReference<>();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(
                new MinestomMenuRenderer(),
                new RecordingSoundCueService(),
                (intervalTicks, action) -> {
                    tickAction.set(action);
                    return () -> tickAction.compareAndSet(action, null);
                },
                nextTickScheduler(scheduled),
                new MenuTraceController(),
                message -> { });
        TestPlayer player = player();
        ReactiveMenu ticking = new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> 0)
                .tickEvery(1L)
                .render(state -> ReactiveMenuView.builder("Death Tick").build())
                .reduce((state, input) -> ReactiveMenuResult.unchanged())
                .build();
        Menu replacement = new StandardMenuService().canvas()
                .title("Respawn Replacement")
                .rows(3)
                .build();
        runtime.open(player, ticking);
        Inventory deadInventory = player.lastOpenedInventory();
        Runnable deadTick = tickAction.get();

        runtime.onPlayerDeath(deathEvent(player));
        assertNull(tickAction.get());
        deadTick.run();
        runtime.open(player, replacement);
        Inventory replacementInventory = player.lastOpenedInventory();
        runtime.onInventoryClose(new InventoryCloseEvent(deadInventory, player, true));
        drainScheduled(scheduled);

        assertSame(replacementInventory, player.getOpenInventory());
        assertEquals("Respawn Replacement", flatten(replacementInventory.getTitle()));
        InventoryPreClickEvent stale = new InventoryPreClickEvent(deadInventory, player, new Click.Left(0));
        runtime.onInventoryPreClick(stale);
        assertTrue(stale.isCancelled());
    }

    @Test
    void deathSettlementReducerFailureCannotSettleOrDeliverTwice() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();
        AtomicInteger deathSettlements = new AtomicInteger();
        ReactiveMenu menu = new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> new CustodyState(MenuCustodySnapshot.EMPTY, false))
                .custodyTarget("center", 31)
                .custodyPolicy((state, gesture, snapshot) -> {
                    if (gesture instanceof MenuCustodyGesture.ViewerClick viewerClick
                            && viewerClick.slot().item() != null
                            && snapshot.empty()) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.target("center"));
                    }
                    return MenuCustodyDecision.reject();
                })
                .render(state -> ReactiveMenuView.builder("Death Reducer")
                        .place(31, state.snapshot().targets().containsKey("center")
                                ? state.snapshot().targets().get("center").presentation()
                                : MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                        .name("Custody Target")
                                        .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.CustodyCommitted committed) {
                        if (committed.gesture() instanceof MenuCustodyGesture.Settle settle
                                && settle.reason() == MenuCustodyGesture.SettleReason.DEATH) {
                            deathSettlements.incrementAndGet();
                            throw new IllegalStateException("intentional death settlement failure");
                        }
                        return ReactiveMenuResult.update(new CustodyState(committed.snapshot(), false));
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
        runtime.open(player, menu);
        Inventory inventory = player.lastOpenedInventory();
        ItemStack original = richMinestomItem(Material.AMETHYST_SHARD, "Settle Once", 4);
        player.getInventory().setItemStack(2, original);
        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(2)));

        runtime.onPlayerDeath(deathEvent(player));
        runtime.onPlayerDeath(deathEvent(player));
        runtime.onInventoryClose(new InventoryCloseEvent(inventory, player, true));

        assertEquals(1, deathSettlements.get());
        assertEquals(original, player.getInventory().getItemStack(2));
        assertEquals(Material.AIR, player.getInventory().getCursorItem().material());
        assertFalse(inventory.getItemStack(31).equals(original));
        assertNull(player.getOpenInventory());
    }

    @Test
    void promptSettlementReturnsExactCustodyBeforeClosingAndReopening() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        runtime.open(player, reactiveCustodyPromptMenu());
        Inventory inventory = player.lastOpenedInventory();
        ItemStack original = richMinestomItem(Material.NETHER_STAR, "Prompt Custody", 1);
        player.getInventory().setItemStack(7, original);

        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(7)));
        drainScheduled(scheduled);
        assertEquals(original, inventory.getItemStack(31));

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(22)));

        assertNull(player.getOpenInventory());
        assertEquals(original, player.getInventory().getItemStack(7));
        assertEquals(Material.AIR, player.getInventory().getCursorItem().material());
        assertFalse(inventory.getItemStack(31).equals(original));

        runtime.onPlayerChat(new PlayerChatEvent(player, List.of(), "Updated"));
        drainScheduled(scheduled);

        assertSame(inventory, player.getOpenInventory());
        assertEquals(original, player.getInventory().getItemStack(7));
        assertEquals("Value: Updated", slotTitle(inventory, 22));
    }

    @Test
    void settlementEffectsCannotOverrideTheEnclosingNavigation() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        Menu unexpected = new StandardMenuService().canvas()
                .title("Unexpected Settlement Effect")
                .rows(6)
                .build();
        Menu intended = new StandardMenuService().canvas()
                .title("Intended Destination")
                .rows(6)
                .build();
        ReactiveMenu source = new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> new CustodyState(MenuCustodySnapshot.EMPTY, false))
                .custodyTarget("center", 31)
                .custodyPolicy((state, gesture, snapshot) -> {
                    if (gesture instanceof MenuCustodyGesture.ViewerClick viewerClick
                            && viewerClick.slot().item() != null
                            && snapshot.empty()) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.target("center"));
                    }
                    return MenuCustodyDecision.reject();
                })
                .render(state -> ReactiveMenuView.builder("Settlement Source")
                        .place(22, MenuButton.builder(MenuIcon.vanilla("chest"))
                                .name("Navigate")
                                .emit(ActionVerb.OPEN, "navigate")
                                .build())
                        .place(31, state.snapshot().targets().containsKey("center")
                                ? state.snapshot().targets().get("center").presentation()
                                : MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                        .name("Custody Target")
                                        .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.CustodyCommitted committed) {
                        CustodyState next = new CustodyState(committed.snapshot(), false);
                        if (committed.gesture() instanceof MenuCustodyGesture.Settle settle
                                && settle.reason() == MenuCustodyGesture.SettleReason.NAVIGATE) {
                            return ReactiveMenuResult.update(
                                    next,
                                    new ReactiveMenuEffect.Open(unexpected));
                        }
                        return ReactiveMenuResult.update(next);
                    }
                    if (input instanceof ReactiveMenuInput.Click click
                            && "navigate".equals(click.message())) {
                        return ReactiveMenuResult.effect(new ReactiveMenuEffect.Open(intended));
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
        runtime.open(player, source);
        Inventory sourceInventory = player.lastOpenedInventory();
        ItemStack original = richMinestomItem(Material.DIAMOND, "Settled Once", 2);
        player.getInventory().setItemStack(2, original);
        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(2)));
        drainScheduled(scheduled);

        runtime.onInventoryPreClick(new InventoryPreClickEvent(sourceInventory, player, new Click.Left(22)));
        drainScheduled(scheduled);

        assertEquals(2, player.openCount());
        assertEquals("Intended Destination", flatten(player.lastOpenedInventory().getTitle()));
        assertEquals(original, player.getInventory().getItemStack(2));
        assertEquals(Material.AIR, sourceInventory.getItemStack(31).material());

        Inventory intendedInventory = player.lastOpenedInventory();
        runtime.onInventoryPreClick(new InventoryPreClickEvent(intendedInventory, player, new Click.Left(48)));
        drainScheduled(scheduled);

        assertEquals(3, player.openCount());
        assertEquals("Settlement Source", flatten(player.lastOpenedInventory().getTitle()));
        Inventory returnedSource = player.lastOpenedInventory();
        runtime.onInventoryPreClick(new InventoryPreClickEvent(returnedSource, player, new Click.Left(48)));
        drainScheduled(scheduled);
        assertEquals(3, player.openCount());
        assertSame(returnedSource, player.getOpenInventory());
    }

    @Test
    void repeatedChildBackCyclesSettleCustodyBeforeEachTransition() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        ReactiveMenu child = reactiveClickInsertMenu(false);
        Menu root = new StandardMenuService().canvas()
                .title("Custody Root")
                .rows(6)
                .place(10, MenuButton.builder(MenuIcon.vanilla("chest"))
                        .name("Open Custody Child")
                        .action(ActionVerb.OPEN, context -> context.open(child))
                        .build())
                .build();
        runtime.open(player, root);

        for (int cycle = 0; cycle < 2; cycle++) {
            Inventory rootInventory = player.lastOpenedInventory();
            runtime.onInventoryPreClick(new InventoryPreClickEvent(rootInventory, player, new Click.Left(10)));
            drainScheduled(scheduled);
            Inventory childInventory = player.lastOpenedInventory();
            ItemStack original = richMinestomItem(Material.AMETHYST_SHARD, "Cycle " + cycle, cycle + 1);
            player.getInventory().setItemStack(4, original);

            runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(4)));
            drainScheduled(scheduled);
            assertEquals(original, childInventory.getItemStack(31));

            runtime.onInventoryPreClick(new InventoryPreClickEvent(childInventory, player, new Click.Left(48)));
            drainScheduled(scheduled);

            assertEquals("Custody Root", flatten(player.lastOpenedInventory().getTitle()));
            assertEquals(original, player.getInventory().getItemStack(4));
            assertEquals(Material.AIR, player.getInventory().getCursorItem().material());
            assertFalse(childInventory.getItemStack(31).equals(original));
        }
    }

    @Test
    void custodyQuarantinesNativeTargetDriftWithoutOverwritingEitherStack() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();
        runtime.open(player, reactiveClickInsertMenu(false));
        Inventory inventory = player.lastOpenedInventory();
        ItemStack source = richMinestomItem(Material.DIAMOND, "Source", 4);
        ItemStack foreignTarget = richMinestomItem(Material.EMERALD, "Foreign Target", 6);
        player.getInventory().setItemStack(2, source);
        inventory.setItemStack(31, foreignTarget);

        InventoryPreClickEvent click = new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(2));
        runtime.onInventoryPreClick(click);

        assertTrue(click.isCancelled());
        assertEquals(source, player.getInventory().getItemStack(2));
        assertEquals(foreignTarget, inventory.getItemStack(31));
        assertEquals(Material.AIR, player.getInventory().getCursorItem().material());
        assertNull(player.getOpenInventory());
    }

    @Test
    void stalePreparedTransitionRestoresThePreviousInventory() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();
        Menu root = new StandardMenuService().canvas().title("Root").rows(3).build();
        Menu child = new StandardMenuService().canvas().title("Child").rows(3).build();
        MenuSessionState state = new MenuSessionState(root);
        MinestomMenuSession session = new MinestomMenuSession(runtime, player, state);
        session.open();
        Inventory rootInventory = player.lastOpenedInventory();
        MenuSessionState.PreparedTransition transition = state.prepareOpenChild(child).orElseThrow();
        player.beforeNextOpen(ignored -> state.invalidateView());

        assertFalse(session.applyTransition(transition));

        assertSame(rootInventory, player.getOpenInventory());
        assertSame(rootInventory, session.inventory());
        assertSame(root, state.menu());
        assertEquals(3, player.openCount());
    }

    @Test
    void multiTargetDragIsRejectedWithoutSplittingTheExactStack() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        runtime.open(player, reactiveTwoTargetDragMenu());
        Inventory inventory = player.lastOpenedInventory();
        ItemStack original = richMinestomItem(Material.EMERALD, "Whole Stack", 9);
        player.getInventory().setItemStack(5, original);

        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(5)));
        drainScheduled(scheduled);
        assertEquals(original, player.getInventory().getCursorItem());

        InventoryPreClickEvent drag = new InventoryPreClickEvent(
                inventory,
                player,
                new Click.LeftDrag(List.of(31, 32)));
        runtime.onInventoryPreClick(drag);

        assertTrue(drag.isCancelled());
        assertEquals(original, player.getInventory().getCursorItem());
        assertFalse(inventory.getItemStack(31).equals(original));
        assertFalse(inventory.getItemStack(32).equals(original));
    }

    @Test
    void occupiedTargetCannotRedirectItsExactStackIntoAnotherTarget() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        AtomicReference<MenuCustodyFailure> rejection = new AtomicReference<>();
        ReactiveMenu menu = new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> new CustodyState(MenuCustodySnapshot.EMPTY, false))
                .custodyTarget("left", 31)
                .custodyTarget("right", 32)
                .custodyPolicy((state, gesture, snapshot) -> {
                    if (gesture instanceof MenuCustodyGesture.ViewerClick viewerClick
                            && viewerClick.slot().item() != null
                            && snapshot.empty()) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.target("left"));
                    }
                    if (gesture instanceof MenuCustodyGesture.TargetClick targetClick
                            && "left".equals(targetClick.targetKey())
                            && snapshot.targets().containsKey("left")) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.target("right"));
                    }
                    return MenuCustodyDecision.reject();
                })
                .render(state -> ReactiveMenuView.builder("Target Redirect")
                        .place(31, state.snapshot().targets().containsKey("left")
                                ? state.snapshot().targets().get("left").presentation()
                                : MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                        .name("Left Target")
                                        .build())
                        .place(32, state.snapshot().targets().containsKey("right")
                                ? state.snapshot().targets().get("right").presentation()
                                : MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                        .name("Right Target")
                                        .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.CustodyCommitted committed) {
                        return ReactiveMenuResult.update(new CustodyState(committed.snapshot(), false));
                    }
                    if (input instanceof ReactiveMenuInput.CustodyRejected rejected) {
                        rejection.set(rejected.failure());
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
        runtime.open(player, menu);
        Inventory inventory = player.lastOpenedInventory();
        ItemStack original = richMinestomItem(Material.DIAMOND, "No Cross Target", 5);
        player.getInventory().setItemStack(3, original);
        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(3)));
        drainScheduled(scheduled);
        assertEquals(original, inventory.getItemStack(31));

        InventoryPreClickEvent redirect = new InventoryPreClickEvent(inventory, player, new Click.Left(31));
        runtime.onInventoryPreClick(redirect);

        assertTrue(redirect.isCancelled());
        assertEquals(MenuCustodyFailure.INVALID_DESTINATION, rejection.get());
        assertEquals(original, inventory.getItemStack(31));
        assertEquals(Material.STONE_BUTTON, inventory.getItemStack(32).material());
        assertEquals(Material.AIR, player.getInventory().getItemStack(3).material());
        assertEquals(Material.AIR, player.getInventory().getCursorItem().material());

        runtime.onInventoryClose(new InventoryCloseEvent(inventory, player, true));
        assertEquals(original, player.getInventory().getItemStack(3));
        assertFalse(inventory.getItemStack(31).equals(original));
    }

    @Test
    void interactiveOriginRejectsAnOccupiedOriginalSlot() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        runtime.open(player, reactiveDragInsertMenu(false));
        Inventory inventory = player.lastOpenedInventory();
        ItemStack original = richMinestomItem(Material.DIAMOND, "Original", 3);
        ItemStack blocker = richMinestomItem(Material.STONE, "Blocker", 2);
        player.getInventory().setItemStack(4, original);
        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(4)));
        drainScheduled(scheduled);
        player.getInventory().setItemStack(4, blocker);

        InventoryPreClickEvent outside = new InventoryPreClickEvent(
                inventory,
                player,
                new Click.LeftDropCursor());
        runtime.onInventoryPreClick(outside);

        assertTrue(outside.isCancelled());
        assertEquals(original, player.getInventory().getCursorItem());
        assertEquals(blocker, player.getInventory().getItemStack(4));
        for (int slot = 0; slot < PlayerInventory.INNER_INVENTORY_SIZE; slot++) {
            if (slot != 4) {
                assertFalse(player.getInventory().getItemStack(slot).equals(original));
            }
        }
    }

    @Test
    void fullStorageSettlementSpawnsExactItemInsteadOfUsingEquipmentSlots() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();
        InstanceContainer instance = overflowInstance();
        player.placeIn(instance, new Pos(4.0, 42.0, 7.0));
        try {
            runtime.open(player, reactiveClickInsertMenu(false));
            Inventory inventory = player.lastOpenedInventory();
            ItemStack original = richMinestomItem(Material.NETHER_STAR, "Fallback Drop", 7);
            player.getInventory().setItemStack(0, original);
            runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(0)));
            fillStorage(player);

            runtime.onInventoryClose(new InventoryCloseEvent(inventory, player, true));

            List<ItemEntity> drops = itemEntities(instance);
            assertEquals(1, drops.size());
            assertEquals(original, drops.getFirst().getItemStack());
            assertEquals(7, drops.getFirst().getItemStack().amount());
            assertEquals(new Pos(4.0, 42.5, 7.0), drops.getFirst().getPosition());
            for (int slot = PlayerInventory.INNER_INVENTORY_SIZE; slot < PlayerInventory.INVENTORY_SIZE; slot++) {
                assertEquals(Material.AIR, player.getInventory().getItemStack(slot).material());
            }
            assertFalse(inventory.getItemStack(31).equals(original));
        } finally {
            destroyInstance(instance);
        }
    }

    @Test
    void itemDropListenerPresenceCannotInterceptLifecycleOverflow() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();
        InstanceContainer instance = overflowInstance();
        player.placeIn(instance, Pos.ZERO);
        AtomicInteger dropListenerCalls = new AtomicInteger();
        EventListener<ItemDropEvent> listener = EventListener.of(ItemDropEvent.class, event -> {
            dropListenerCalls.incrementAndGet();
            event.setCancelled(true);
        });
        instance.eventNode().addListener(listener);
        try {
            runtime.open(player, reactiveClickInsertMenu(false));
            Inventory inventory = player.lastOpenedInventory();
            ItemStack original = richMinestomItem(Material.NETHER_STAR, "Ambiguous Drop", 1);
            player.getInventory().setItemStack(0, original);
            runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(0)));
            fillStorage(player);

            runtime.onInventoryClose(new InventoryCloseEvent(inventory, player, true));

            assertEquals(0, dropListenerCalls.get());
            List<ItemEntity> drops = itemEntities(instance);
            assertEquals(1, drops.size());
            assertEquals(original, drops.getFirst().getItemStack());
            assertCustodyCopyAbsent(player, inventory, original);
            InventoryPreClickEvent stale = new InventoryPreClickEvent(inventory, player, new Click.Left(31));
            runtime.onInventoryPreClick(stale);
            assertTrue(stale.isCancelled());
        } finally {
            instance.eventNode().removeListener(listener);
            destroyInstance(instance);
        }
    }

    @Test
    void custodyNeverAcquiresSlotsOutsideOrdinaryViewerStorage() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();
        runtime.open(player, reactiveClickInsertMenu(false));
        Inventory inventory = player.lastOpenedInventory();
        ItemStack equipment = richMinestomItem(Material.DIAMOND, "Equipment", 1);
        int nonStorageSlot = PlayerInventory.INNER_INVENTORY_SIZE;
        player.getInventory().setItemStack(nonStorageSlot, equipment);

        InventoryPreClickEvent click = new InventoryPreClickEvent(
                player.getInventory(),
                player,
                new Click.Left(nonStorageSlot));
        runtime.onInventoryPreClick(click);

        assertTrue(click.isCancelled());
        assertEquals(equipment, player.getInventory().getItemStack(nonStorageSlot));
        assertEquals(Material.AIR, player.getInventory().getCursorItem().material());
        assertFalse(inventory.getItemStack(31).equals(equipment));
    }

    @Test
    void cancelledItemEntitySpawnNeverRestoresCustody() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();
        InstanceContainer instance = overflowInstance();
        player.placeIn(instance, Pos.ZERO);
        List<ItemStack> attemptedItems = new ArrayList<>();
        EventListener<AddEntityToInstanceEvent> listener = EventListener.of(AddEntityToInstanceEvent.class, event -> {
            if (!(event.getEntity() instanceof ItemEntity item)) {
                return;
            }
            attemptedItems.add(item.getItemStack());
            event.setCancelled(true);
        });
        instance.eventNode().addListener(listener);
        try {
            runtime.open(player, reactiveClickInsertMenu(false));
            Inventory inventory = player.lastOpenedInventory();
            ItemStack original = richMinestomItem(Material.NETHER_STAR, "Fail Closed", 1);
            player.getInventory().setItemStack(0, original);
            runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(0)));
            fillStorage(player);

            runtime.onInventoryClose(new InventoryCloseEvent(inventory, player, true));

            assertEquals(List.of(original), attemptedItems);
            assertTrue(itemEntities(instance).isEmpty());
            assertCustodyCopyAbsent(player, inventory, original);
        } finally {
            instance.eventNode().removeListener(listener);
            destroyInstance(instance);
        }
    }

    @Test
    void spawnListenerFailureAfterSideEffectDoesNotRetryOrRestoreCustody() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();
        InstanceContainer instance = overflowInstance();
        player.placeIn(instance, Pos.ZERO);
        AtomicInteger listenerCalls = new AtomicInteger();
        EventListener<AddEntityToInstanceEvent> listener = EventListener.of(AddEntityToInstanceEvent.class, event -> {
            if (event.getEntity() instanceof ItemEntity) {
                listenerCalls.incrementAndGet();
                throw new IllegalStateException("intentional post-side-effect failure");
            }
        });
        instance.eventNode().addListener(listener);
        try {
            runtime.open(player, reactiveClickInsertMenu(false));
            Inventory inventory = player.lastOpenedInventory();
            ItemStack original = richMinestomItem(Material.NETHER_STAR, "Listener Failure", 3);
            player.getInventory().setItemStack(0, original);
            runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(0)));
            fillStorage(player);

            runtime.onInventoryClose(new InventoryCloseEvent(inventory, player, true));

            assertEquals(1, listenerCalls.get());
            List<ItemEntity> drops = itemEntities(instance);
            assertEquals(1, drops.size());
            assertEquals(original, drops.getFirst().getItemStack());
            assertCustodyCopyAbsent(player, inventory, original);
        } finally {
            instance.eventNode().removeListener(listener);
            destroyInstance(instance);
        }
    }

    @Test
    void reducerFailureAfterCustodyCommitSettlesAndQuarantinesTheSession() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();
        runtime.open(player, reactiveFaultyCustodyMenu());
        Inventory inventory = player.lastOpenedInventory();
        ItemStack original = richMinestomItem(Material.DIAMOND, "Reducer Failure", 4);
        player.getInventory().setItemStack(2, original);

        InventoryPreClickEvent click = new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(2));
        runtime.onInventoryPreClick(click);

        assertTrue(click.isCancelled());
        assertEquals(original, player.getInventory().getItemStack(2));
        assertEquals(Material.AIR, player.getInventory().getCursorItem().material());
        assertFalse(inventory.getItemStack(31).equals(original));
        assertNull(player.getOpenInventory());
        InventoryPreClickEvent delayed = new InventoryPreClickEvent(inventory, player, new Click.Left(31));
        runtime.onInventoryPreClick(delayed);
        assertTrue(delayed.isCancelled());
    }

    @Test
    void nativeMutationRollbackRestoresDestinationBeforeSource() {
        FaultInjectingPlayerInventory playerInventory = new FaultInjectingPlayerInventory();
        TestPlayer player = new TestPlayer(UUID.randomUUID(), playerInventory);
        MinestomMenuRuntime runtime = runtime();
        runtime.open(player, reactiveDragInsertMenu(false));
        Inventory inventory = player.lastOpenedInventory();
        ItemStack original = richMinestomItem(Material.EMERALD, "Rollback Order", 3);
        playerInventory.setItemStack(4, original);
        playerInventory.clearMutationLog();
        playerInventory.failAfterNextCursorWrite();

        InventoryPreClickEvent click = new InventoryPreClickEvent(playerInventory, player, new Click.Left(4));
        runtime.onInventoryPreClick(click);

        assertTrue(click.isCancelled());
        assertEquals(List.of(
                        new NativeMutation("viewer", 4, ItemStack.AIR),
                        new NativeMutation("cursor", -1, original),
                        new NativeMutation("cursor", -1, ItemStack.AIR),
                        new NativeMutation("viewer", 4, original)),
                playerInventory.mutations());
        assertEquals(original, playerInventory.getItemStack(4));
        assertEquals(Material.AIR, playerInventory.getCursorItem().material());
        assertSame(inventory, player.getOpenInventory());
    }

    @Test
    void nativeMutationRollbackStopsBeforeSourceWhenDestinationRestoreFails() {
        FaultInjectingPlayerInventory playerInventory = new FaultInjectingPlayerInventory();
        TestPlayer player = new TestPlayer(UUID.randomUUID(), playerInventory);
        MinestomMenuRuntime runtime = runtime();
        runtime.open(player, reactiveDragInsertMenu(false));
        ItemStack original = richMinestomItem(Material.EMERALD, "Rollback Failure", 3);
        playerInventory.setItemStack(4, original);
        playerInventory.clearMutationLog();
        playerInventory.failAfterNextCursorWrites(2);

        runtime.onInventoryPreClick(new InventoryPreClickEvent(playerInventory, player, new Click.Left(4)));

        assertEquals(List.of(
                        new NativeMutation("viewer", 4, ItemStack.AIR),
                        new NativeMutation("cursor", -1, original),
                        new NativeMutation("cursor", -1, ItemStack.AIR)),
                playerInventory.mutations());
        assertEquals(Material.AIR, playerInventory.getItemStack(4).material());
        assertEquals(Material.AIR, playerInventory.getCursorItem().material());
        assertNull(player.getOpenInventory());
    }

    @Test
    void custodyRejectedReducerFailureQuarantinesTheSession() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();
        runtime.open(player, reactiveFaultyCustodyRejectedMenu());
        Inventory inventory = player.lastOpenedInventory();
        ItemStack original = richMinestomItem(Material.DIAMOND, "Rejected Reducer", 2);
        player.getInventory().setItemStack(3, original);

        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(3)));

        assertEquals(original, player.getInventory().getItemStack(3));
        assertEquals(Material.AIR, player.getInventory().getCursorItem().material());
        assertNull(player.getOpenInventory());
        InventoryPreClickEvent stale = new InventoryPreClickEvent(inventory, player, new Click.Left(31));
        runtime.onInventoryPreClick(stale);
        assertTrue(stale.isCancelled());
    }

    @Test
    void custodyPolicyRootReplacementCannotMoveTheSourceIntoTheRetiredSession() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        Menu replacement = new StandardMenuService().canvas()
                .title("Policy Successor")
                .rows(3)
                .place(13, MenuDisplayItem.builder(MenuIcon.vanilla("emerald"))
                        .name("Successor Content")
                        .build())
                .build();
        ReactiveMenu menu = new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> 0)
                .custodyTarget("center", 31)
                .custodyPolicy((state, gesture, snapshot) -> {
                    if (gesture instanceof MenuCustodyGesture.ViewerClick) {
                        runtime.open(player, replacement);
                        return MenuCustodyDecision.move(MenuCustodyDestination.target("center"));
                    }
                    return MenuCustodyDecision.reject();
                })
                .render(state -> ReactiveMenuView.builder("Policy Source")
                        .place(31, MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                .name("Custody Target")
                                .build())
                        .build())
                .reduce((state, input) -> ReactiveMenuResult.unchanged())
                .build();
        runtime.open(player, menu);
        Inventory sourceInventory = player.lastOpenedInventory();
        ItemStack original = richMinestomItem(Material.DIAMOND, "Policy Source Item", 4);
        player.getInventory().setItemStack(3, original);

        InventoryPreClickEvent click = new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(3));
        runtime.onInventoryPreClick(click);

        assertTrue(click.isCancelled());
        assertSame(sourceInventory, player.getOpenInventory());
        assertEquals(original, player.getInventory().getItemStack(3));
        assertEquals("Custody Target", slotTitle(sourceInventory, 31));

        drainScheduled(scheduled);

        Inventory successor = player.lastOpenedInventory();
        assertNotSame(sourceInventory, successor);
        assertEquals("Policy Successor", flatten(successor.getTitle()));
        assertEquals(original, player.getInventory().getItemStack(3));
        assertEquals("Successor Content", slotTitle(successor, 13));

        InventoryPreClickEvent stale = new InventoryPreClickEvent(sourceInventory, player, new Click.Left(31));
        runtime.onInventoryPreClick(stale);
        assertTrue(stale.isCancelled());
        assertSame(successor, player.getOpenInventory());
    }

    @Test
    void compiledActionDefersReentrantNavigationUntilTheCallbackReturns() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        Menu successor = new StandardMenuService().canvas()
                .title("Deferred Successor")
                .rows(3)
                .build();
        Menu source = new StandardMenuService().canvas()
                .title("Direct Source")
                .rows(3)
                .place(10, MenuButton.builder(MenuIcon.vanilla("chest"))
                        .name("Open Child")
                        .action(ActionVerb.OPEN, context -> context.open(successor))
                        .build())
                .build();
        runtime.open(player, source);
        Inventory sourceInventory = player.lastOpenedInventory();

        InventoryPreClickEvent click = new InventoryPreClickEvent(sourceInventory, player, new Click.Left(10));
        runtime.onInventoryPreClick(click);

        assertTrue(click.isCancelled());
        assertSame(sourceInventory, player.getOpenInventory());
        assertEquals(1, player.openCount());

        drainScheduled(scheduled);

        assertEquals(2, player.openCount());
        assertEquals("Deferred Successor", flatten(player.lastOpenedInventory().getTitle()));
        InventoryPreClickEvent stale = new InventoryPreClickEvent(sourceInventory, player, new Click.Left(10));
        runtime.onInventoryPreClick(stale);
        assertTrue(stale.isCancelled());
    }

    @Test
    void throwingCompiledActionDiscardsItsQueuedNavigationAndQuarantines() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        Menu successor = new StandardMenuService().canvas()
                .title("Must Not Open")
                .rows(3)
                .build();
        Menu source = new StandardMenuService().canvas()
                .title("Throwing Direct Source")
                .rows(3)
                .place(10, MenuButton.builder(MenuIcon.vanilla("barrier"))
                        .name("Queue Then Throw")
                        .action(ActionVerb.OPEN, context -> {
                            context.open(successor);
                            throw new IllegalStateException("intentional direct action failure");
                        })
                        .build())
                .build();
        runtime.open(player, source);
        Inventory sourceInventory = player.lastOpenedInventory();

        InventoryPreClickEvent click = new InventoryPreClickEvent(sourceInventory, player, new Click.Left(10));
        runtime.onInventoryPreClick(click);
        drainScheduled(scheduled);

        assertTrue(click.isCancelled());
        assertNull(player.getOpenInventory());
        assertEquals(1, player.openCount());
        InventoryPreClickEvent stale = new InventoryPreClickEvent(sourceInventory, player, new Click.Left(10));
        runtime.onInventoryPreClick(stale);
        assertTrue(stale.isCancelled());
    }

    @Test
    void malformedOpenFrameKeepsTheOriginalCompiledViewUsable() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        AtomicInteger retainedActions = new AtomicInteger();
        runtime.open(player, malformedOpenFrameMenu(retainedActions));
        Inventory inventory = player.lastOpenedInventory();

        InventoryPreClickEvent malformed = new InventoryPreClickEvent(inventory, player, new Click.Left(10));
        runtime.onInventoryPreClick(malformed);

        assertTrue(malformed.isCancelled());
        assertSame(inventory, player.getOpenInventory());
        assertEquals("Malformed Frame", flatten(inventory.getTitle()));
        drainScheduled(scheduled);

        InventoryPreClickEvent retained = new InventoryPreClickEvent(inventory, player, new Click.Left(11));
        runtime.onInventoryPreClick(retained);

        assertTrue(retained.isCancelled());
        assertEquals(1, retainedActions.get());
        assertSame(inventory, player.getOpenInventory());
    }

    @Test
    void throwingChildStateFactoryRestoresTheSettledCustodyView() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        Menu queuedSuccessor = new StandardMenuService().canvas()
                .title("Must Not Escape Failed Factory")
                .rows(3)
                .build();
        ReactiveMenu badChild = new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> {
                    runtime.open(player, queuedSuccessor);
                    throw new IllegalStateException("intentional child state factory failure");
                })
                .render(state -> ReactiveMenuView.builder("Never Rendered").build())
                .reduce((state, input) -> ReactiveMenuResult.unchanged())
                .build();
        runtime.open(player, reactiveCustodyNavigationMenu(new ReactiveMenuEffect.Open(badChild)));
        Inventory inventory = player.lastOpenedInventory();
        ItemStack original = richMinestomItem(Material.DIAMOND, "Factory Settlement", 3);
        player.getInventory().setItemStack(3, original);
        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(3)));
        drainScheduled(scheduled);
        assertEquals(original, inventory.getItemStack(31));

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(22)));
        drainScheduled(scheduled);

        assertSame(inventory, player.getOpenInventory());
        assertEquals(1, player.openCount());
        assertEquals(original, player.getInventory().getItemStack(3));
        assertEquals("Custody Target", slotTitle(inventory, 31));
        ItemStack second = richMinestomItem(Material.EMERALD, "Factory Recovery", 2);
        player.getInventory().setItemStack(4, second);
        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(4)));
        assertEquals(second, inventory.getItemStack(31));
    }

    @Test
    void throwingReplacementRendererRestoresTheSettledCustodyView() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        ReactiveMenu badReplacement = new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> 0)
                .render(state -> {
                    throw new IllegalStateException("intentional replacement renderer failure");
                })
                .reduce((state, input) -> ReactiveMenuResult.unchanged())
                .build();
        runtime.open(player, reactiveCustodyNavigationMenu(new ReactiveMenuEffect.Replace(badReplacement)));
        Inventory inventory = player.lastOpenedInventory();
        ItemStack original = richMinestomItem(Material.AMETHYST_SHARD, "Renderer Settlement", 4);
        player.getInventory().setItemStack(5, original);
        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(5)));
        drainScheduled(scheduled);
        assertEquals(original, inventory.getItemStack(31));

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(22)));

        assertSame(inventory, player.getOpenInventory());
        assertEquals(original, player.getInventory().getItemStack(5));
        assertEquals("Custody Target", slotTitle(inventory, 31));
        drainScheduled(scheduled);
        ItemStack second = richMinestomItem(Material.NETHER_STAR, "Renderer Recovery", 1);
        player.getInventory().setItemStack(6, second);
        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(6)));
        assertEquals(second, inventory.getItemStack(31));
    }

    @Test
    void failedSettledViewRestorationQuarantinesInsteadOfLeavingAHeadlessSession() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        ReactiveMenu badChild = new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> {
                    throw new IllegalStateException("intentional child preparation failure");
                })
                .render(state -> ReactiveMenuView.builder("Never Rendered").build())
                .reduce((state, input) -> ReactiveMenuResult.unchanged())
                .build();
        AtomicBoolean failEmptyRender = new AtomicBoolean();
        ReactiveMenu source = new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> new CustodyState(MenuCustodySnapshot.EMPTY, false))
                .custodyTarget("center", 31)
                .custodyPolicy((state, gesture, snapshot) -> {
                    if (gesture instanceof MenuCustodyGesture.ViewerClick viewerClick
                            && viewerClick.slot().item() != null
                            && snapshot.empty()) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.target("center"));
                    }
                    return MenuCustodyDecision.reject();
                })
                .render(state -> {
                    if (failEmptyRender.get() && state.snapshot().empty()) {
                        throw new IllegalStateException("intentional settled-view renderer failure");
                    }
                    return ReactiveMenuView.builder("Fragile Custody Navigation")
                            .place(22, MenuButton.builder(MenuIcon.vanilla("chest"))
                                    .name("Navigate")
                                    .emit(ActionVerb.OPEN, "navigate")
                                    .build())
                            .place(31, state.snapshot().targets().containsKey("center")
                                    ? state.snapshot().targets().get("center").presentation()
                                    : MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                            .name("Custody Target")
                                            .build())
                            .build();
                })
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.CustodyCommitted committed) {
                        return ReactiveMenuResult.update(new CustodyState(committed.snapshot(), false));
                    }
                    if (input instanceof ReactiveMenuInput.Click click
                            && "navigate".equals(click.message())) {
                        return ReactiveMenuResult.effect(new ReactiveMenuEffect.Open(badChild));
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
        runtime.open(player, source);
        Inventory inventory = player.lastOpenedInventory();
        ItemStack original = richMinestomItem(Material.DIAMOND, "Quarantine Settlement", 2);
        player.getInventory().setItemStack(2, original);
        runtime.onInventoryPreClick(new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(2)));
        drainScheduled(scheduled);
        failEmptyRender.set(true);

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(22)));

        assertEquals(original, player.getInventory().getItemStack(2));
        assertNull(player.getOpenInventory());
        InventoryPreClickEvent stale = new InventoryPreClickEvent(inventory, player, new Click.Left(22));
        runtime.onInventoryPreClick(stale);
        assertTrue(stale.isCancelled());

        runtime.open(player, new StandardMenuService().canvas()
                .title("Recovered After Quarantine")
                .rows(3)
                .build());
        assertEquals("Recovered After Quarantine", flatten(player.lastOpenedInventory().getTitle()));
    }

    @Test
    void ordinaryReactiveReducerFailureQuarantinesTheSession() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();
        ReactiveMenu menu = new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> 0)
                .render(state -> ReactiveMenuView.builder("Faulty Click")
                        .place(22, MenuDisplayItem.builder(MenuIcon.vanilla("stone"))
                                .name("Explode")
                                .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.Click) {
                        throw new IllegalStateException("intentional click reducer failure");
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
        runtime.open(player, menu);
        Inventory inventory = player.lastOpenedInventory();
        ItemStack retained = richMinestomItem(Material.EMERALD, "Retained", 2);
        player.getInventory().setItemStack(5, retained);

        InventoryPreClickEvent click = new InventoryPreClickEvent(inventory, player, new Click.Left(22));
        runtime.onInventoryPreClick(click);

        assertTrue(click.isCancelled());
        assertNull(player.getOpenInventory());
        assertEquals(retained, player.getInventory().getItemStack(5));
        InventoryPreClickEvent stale = new InventoryPreClickEvent(inventory, player, new Click.Left(22));
        runtime.onInventoryPreClick(stale);
        assertTrue(stale.isCancelled());
    }

    @Test
    void reactiveReducerRootReplacementCannotRenderItsOldOutcomeIntoTheSuccessor() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        Menu replacement = new StandardMenuService().canvas()
                .title("Reducer Successor")
                .rows(3)
                .place(13, MenuDisplayItem.builder(MenuIcon.vanilla("emerald"))
                        .name("Successor Content")
                        .build())
                .build();
        ReactiveMenu menu = new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> 0)
                .render(state -> ReactiveMenuView.builder("Reducer Source")
                        .place(22, MenuDisplayItem.builder(MenuIcon.vanilla("stone"))
                                .name("Old State " + state)
                                .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.Click) {
                        runtime.open(player, replacement);
                        return ReactiveMenuResult.update(state + 1);
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
        runtime.open(player, menu);
        Inventory sourceInventory = player.lastOpenedInventory();

        runtime.onInventoryPreClick(new InventoryPreClickEvent(sourceInventory, player, new Click.Left(22)));

        assertSame(sourceInventory, player.getOpenInventory());
        assertEquals("Old State 0", slotTitle(sourceInventory, 22));

        drainScheduled(scheduled);

        Inventory successor = player.lastOpenedInventory();
        assertNotSame(sourceInventory, successor);
        assertEquals("Reducer Successor", flatten(successor.getTitle()));
        assertEquals("Successor Content", slotTitle(successor, 13));
    }

    @Test
    void initialRendererRootReplacementNeverOpensAnOrphanInventory() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        Menu successor = new StandardMenuService().canvas()
                .title("Initial Render Successor")
                .rows(3)
                .place(13, MenuDisplayItem.builder(MenuIcon.vanilla("emerald"))
                        .name("Only Opened Inventory")
                        .build())
                .build();
        AtomicBoolean replaced = new AtomicBoolean();
        ReactiveMenu source = new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> 0)
                .render(state -> {
                    if (replaced.compareAndSet(false, true)) {
                        runtime.open(player, successor);
                    }
                    return ReactiveMenuView.builder("Orphan Candidate")
                            .place(22, MenuDisplayItem.builder(MenuIcon.vanilla("stone"))
                                    .name("Must Never Open")
                                    .build())
                            .build();
                })
                .reduce((state, input) -> ReactiveMenuResult.unchanged())
                .build();

        runtime.open(player, source);

        assertNull(player.getOpenInventory());
        assertEquals(0, player.openCount());

        drainScheduled(scheduled);

        Inventory opened = player.lastOpenedInventory();
        assertEquals(1, player.openCount());
        assertEquals("Initial Render Successor", flatten(opened.getTitle()));
        assertEquals("Only Opened Inventory", slotTitle(opened, 13));
    }

    @Test
    void failedInitialRendererDropsItsDeferredRootReplacement() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        AtomicInteger previousActions = new AtomicInteger();
        Menu previous = new StandardMenuService().canvas()
                .title("Previous")
                .rows(3)
                .place(10, MenuButton.builder(MenuIcon.vanilla("lever"))
                        .name("Previous Action")
                        .action(ActionVerb.VIEW, context -> previousActions.incrementAndGet())
                        .build())
                .build();
        Menu successor = new StandardMenuService().canvas()
                .title("Failed Render Successor")
                .rows(3)
                .build();
        ReactiveMenu source = new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> 0)
                .render(state -> {
                    runtime.open(player, successor);
                    throw new IllegalStateException("intentional renderer failure");
                })
                .reduce((state, input) -> ReactiveMenuResult.unchanged())
                .build();
        runtime.open(player, previous);
        Inventory previousInventory = player.lastOpenedInventory();

        runtime.open(player, source);
        drainScheduled(scheduled);

        assertSame(previousInventory, player.getOpenInventory());
        assertEquals(1, player.openCount());
        assertEquals("Previous", flatten(player.lastOpenedInventory().getTitle()));
        InventoryPreClickEvent click = new InventoryPreClickEvent(
                previousInventory,
                player,
                new Click.Left(10));
        runtime.onInventoryPreClick(click);
        assertTrue(click.isCancelled());
        assertEquals(1, previousActions.get());
    }

    @Test
    void transitionRendererRootReplacementCannotExposeTheAbortedChildInventory() {
        TestPlayer player = player();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), scheduled);
        Menu successor = new StandardMenuService().canvas()
                .title("Transition Render Successor")
                .rows(3)
                .place(13, MenuDisplayItem.builder(MenuIcon.vanilla("emerald"))
                        .name("Successor Content")
                        .build())
                .build();
        AtomicBoolean replaced = new AtomicBoolean();
        ReactiveMenu child = new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> 0)
                .render(state -> {
                    if (replaced.compareAndSet(false, true)) {
                        runtime.open(player, successor);
                    }
                    return ReactiveMenuView.builder("Aborted Child")
                            .place(22, MenuDisplayItem.builder(MenuIcon.vanilla("diamond"))
                                    .name("Must Never Open")
                                    .build())
                            .build();
                })
                .reduce((state, input) -> ReactiveMenuResult.unchanged())
                .build();
        ReactiveMenu root = new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> 0)
                .render(state -> ReactiveMenuView.builder("Transition Root")
                        .place(22, MenuDisplayItem.builder(MenuIcon.vanilla("stone"))
                                .name("Open Child")
                                .build())
                        .build())
                .reduce((state, input) -> input instanceof ReactiveMenuInput.Click
                        ? ReactiveMenuResult.effect(new ReactiveMenuEffect.Open(child))
                        : ReactiveMenuResult.unchanged())
                .build();
        runtime.open(player, root);
        Inventory rootInventory = player.lastOpenedInventory();

        InventoryPreClickEvent click = new InventoryPreClickEvent(rootInventory, player, new Click.Left(22));
        runtime.onInventoryPreClick(click);

        assertTrue(click.isCancelled());
        assertSame(rootInventory, player.getOpenInventory());
        assertEquals(1, player.openCount());
        assertEquals("Transition Root", flatten(rootInventory.getTitle()));

        drainScheduled(scheduled);

        Inventory opened = player.lastOpenedInventory();
        assertEquals(2, player.openCount());
        assertEquals("Transition Render Successor", flatten(opened.getTitle()));
        assertEquals("Successor Content", slotTitle(opened, 13));

        InventoryPreClickEvent stale = new InventoryPreClickEvent(rootInventory, player, new Click.Left(22));
        runtime.onInventoryPreClick(stale);
        assertTrue(stale.isCancelled());
        assertSame(opened, player.getOpenInventory());
    }

    @Test
    void reactiveOpenedReducerFailureQuarantinesTheSession() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();
        ReactiveMenu menu = new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> 0)
                .render(state -> ReactiveMenuView.builder("Faulty Opened").build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.Opened) {
                        throw new IllegalStateException("intentional opened reducer failure");
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();

        runtime.open(player, menu);

        Inventory inventory = player.lastOpenedInventory();
        assertNull(player.getOpenInventory());
        InventoryPreClickEvent stale = new InventoryPreClickEvent(inventory, player, new Click.Left(22));
        runtime.onInventoryPreClick(stale);
        assertTrue(stale.isCancelled());
    }

    @Test
    void reactiveRenderFailureDuringClickQuarantinesTheSession() {
        TestPlayer player = player();
        MinestomMenuRuntime runtime = runtime();
        ReactiveMenu menu = new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> 0)
                .render(state -> {
                    if (state == 1) {
                        throw new IllegalStateException("intentional render failure");
                    }
                    return ReactiveMenuView.builder("Faulty Render")
                            .place(22, MenuDisplayItem.builder(MenuIcon.vanilla("stone"))
                                    .name("Render Next")
                                    .build())
                            .build();
                })
                .reduce((state, input) -> input instanceof ReactiveMenuInput.Click
                        ? ReactiveMenuResult.update(1)
                        : ReactiveMenuResult.unchanged())
                .build();
        runtime.open(player, menu);
        Inventory inventory = player.lastOpenedInventory();

        InventoryPreClickEvent click = new InventoryPreClickEvent(inventory, player, new Click.Left(22));
        runtime.onInventoryPreClick(click);

        assertTrue(click.isCancelled());
        assertNull(player.getOpenInventory());
        InventoryPreClickEvent stale = new InventoryPreClickEvent(inventory, player, new Click.Left(22));
        runtime.onInventoryPreClick(stale);
        assertTrue(stale.isCancelled());
    }

    @Test
    void tickReducerFailureQuarantinesTheSession() {
        AtomicReference<Runnable> tickAction = new AtomicReference<>();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(
                new MinestomMenuRenderer(),
                new RecordingSoundCueService(),
                (intervalTicks, action) -> {
                    tickAction.set(action);
                    return () -> tickAction.compareAndSet(action, null);
                },
                nextTickScheduler(new ArrayDeque<>()),
                new MenuTraceController(),
                message -> { });
        TestPlayer player = player();
        runtime.open(player, reactiveFaultyTickMenu());
        Inventory inventory = player.lastOpenedInventory();

        tickAction.get().run();

        assertNull(player.getOpenInventory());
        assertNull(tickAction.get());
        InventoryPreClickEvent stale = new InventoryPreClickEvent(inventory, player, new Click.Left(22));
        runtime.onInventoryPreClick(stale);
        assertTrue(stale.isCancelled());
    }

    @Test
    void unchangedTickUsesCachedTraceTitleWithoutInvokingTheRenderer() {
        AtomicReference<Runnable> tickAction = new AtomicReference<>();
        MenuTraceController trace = new MenuTraceController();
        trace.traceAll();
        List<String> logs = new ArrayList<>();
        MinestomMenuRuntime runtime = new MinestomMenuRuntime(
                new MinestomMenuRenderer(),
                new RecordingSoundCueService(),
                (intervalTicks, action) -> {
                    tickAction.set(action);
                    return () -> tickAction.compareAndSet(action, null);
                },
                nextTickScheduler(new ArrayDeque<>()),
                trace,
                logs::add);
        TestPlayer player = player();
        Menu unexpectedSuccessor = new StandardMenuService().canvas()
                .title("Unexpected Trace Reentry")
                .rows(3)
                .build();
        AtomicInteger renderCalls = new AtomicInteger();
        ReactiveMenu menu = new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> 0)
                .tickEvery(1L)
                .render(state -> {
                    if (renderCalls.incrementAndGet() > 1) {
                        runtime.open(player, unexpectedSuccessor);
                    }
                    return ReactiveMenuView.builder("Cached Tick Title")
                            .place(22, MenuDisplayItem.builder(MenuIcon.vanilla("clock"))
                                    .name("Unchanged")
                                    .build())
                            .build();
                })
                .reduce((state, input) -> ReactiveMenuResult.unchanged())
                .build();
        runtime.open(player, menu);
        Inventory inventory = player.lastOpenedInventory();
        logs.clear();

        tickAction.get().run();

        assertEquals(1, renderCalls.get());
        assertSame(inventory, player.getOpenInventory());
        assertEquals(1, player.openCount());
        assertEquals("Cached Tick Title", flatten(inventory.getTitle()));
        assertTrue(logs.stream().anyMatch(line -> line.startsWith("summary ")
                && line.contains("cause=")
                && line.contains("Cached Tick Title")));
    }

    @Test
    void unchangedReactiveDispatchSkipsInventoryRendering() {
        TestPlayer player = player();
        MenuTraceController trace = new MenuTraceController();
        trace.traceAll();
        List<String> logs = new ArrayList<>();
        MinestomMenuRuntime runtime = runtime(new RecordingSoundCueService(), trace, logs, new ArrayDeque<>());
        ReactiveMenu menu = new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> 0)
                .render(ignored -> ReactiveMenuView.builder("Unchanged")
                        .place(22, MenuDisplayItem.builder(MenuIcon.vanilla("stone"))
                                .name("No Change")
                                .build())
                        .build())
                .reduce((state, input) -> ReactiveMenuResult.unchanged())
                .build();
        runtime.open(player, menu);
        Inventory inventory = player.lastOpenedInventory();
        logs.clear();

        runtime.onInventoryPreClick(new InventoryPreClickEvent(inventory, player, new Click.Left(22)));

        String summary = summaryLine(logs, "click");
        assertTrue(summary.contains("runtime.reactiveDispatch="));
        assertFalse(summary.contains("runtime.inventoryPatch="));
        assertSame(inventory, player.lastOpenedInventory());
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
        inventory = player.lastOpenedInventory();

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

        assertSame(firstPage, secondPage);
        assertEquals(1, player.openCount());
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

    private static Menu malformedOpenFrameMenu(AtomicInteger retainedActions) {
        Menu delegate = new StandardMenuService().canvas()
                .title("Malformed Frame")
                .rows(3)
                .place(10, MenuButton.builder(MenuIcon.vanilla("barrier"))
                        .name("Broken Frame")
                        .action(ActionVerb.OPEN, context -> { })
                        .build())
                .place(11, MenuButton.builder(MenuIcon.vanilla("lever"))
                        .name("Retained Action")
                        .action(ActionVerb.VIEW, context -> retainedActions.incrementAndGet())
                        .build())
                .build();
        MenuFrame delegateFrame = delegate.initialFrame();
        List<MenuSlot> slots = new ArrayList<>(delegateFrame.slots());
        MenuSlot template = slots.get(10);
        slots.set(10, new MenuSlot(
                template.slot(),
                template.icon(),
                template.title(),
                template.lore(),
                template.glow(),
                Map.of(MenuClick.LEFT, MenuInteraction.of(
                        ActionVerb.OPEN,
                        new MenuSlotAction.OpenFrame("broken"))),
                template.amount(),
                template.tooltipBehavior(),
                template.replaceableLoreLineCount()));
        MenuFrame rootFrame = new MenuFrame(delegateFrame.title(), slots);
        return new Menu() {
            @Override
            public Component title() {
                return delegate.title();
            }

            @Override
            public String initialFrameId() {
                return delegate.initialFrameId();
            }

            @Override
            public Set<String> frameIds() {
                return Set.of(delegate.initialFrameId(), "broken");
            }

            @Override
            public MenuFrame frame(String frameId) {
                if ("broken".equals(frameId)) {
                    throw new IllegalStateException("intentional malformed frame");
                }
                if (delegate.initialFrameId().equals(frameId)) {
                    return rootFrame;
                }
                return delegate.frame(frameId);
            }

            @Override
            public MenuGeometry geometry() {
                return delegate.geometry();
            }

            @Override
            public int rows() {
                return delegate.rows();
            }
        };
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

    private static ReactiveMenu reactivePromptMenu() {
        return new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> "Initial")
                .render(value -> ReactiveMenuView.builder("Prompt")
                        .place(22, MenuButton.builder(MenuIcon.vanilla("name_tag"))
                                .name("Value: " + value)
                                .emit(ActionVerb.SELECT, "prompt")
                                .build())
                        .build())
                .reduce((value, input) -> {
                    if (input instanceof ReactiveMenuInput.Click click && "prompt".equals(click.message())) {
                        return ReactiveMenuResult.effect(
                                new ReactiveMenuEffect.RequestTextPrompt(
                                        sh.harold.library.menu.ReactiveTextPromptRequest.chat(
                                                "value",
                                                "Enter a value.",
                                                value)));
                    }
                    if (input instanceof ReactiveMenuInput.TextPromptSubmitted submitted) {
                        return ReactiveMenuResult.update(submitted.value());
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
    }

    private static ReactiveMenu reactiveCustodyNavigationMenu(ReactiveMenuEffect navigation) {
        return new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> new CustodyState(MenuCustodySnapshot.EMPTY, false))
                .custodyTarget("center", 31)
                .custodyPolicy((state, gesture, snapshot) -> {
                    if (gesture instanceof MenuCustodyGesture.ViewerClick viewerClick
                            && viewerClick.slot().item() != null
                            && snapshot.empty()) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.target("center"));
                    }
                    return MenuCustodyDecision.reject();
                })
                .render(state -> ReactiveMenuView.builder("Custody Navigation")
                        .place(22, MenuButton.builder(MenuIcon.vanilla("chest"))
                                .name("Navigate")
                                .emit(ActionVerb.OPEN, "navigate")
                                .build())
                        .place(31, state.snapshot().targets().containsKey("center")
                                ? state.snapshot().targets().get("center").presentation()
                                : MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                        .name("Custody Target")
                                        .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.CustodyCommitted committed) {
                        return ReactiveMenuResult.update(new CustodyState(committed.snapshot(), false));
                    }
                    if (input instanceof ReactiveMenuInput.Click click
                            && "navigate".equals(click.message())) {
                        return ReactiveMenuResult.effect(navigation);
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
    }

    private static ReactiveMenu reactiveFaultyPromptMenu() {
        return new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> "Initial")
                .render(value -> ReactiveMenuView.builder("Faulty Prompt")
                        .place(22, MenuButton.builder(MenuIcon.vanilla("name_tag"))
                                .name("Open Prompt")
                                .emit(ActionVerb.SELECT, "prompt")
                                .build())
                        .build())
                .reduce((value, input) -> {
                    if (input instanceof ReactiveMenuInput.Click click && "prompt".equals(click.message())) {
                        return ReactiveMenuResult.effect(new ReactiveMenuEffect.RequestTextPrompt(
                                sh.harold.library.menu.ReactiveTextPromptRequest.chat(
                                        "value",
                                        "Enter a value.",
                                        value)));
                    }
                    if (input instanceof ReactiveMenuInput.TextPromptSubmitted) {
                        throw new IllegalStateException("intentional prompt reducer failure");
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
    }

    private static ReactiveMenu reactiveCustodyPromptMenu() {
        return new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> new CustodyPromptState(MenuCustodySnapshot.EMPTY, "Initial"))
                .custodyTarget("center", 31)
                .custodyPolicy((state, gesture, snapshot) -> {
                    if (gesture instanceof MenuCustodyGesture.ViewerClick viewerClick
                            && viewerClick.slot().item() != null
                            && snapshot.targets().isEmpty()
                            && snapshot.cursor().isEmpty()) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.target("center"));
                    }
                    if (gesture instanceof MenuCustodyGesture.TargetClick
                            && snapshot.targets().containsKey("center")
                            && snapshot.cursor().isEmpty()) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.origin());
                    }
                    return MenuCustodyDecision.reject();
                })
                .render(state -> ReactiveMenuView.builder("Custody Prompt")
                        .place(22, MenuButton.builder(MenuIcon.vanilla("name_tag"))
                                .name("Value: " + state.value())
                                .emit(ActionVerb.SELECT, "prompt")
                                .build())
                        .place(31, state.snapshot().targets().containsKey("center")
                                ? state.snapshot().targets().get("center").presentation()
                                : MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                        .name("Custody Target")
                                        .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.CustodyCommitted committed) {
                        return ReactiveMenuResult.update(new CustodyPromptState(
                                committed.snapshot(),
                                state.value()));
                    }
                    if (input instanceof ReactiveMenuInput.Click click && "prompt".equals(click.message())) {
                        return ReactiveMenuResult.effect(new ReactiveMenuEffect.RequestTextPrompt(
                                sh.harold.library.menu.ReactiveTextPromptRequest.chat(
                                        "value",
                                        "Enter a value.",
                                        state.value())));
                    }
                    if (input instanceof ReactiveMenuInput.TextPromptSubmitted submitted) {
                        return ReactiveMenuResult.update(new CustodyPromptState(
                                state.snapshot(),
                                submitted.value()));
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
    }

    private static ReactiveMenu reactiveClickInsertMenu(boolean locked) {
        return new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> new CustodyState(MenuCustodySnapshot.EMPTY, locked))
                .custodyTarget("center", 31)
                .custodyPolicy((state, gesture, snapshot) -> {
                    if (state.locked()) {
                        return MenuCustodyDecision.reject();
                    }
                    if (gesture instanceof MenuCustodyGesture.ViewerClick viewerClick
                            && viewerClick.slot().item() != null
                            && snapshot.targets().isEmpty()
                            && snapshot.cursor().isEmpty()) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.target("center"));
                    }
                    if (gesture instanceof MenuCustodyGesture.TargetClick
                            && snapshot.targets().containsKey("center")
                            && snapshot.cursor().isEmpty()) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.origin());
                    }
                    return MenuCustodyDecision.reject();
                })
                .render(state -> ReactiveMenuView.builder("Reactive Click")
                        .place(13, MenuDisplayItem.builder(MenuIcon.vanilla("hopper"))
                                .name("Click Insert")
                                .description("Click a bottom inventory stack to load it into the center slot, then click the loaded slot to return it to the same source slot.")
                                .build())
                        .place(31, state.snapshot().targets().containsKey("center")
                                ? state.snapshot().targets().get("center").presentation()
                                : MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                        .name("Click An Inventory Stack")
                                        .description("The source slot clears when the stack loads.")
                                        .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.CustodyCommitted committed) {
                        return ReactiveMenuResult.update(new CustodyState(committed.snapshot(), state.locked()));
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
    }

    private static ReactiveMenu reactiveUnchangedCustodyMenu() {
        return new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> 0)
                .custodyTarget("center", 31)
                .custodyPolicy((state, gesture, snapshot) -> {
                    if (gesture instanceof MenuCustodyGesture.ViewerClick viewerClick
                            && viewerClick.slot().item() != null
                            && snapshot.targets().isEmpty()
                            && snapshot.cursor().isEmpty()) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.target("center"));
                    }
                    if (gesture instanceof MenuCustodyGesture.TargetClick
                            && snapshot.targets().containsKey("center")
                            && snapshot.cursor().isEmpty()) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.origin());
                    }
                    return MenuCustodyDecision.reject();
                })
                .render(state -> ReactiveMenuView.builder("Unchanged Custody")
                        .place(31, MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                .name("Empty Custody Target")
                                .build())
                        .build())
                .reduce((state, input) -> ReactiveMenuResult.unchanged())
                .build();
    }

    private static ReactiveMenu reactiveRefreshMenu(AtomicBoolean enabled) {
        return new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> enabled)
                .render(state -> ReactiveMenuView.builder("Reactive Refresh")
                        .place(22, MenuButton.builder(MenuIcon.vanilla("lever"))
                                .name(state.get() ? "Reactive Refresh: On" : "Reactive Refresh: Off")
                                .action(ActionVerb.TOGGLE, context -> {
                                    state.set(!state.get());
                                    context.refresh();
                                })
                                .build())
                        .build())
                .reduce((state, input) -> ReactiveMenuResult.unchanged())
                .build();
    }

    private static ReactiveMenu reactiveDragInsertMenu(boolean locked) {
        return new StandardMenuService().reactiveCanvas()
                .fillWithBlackPane(false)
                .stateFactory(() -> new CustodyState(MenuCustodySnapshot.EMPTY, locked))
                .custodyTarget("center", 31)
                .custodyPolicy((state, gesture, snapshot) -> dragCustodyDecision(state, gesture, snapshot))
                .render(state -> {
                    ReactiveMenuView.Builder builder = ReactiveMenuView.builder("Reactive Drag")
                            .place(13, MenuDisplayItem.builder(MenuIcon.vanilla("hopper"))
                                    .name("Shift Or Drag")
                                    .description("Shift-click or click a bottom inventory stack to place the exact runtime-owned stack into the center slot.")
                                    .build());
                    if (state.snapshot().targets().containsKey("center")) {
                        builder.place(31, state.snapshot().targets().get("center").presentation());
                    }
                    return builder.build();
                })
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.CustodyCommitted committed) {
                        return ReactiveMenuResult.update(new CustodyState(committed.snapshot(), state.locked()));
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
    }

    private static ReactiveMenu reactiveTwoTargetDragMenu() {
        return new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> new CustodyState(MenuCustodySnapshot.EMPTY, false))
                .custodyTarget("left", 31)
                .custodyTarget("right", 32)
                .custodyPolicy((state, gesture, snapshot) -> {
                    if (gesture instanceof MenuCustodyGesture.ViewerClick viewerClick
                            && viewerClick.slot().item() != null
                            && snapshot.cursor().isEmpty()) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.cursor());
                    }
                    if (gesture instanceof MenuCustodyGesture.TargetDrag) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.target("left"));
                    }
                    if (gesture instanceof MenuCustodyGesture.OutsideClick
                            && snapshot.cursor().isPresent()) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.origin());
                    }
                    return MenuCustodyDecision.reject();
                })
                .render(state -> ReactiveMenuView.builder("Two Targets")
                        .place(31, MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                .name("Left Target")
                                .build())
                        .place(32, MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                .name("Right Target")
                                .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.CustodyCommitted committed) {
                        return ReactiveMenuResult.update(new CustodyState(committed.snapshot(), false));
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
    }

    private static ReactiveMenu reactiveFaultyCustodyMenu() {
        return new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> MenuCustodySnapshot.EMPTY)
                .custodyTarget("center", 31)
                .custodyPolicy((state, gesture, snapshot) -> {
                    if (gesture instanceof MenuCustodyGesture.ViewerClick viewerClick
                            && viewerClick.slot().item() != null
                            && snapshot.targets().isEmpty()) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.target("center"));
                    }
                    return MenuCustodyDecision.reject();
                })
                .render(state -> ReactiveMenuView.builder("Faulty Custody")
                        .place(31, MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                .name("Custody Target")
                                .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.CustodyCommitted) {
                        throw new IllegalStateException("intentional reducer failure");
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
    }

    private static ReactiveMenu reactiveFaultyCustodyRejectedMenu() {
        return new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> MenuCustodySnapshot.EMPTY)
                .custodyTarget("center", 31)
                .custodyPolicy((state, gesture, snapshot) -> MenuCustodyDecision.reject())
                .render(state -> ReactiveMenuView.builder("Faulty Rejection")
                        .place(31, MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                .name("Custody Target")
                                .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.CustodyRejected) {
                        throw new IllegalStateException("intentional rejection reducer failure");
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
    }

    private static ReactiveMenu reactiveFaultyTickMenu() {
        return new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> 0)
                .tickEvery(1L)
                .render(state -> ReactiveMenuView.builder("Faulty Tick").build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.Tick) {
                        throw new IllegalStateException("intentional tick reducer failure");
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
    }

    private static MenuCustodyDecision dragCustodyDecision(
            CustodyState state,
            MenuCustodyGesture gesture,
            MenuCustodySnapshot snapshot
    ) {
        if (state.locked()) {
            return MenuCustodyDecision.reject();
        }
        if (gesture instanceof MenuCustodyGesture.ViewerClick viewerClick) {
            if (snapshot.cursor().isPresent() && viewerClick.slot().item() == null) {
                return MenuCustodyDecision.move(MenuCustodyDestination.viewerSlot(viewerClick.slot()));
            }
            if (snapshot.cursor().isEmpty() && viewerClick.slot().item() != null) {
                return MenuCustodyDecision.move(viewerClick.shift()
                        ? MenuCustodyDestination.target("center")
                        : MenuCustodyDestination.cursor());
            }
        }
        if (gesture instanceof MenuCustodyGesture.TargetDrag targetDrag
                && snapshot.cursor().isPresent()
                && targetDrag.targetKeys().contains("center")) {
            return MenuCustodyDecision.move(MenuCustodyDestination.target("center"));
        }
        if (gesture instanceof MenuCustodyGesture.TargetClick targetClick) {
            if (snapshot.cursor().isPresent() && !snapshot.targets().containsKey(targetClick.targetKey())) {
                return MenuCustodyDecision.move(MenuCustodyDestination.target(targetClick.targetKey()));
            }
            if (snapshot.cursor().isEmpty() && snapshot.targets().containsKey(targetClick.targetKey())) {
                return MenuCustodyDecision.move(targetClick.shift()
                        ? MenuCustodyDestination.origin()
                        : MenuCustodyDestination.cursor());
            }
        }
        if (gesture instanceof MenuCustodyGesture.OutsideClick && snapshot.cursor().isPresent()) {
            return MenuCustodyDecision.move(MenuCustodyDestination.origin());
        }
        return MenuCustodyDecision.reject();
    }

    private static ReactiveMenu reactiveClickRoutingMenu() {
        return new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> new StoredState(null))
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
                        return ReactiveMenuResult.update(new StoredState(counter));
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
    }

    private static ReactiveMenu reactiveListMenu(int pageIndex) {
        return new StandardMenuService().reactiveList()
                .stateFactory(() -> pageIndex)
                .render(currentPage -> ReactiveListView.builder("Profiles")
                        .page(currentPage)
                        .addItems(sampleReactiveButtons("Item", 29))
                        .build())
                .reduce((currentPage, input) -> {
                    if (!(input instanceof ReactiveMenuInput.Click click)) {
                        return ReactiveMenuResult.unchanged();
                    }
                    if (click.message() instanceof ReactiveGeometryAction.PreviousPage) {
                        return ReactiveMenuResult.update(currentPage - 1);
                    }
                    if (click.message() instanceof ReactiveGeometryAction.NextPage) {
                        return ReactiveMenuResult.update(currentPage + 1);
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
    }

    private static ReactiveMenu reactiveTabsMenu(String activeTabId, int navStart, int pageIndex) {
        return new StandardMenuService().reactiveTabs()
                .stateFactory(() -> new ReactiveTabsState(activeTabId, navStart, pageIndex))
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
                        return ReactiveMenuResult.unchanged();
                    }
                    if (click.message() instanceof ReactiveGeometryAction.PreviousTabs) {
                        return ReactiveMenuResult.update(new ReactiveTabsState(
                                state.activeTabId(),
                                Math.max(0, state.navStart() - 1),
                                state.pageIndex()));
                    }
                    if (click.message() instanceof ReactiveGeometryAction.NextTabs) {
                        return ReactiveMenuResult.update(new ReactiveTabsState(
                                state.activeTabId(),
                                state.navStart() + 1,
                                state.pageIndex()));
                    }
                    if (click.message() instanceof ReactiveGeometryAction.PreviousPage) {
                        return ReactiveMenuResult.update(new ReactiveTabsState(
                                state.activeTabId(),
                                state.navStart(),
                                Math.max(0, state.pageIndex() - 1)));
                    }
                    if (click.message() instanceof ReactiveGeometryAction.NextPage) {
                        return ReactiveMenuResult.update(new ReactiveTabsState(
                                state.activeTabId(),
                                state.navStart(),
                                state.pageIndex() + 1));
                    }
                    if (click.message() instanceof ReactiveGeometryAction.SwitchTab switchTab) {
                        return ReactiveMenuResult.update(new ReactiveTabsState(switchTab.tabId(), state.navStart(), 0));
                    }
                    return ReactiveMenuResult.unchanged();
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

    private static InstanceContainer overflowInstance() {
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.enableAutoChunkLoad(true);
        instance.loadChunk(0, 0).join();
        return instance;
    }

    private static void destroyInstance(Instance instance) {
        List.copyOf(instance.getEntities()).forEach(Entity::remove);
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    private static List<ItemEntity> itemEntities(Instance instance) {
        return instance.getEntities().stream()
                .filter(ItemEntity.class::isInstance)
                .map(ItemEntity.class::cast)
                .toList();
    }

    private static void fillStorage(TestPlayer player) {
        for (int slot = 0; slot < PlayerInventory.INNER_INVENTORY_SIZE; slot++) {
            player.getInventory().setItemStack(slot, namedMinestomItem(Material.COBBLESTONE, "Filler " + slot, 1));
        }
    }

    private static void assertCustodyCopyAbsent(TestPlayer player, Inventory inventory, ItemStack original) {
        assertEquals(Material.AIR, player.getInventory().getCursorItem().material());
        assertEquals(Material.AIR, inventory.getItemStack(31).material());
        for (int slot = 0; slot < PlayerInventory.INNER_INVENTORY_SIZE; slot++) {
            assertFalse(player.getInventory().getItemStack(slot).equals(original));
        }
    }

    private static ItemStack namedMinestomItem(Material material, String name, int amount) {
        return ItemStack.of(material, amount)
                .withCustomName(Component.text(name))
                .withoutExtraTooltip();
    }

    private static ItemStack richMinestomItem(Material material, String name, int amount) {
        return namedMinestomItem(material, name, amount)
                .withLore(Component.text("Exact custody metadata"), Component.text("Amount: " + amount))
                .withCustomModelData(List.of(17.0f), List.of(true), List.of("custody"), List.of())
                .withGlowing(true)
                .withMaxStackSize(16);
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
        private Consumer<Inventory> beforeNextOpen;
        private boolean rejectNextOpen;

        private TestPlayer(UUID uuid) {
            super(new TestPlayerConnection(), new GameProfile(uuid, "menu-test"));
        }

        private TestPlayer(UUID uuid, PlayerInventory inventory) {
            this(uuid);
            this.inventory = inventory;
        }

        @Override
        public boolean openInventory(Inventory inventory) {
            if (rejectNextOpen) {
                rejectNextOpen = false;
                return false;
            }
            Consumer<Inventory> beforeOpen = beforeNextOpen;
            beforeNextOpen = null;
            if (beforeOpen != null) {
                beforeOpen.accept(inventory);
            }
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

        private void beforeNextOpen(Consumer<Inventory> action) {
            beforeNextOpen = action;
        }

        private void rejectNextOpen() {
            rejectNextOpen = true;
        }

        private void placeIn(Instance instance, Pos position) {
            this.instance = instance;
            this.position = position;
        }
    }

    private static final class FaultInjectingPlayerInventory extends PlayerInventory {

        private final List<NativeMutation> mutations = new ArrayList<>();
        private int cursorWriteFailuresRemaining;

        @Override
        public void setItemStack(int slot, ItemStack itemStack) {
            super.setItemStack(slot, itemStack);
            mutations.add(new NativeMutation("viewer", slot, itemStack));
        }

        @Override
        public void setCursorItem(ItemStack itemStack) {
            super.setCursorItem(itemStack);
            mutations.add(new NativeMutation("cursor", -1, itemStack));
            if (cursorWriteFailuresRemaining > 0) {
                cursorWriteFailuresRemaining--;
                throw new IllegalStateException("intentional cursor write failure");
            }
        }

        private void failAfterNextCursorWrite() {
            failAfterNextCursorWrites(1);
        }

        private void failAfterNextCursorWrites(int writes) {
            cursorWriteFailuresRemaining = writes;
        }

        private void clearMutationLog() {
            mutations.clear();
        }

        private List<NativeMutation> mutations() {
            return List.copyOf(mutations);
        }
    }

    private record NativeMutation(String location, int slot, ItemStack item) {
    }

    private record StoredState(MenuStack stored) {
    }

    private record CustodyState(MenuCustodySnapshot snapshot, boolean locked) {
    }

    private record CustodyPromptState(MenuCustodySnapshot snapshot, String value) {
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
        public CuePlayback play(SoundTarget target, SoundCue cue) {
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
            SoundCue cue = sh.harold.library.sound.SoundCues.sound(soundKey, 0.5f, 1.0f);
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
