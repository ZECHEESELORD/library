package sh.harold.creative.library.menu.paper;

import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import io.papermc.paper.math.Position;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
import sh.harold.creative.library.menu.ReactiveTextPromptRequest;
import sh.harold.creative.library.menu.ReactiveTabsView;
import sh.harold.creative.library.menu.ReactiveMenuView;
import sh.harold.creative.library.menu.UtilitySlot;
import sh.harold.creative.library.menu.core.MenuTickHandle;
import sh.harold.creative.library.menu.core.StandardMenuService;
import sh.harold.creative.library.sound.CuePlayback;
import sh.harold.creative.library.sound.SoundCue;
import sh.harold.creative.library.sound.SoundCueKeys;
import sh.harold.creative.library.sound.SoundCuePacks;
import sh.harold.creative.library.sound.SoundCueRegistry;
import sh.harold.creative.library.sound.SoundCueService;
import sh.harold.creative.library.sound.core.StandardSoundCueRegistry;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PaperMenuRuntimeTest {

    private static final Key SPECIAL_SOUND = Key.key("test", "menu/special");

    @Test
    void openClickNavigateAndCloseUsesOwnedInventoryIdentity() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());
        Menu menu = pagedMenu();

        runtime.open(player, menu);

        Inventory inventory = access.lastOpenedInventory();
        assertEquals("Profiles (1/3)", inventoryTitle(access, inventory));
        assertEquals("Close", slotTitle(access, inventory, 49));
        assertEquals("Next Page", slotTitle(access, inventory, 53));
        assertEquals(List.of("Page 2"), slotLore(access, inventory, 53));

        InventoryClickEvent nextPage = click(player, inventory, 53, ClickType.LEFT);
        runtime.onInventoryClick(nextPage);

        assertTrue(nextPage.isCancelled());
        Inventory secondPageInventory = access.lastOpenedInventory();
        assertNotSame(inventory, secondPageInventory);
        assertEquals("Profiles (2/3)", inventoryTitle(access, secondPageInventory));
        assertEquals("Previous Page", slotTitle(access, secondPageInventory, 45));
        assertEquals(List.of("Page 1"), slotLore(access, secondPageInventory, 45));
        assertEquals("Close", slotTitle(access, secondPageInventory, 49));
        assertEquals("Next Page", slotTitle(access, secondPageInventory, 53));
        assertEquals(List.of("Page 3"), slotLore(access, secondPageInventory, 53));

        InventoryClickEvent close = click(player, secondPageInventory, 49, ClickType.LEFT);
        runtime.onInventoryClick(close);

        assertTrue(close.isCancelled());
        assertEquals(List.of(viewerId), access.closedPlayers);
    }

    @Test
    void actionCanReplaceCurrentMenuAndRefreshRenderedContents() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());
        AtomicBoolean enabled = new AtomicBoolean(false);

        runtime.open(player, toggleMenu(enabled));
        Inventory inventory = access.lastOpenedInventory();
        assertEquals("Disabled", slotTitle(access, inventory, 10));

        runtime.onInventoryClick(click(player, inventory, 10, ClickType.LEFT));

        assertEquals("Enabled", slotTitle(access, inventory, 10));
        assertEquals(1, access.openedInventories.size());
    }

    @Test
    void refreshRebuildsReactiveMenuAfterExternalStateMutation() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());
        AtomicBoolean enabled = new AtomicBoolean(false);

        runtime.open(player, reactiveRefreshMenu(enabled));
        Inventory inventory = access.lastOpenedInventory();
        assertEquals("Reactive Refresh: Off", slotTitle(access, inventory, 22));

        runtime.onInventoryClick(click(player, inventory, 22, ClickType.LEFT));

        assertEquals("Reactive Refresh: On", slotTitle(access, inventory, 22));
        assertEquals(1, access.openedInventories.size());
    }

    @Test
    void signPromptClosesMenuOpensVirtualSignAndReopensReactiveMenuOnSubmit() {
        UUID viewerId = UUID.randomUUID();
        PaperMenuTestSupport.TrackedPlayer trackedPlayer = PaperMenuTestSupport.trackedPlayer(viewerId);
        Player player = trackedPlayer.player();
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        PaperMenuRuntime.PaperVirtualSignSupport virtualSigns = (request, allowedEditorId) ->
                new PaperMenuRuntime.PreparedVirtualSign(mock(BlockData.class), mock(TileState.class));
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(),
                new RecordingSoundCueService(), sh.harold.creative.library.menu.core.MenuTickScheduler.unsupported(),
                queuedScheduler(scheduled), virtualSigns);

        runtime.open(player, reactivePromptMenu(new PromptState("")));
        Inventory inventory = access.lastOpenedInventory();

        runtime.onInventoryClick(click(player, inventory, UtilitySlot.RIGHT_1.resolveSlot(45), ClickType.LEFT));

        assertTrue(trackedPlayer.state().openedVirtualSigns().isEmpty());

        runNextTick(scheduled);
        runNextTick(scheduled);
        assertNull(access.topInventory(player));

        runtime.onInventoryClose(new InventoryCloseEvent(view(player, inventory)));

        assertTrue(trackedPlayer.state().openedVirtualSigns().isEmpty());

        runNextTick(scheduled);

        assertEquals(List.of("block-change", "block-update", "open-virtual-sign"), trackedPlayer.state().signPromptActions());
        assertEquals(1, trackedPlayer.state().blockChanges().size());
        assertEquals(1, trackedPlayer.state().blockUpdates().size());
        assertEquals(1, trackedPlayer.state().openedVirtualSigns().size());
        assertNull(access.topInventory(player));

        PaperMenuSession session = (PaperMenuSession) inventory.getHolder(false);
        runtime.refresh(session);

        assertNull(access.topInventory(player));

        Location signLocation = trackedPlayer.state().blockChangeLocations().getFirst();
        UncheckedSignChangeEvent signChange = new UncheckedSignChangeEvent(
                player,
                Position.block(signLocation),
                Side.FRONT,
                List.of(Component.text("pain"), Component.empty(), Component.empty(), Component.empty()));
        runtime.onUncheckedSignChange(signChange);

        assertTrue(signChange.isCancelled());

        runNextTick(scheduled);

        Inventory reopened = access.lastOpenedInventory();
        assertEquals("Search: pain", slotTitle(access, reopened, UtilitySlot.RIGHT_1.resolveSlot(45)));
        assertEquals(reopened, access.topInventory(player));
    }

    @Test
    void compiledMenusIgnoreDoubleAndShiftClickVariants() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());
        AtomicInteger count = new AtomicInteger();

        runtime.open(player, counterMenu(count));
        Inventory inventory = access.lastOpenedInventory();

        InventoryClickEvent doubleClick = click(player, inventory, 10, ClickType.DOUBLE_CLICK);
        runtime.onInventoryClick(doubleClick);

        InventoryClickEvent shifted = click(player, inventory, 10, ClickType.SHIFT_LEFT);
        runtime.onInventoryClick(shifted);

        assertTrue(doubleClick.isCancelled());
        assertTrue(shifted.isCancelled());
        assertEquals(0, count.get());
    }

    @Test
    void compiledMenusAcceptOnlyOneInputPerTick() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(),
                new RecordingSoundCueService(), sh.harold.creative.library.menu.core.MenuTickScheduler.unsupported(),
                queuedScheduler(scheduled));

        runtime.open(player, dualCounterMenu(first, second));
        Inventory inventory = access.lastOpenedInventory();

        InventoryClickEvent firstClick = click(player, inventory, 10, ClickType.LEFT);
        runtime.onInventoryClick(firstClick);

        InventoryClickEvent secondClick = click(player, inventory, 11, ClickType.LEFT);
        runtime.onInventoryClick(secondClick);

        assertTrue(firstClick.isCancelled());
        assertTrue(secondClick.isCancelled());
        assertEquals(1, first.get());
        assertEquals(0, second.get());
        assertEquals(1, scheduled.size());

        runNextTick(scheduled);

        runtime.onInventoryClick(click(player, inventory, 11, ClickType.LEFT));

        assertEquals(1, first.get());
        assertEquals(1, second.get());
    }

    @Test
    void closeAndSpoofedInventoriesDoNotRouteByTitle() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());
        Menu menu = pagedMenu();

        runtime.open(player, menu);
        Inventory ownedInventory = access.lastOpenedInventory();

        Inventory spoofedInventory = access.createInventory(new InventoryHolder() {
            @Override
            public Inventory getInventory() {
                return null;
            }
        }, 54, Component.text("Profiles"));

        InventoryClickEvent spoofedClick = click(player, spoofedInventory, 53, ClickType.LEFT);
        runtime.onInventoryClick(spoofedClick);
        assertFalse(spoofedClick.isCancelled());
        assertTrue(access.closedPlayers.isEmpty());

        runtime.onInventoryClose(new InventoryCloseEvent(view(player, ownedInventory)));

        InventoryClickEvent staleClick = click(player, ownedInventory, 53, ClickType.LEFT);
        runtime.onInventoryClick(staleClick);
        assertFalse(staleClick.isCancelled());
    }

    @Test
    void disconnectCleansUpViewerSession() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());

        runtime.open(player, pagedMenu());
        runtime.onPlayerDisconnect(player);

        InventoryClickEvent click = click(player, access.lastOpenedInventory(), 53, ClickType.LEFT);
        runtime.onInventoryClick(click);
        assertFalse(click.isCancelled());
    }

    @Test
    void childBackUsesHistoryAndTabSwitchesPushFrameHistory() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());

        runtime.open(player, launcherMenu());
        Inventory rootInventory = access.lastOpenedInventory();
        assertEquals("Open Gallery", slotTitle(access, rootInventory, 10));

        InventoryClickEvent openChild = click(player, rootInventory, 10, ClickType.LEFT);
        runtime.onInventoryClick(openChild);

        assertTrue(openChild.isCancelled());
        Inventory inventory = access.lastOpenedInventory();

        assertEquals("Go Back", slotTitle(access, inventory, 48));
        assertEquals("Profiles", slotTitle(access, inventory, 3));
        assertEquals("Progress", slotTitle(access, inventory, 4));
        assertEquals("Your SkyBlock Profile", slotTitle(access, inventory, 19));

        InventoryClickEvent switchTab = click(player, inventory, 4, ClickType.LEFT);
        runtime.onInventoryClick(switchTab);

        assertTrue(switchTab.isCancelled());
        assertEquals("Profiles", slotTitle(access, inventory, 3));
        assertEquals("Progress", slotTitle(access, inventory, 4));
        assertEquals("Farming XLIX", slotTitle(access, inventory, 19));

        InventoryClickEvent backToPreviousFrame = click(player, inventory, 48, ClickType.LEFT);
        runtime.onInventoryClick(backToPreviousFrame);

        assertTrue(backToPreviousFrame.isCancelled());
        Inventory afterFrameBack = access.lastOpenedInventory();
        assertEquals("Go Back", slotTitle(access, afterFrameBack, 48));
        assertEquals("Your SkyBlock Profile", slotTitle(access, afterFrameBack, 19));

        InventoryClickEvent backToRoot = click(player, afterFrameBack, 48, ClickType.LEFT);
        runtime.onInventoryClick(backToRoot);

        assertTrue(backToRoot.isCancelled());
        Inventory finalInventory = access.lastOpenedInventory();
        assertEquals("Open Gallery", slotTitle(access, finalInventory, 10));
    }

    @Test
    void navArrowsScrollStripWithoutChangingActiveContent() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());

        runtime.open(player, overflowGalleryMenu());
        Inventory inventory = access.lastOpenedInventory();

        assertEquals("Previous Tab", slotTitle(access, inventory, 0));
        assertEquals(List.of("Page 1"), slotLore(access, inventory, 0));
        assertEquals("Next Tab", slotTitle(access, inventory, 8));
        assertEquals(List.of("Page 2"), slotLore(access, inventory, 8));
        assertEquals("Tab 0", slotTitle(access, inventory, 1));
        assertEquals("Tab 6", slotTitle(access, inventory, 7));
        assertEquals("Tab 0 Item 0", slotTitle(access, inventory, 19));

        InventoryClickEvent scrollRight = click(player, inventory, 8, ClickType.LEFT);
        runtime.onInventoryClick(scrollRight);

        assertTrue(scrollRight.isCancelled());
        assertEquals("Tab 1", slotTitle(access, inventory, 1));
        assertEquals("Tab 7", slotTitle(access, inventory, 7));
        assertEquals("Tab 0 Item 0", slotTitle(access, inventory, 19));

        InventoryClickEvent jumpEnd = click(player, inventory, 8, ClickType.RIGHT);
        runtime.onInventoryClick(jumpEnd);

        assertTrue(jumpEnd.isCancelled());
        assertEquals("Tab 3", slotTitle(access, inventory, 1));
        assertEquals("Tab 9", slotTitle(access, inventory, 7));
        assertEquals("Tab 0 Item 0", slotTitle(access, inventory, 19));

        InventoryClickEvent switchTab = click(player, inventory, 7, ClickType.LEFT);
        runtime.onInventoryClick(switchTab);

        assertTrue(switchTab.isCancelled());
        assertEquals("Tab 9 Item 0", slotTitle(access, inventory, 19));
    }

    @Test
    void pagedTabContentUsesFooterArrowsForLargeTabs() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());

        runtime.open(player, pagedTabGalleryMenu());
        Inventory inventory = access.lastOpenedInventory();

        assertEquals("Profile Item 0", slotTitle(access, inventory, 19));
        assertEquals("Next Page", slotTitle(access, inventory, 53));
        assertEquals(List.of("Page 2"), slotLore(access, inventory, 53));

        InventoryClickEvent nextPage = click(player, inventory, 53, ClickType.LEFT);
        runtime.onInventoryClick(nextPage);

        assertTrue(nextPage.isCancelled());
        assertEquals("Previous Page", slotTitle(access, inventory, 45));
        assertEquals(List.of("Page 1"), slotLore(access, inventory, 45));
        assertEquals("Profile Item 21", slotTitle(access, inventory, 19));
        assertEquals("Profile Item 28", slotTitle(access, inventory, 28));
    }

    @Test
    void canvasRoutesPlacedItemsThroughOwnedInventoryIdentity() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());
        AtomicBoolean opened = new AtomicBoolean(false);

        runtime.open(player, canvasMenu(opened));
        Inventory inventory = access.lastOpenedInventory();

        assertEquals("Museum Rewards", slotTitle(access, inventory, 13));

        InventoryClickEvent open = click(player, inventory, 13, ClickType.LEFT);
        runtime.onInventoryClick(open);

        assertTrue(open.isCancelled());
        assertTrue(opened.get());
    }

    @Test
    void interactionSoundsUseDefaultAndOverrideMappings() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        RecordingSoundCueService sounds = new RecordingSoundCueService();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), sounds);

        runtime.open(player, soundMenu());
        Inventory inventory = access.lastOpenedInventory();

        runtime.onInventoryClick(click(player, inventory, 10, ClickType.LEFT));
        runtime.onInventoryClick(click(player, inventory, 11, ClickType.LEFT));
        runtime.onInventoryClick(click(player, inventory, 12, ClickType.LEFT));

        runtime.open(player, pagedMenu());
        Inventory pagedInventory = access.lastOpenedInventory();
        runtime.onInventoryClick(click(player, pagedInventory, 53, ClickType.LEFT));

        assertEquals(List.of(
                SoundCueKeys.MENU_CLICK,
                SoundCueKeys.RESULT_CONFIRM,
                SPECIAL_SOUND,
                SoundCueKeys.MENU_SCROLL
        ), sounds.playedKeys());
    }

    @Test
    void reactiveMenusCanMoveInventoryStacksWithoutDuplicatingThem() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());

        runtime.open(player, reactiveClickInsertMenu(false));
        Inventory inventory = access.lastOpenedInventory();
        int topSize = inventory.getSize();
        ItemStack bottomItem = namedBukkitItem(Material.STONE, "Bottom Item", 3);
        playerInventory(player).setItem(5, bottomItem);

        InventoryClickEvent click = click(player, inventory, topSize + 5, ClickType.LEFT);
        runtime.onInventoryClick(click);

        assertTrue(click.isCancelled());
        assertNull(playerInventory(player).getItem(5));
        assertNull(player.getItemOnCursor());
        assertEquals("Bottom Item", slotTitle(access, inventory, 31));

        InventoryClickEvent returnClick = click(player, inventory, 31, ClickType.LEFT);
        runtime.onInventoryClick(returnClick);

        assertTrue(returnClick.isCancelled());
        assertEquals("Bottom Item", itemTitle(playerInventory(player).getItem(5)));
        assertEquals("Click An Inventory Stack", slotTitle(access, inventory, 31));
    }

    @Test
    void reactiveMenusCanMoveDraggedStacksWithoutDuplicatingThem() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());

        runtime.open(player, reactiveDragInsertMenu(false));
        Inventory inventory = access.lastOpenedInventory();
        int topSize = inventory.getSize();
        ItemStack sourceItem = namedBukkitItem(Material.EMERALD, "Dragged Item", 2);
        playerInventory(player).setItem(4, sourceItem);

        InventoryClickEvent pickup = click(player, inventory, topSize + 4, ClickType.LEFT);
        runtime.onInventoryClick(pickup);

        assertTrue(pickup.isCancelled());
        assertNull(playerInventory(player).getItem(4));

        ItemStack cursorItem = sourceItem;
        InventoryDragEvent drag = dragEvent(player, inventory, Set.of(31), cursorItem);

        runtime.onInventoryDrag(drag);

        assertTrue(drag.isCancelled());
        assertEquals("Dragged Item", slotTitle(access, inventory, 31));

        InventoryClickEvent returnClick = click(player, inventory, 31, ClickType.SHIFT_LEFT);

        runtime.onInventoryClick(returnClick);

        assertTrue(returnClick.isCancelled());
        assertEquals("Dragged Item", itemTitle(playerInventory(player).getItem(4)));
    }

    @Test
    void reactiveMenusCanPlacePickedUpCenterStacksIntoEmptyInventorySlots() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());

        runtime.open(player, reactiveDragInsertMenu(false));
        Inventory inventory = access.lastOpenedInventory();
        int topSize = inventory.getSize();
        ItemStack sourceItem = namedBukkitItem(Material.EMERALD, "Dragged Item", 2);
        playerInventory(player).setItem(4, sourceItem);

        InventoryClickEvent pickup = click(player, inventory, topSize + 4, ClickType.LEFT);
        runtime.onInventoryClick(pickup);

        runtime.onInventoryDrag(dragEvent(player, inventory, Set.of(31), sourceItem));

        InventoryClickEvent pickupFromCenter = click(player, inventory, 31, ClickType.LEFT);
        runtime.onInventoryClick(pickupFromCenter);

        assertTrue(pickupFromCenter.isCancelled());
        assertEquals("Dragged Item", itemTitle(player.getItemOnCursor()));

        InventoryClickEvent placeIntoInventory = click(player, inventory, topSize + 8, ClickType.LEFT);
        runtime.onInventoryClick(placeIntoInventory);

        assertTrue(placeIntoInventory.isCancelled());
        assertEquals("Dragged Item", itemTitle(playerInventory(player).getItem(8)));
        assertNull(player.getItemOnCursor());
        assertNull(inventory.getItem(31));
    }

    @Test
    void reactiveMenusDoNotMutateLockedInsertTargets() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());

        runtime.open(player, reactiveClickInsertMenu(true));
        Inventory inventory = access.lastOpenedInventory();
        int topSize = inventory.getSize();
        ItemStack sourceItem = namedBukkitItem(Material.DIAMOND, "Locked Item", 1);
        playerInventory(player).setItem(2, sourceItem);

        InventoryClickEvent click = click(player, inventory, topSize + 2, ClickType.LEFT);

        runtime.onInventoryClick(click);

        assertTrue(click.isCancelled());
        assertEquals("Locked Item", itemTitle(playerInventory(player).getItem(2)));
        assertNull(player.getItemOnCursor());
        assertEquals("Click An Inventory Stack", slotTitle(access, inventory, 31));
    }

    @Test
    void reactiveMenusIgnoreInertBaseChromeClicks() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());

        runtime.open(player, reactiveClickRoutingMenu());
        Inventory inventory = access.lastOpenedInventory();
        assertEquals("Placed Clicks: 0", slotTitle(access, inventory, 22));

        runtime.onInventoryClick(click(player, inventory, 0, ClickType.LEFT));
        assertEquals("Placed Clicks: 0", slotTitle(access, inventory, 22));

        runtime.onInventoryClick(click(player, inventory, 22, ClickType.LEFT));
        assertEquals("Placed Clicks: 1", slotTitle(access, inventory, 22));
    }

    @Test
    void reactiveMenusAcceptOnlyOneInputPerTickAcrossDifferentInputPaths() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(),
                new RecordingSoundCueService(), sh.harold.creative.library.menu.core.MenuTickScheduler.unsupported(),
                queuedScheduler(scheduled));

        runtime.open(player, reactiveClickInsertMenu(false));
        Inventory inventory = access.lastOpenedInventory();
        int topSize = inventory.getSize();
        ItemStack bottomItem = namedBukkitItem(Material.STONE, "Bottom Item", 3);
        playerInventory(player).setItem(5, bottomItem);

        InventoryClickEvent load = click(player, inventory, topSize + 5, ClickType.LEFT);
        runtime.onInventoryClick(load);

        InventoryClickEvent returnClick = click(player, inventory, 31, ClickType.LEFT);
        runtime.onInventoryClick(returnClick);

        assertTrue(load.isCancelled());
        assertTrue(returnClick.isCancelled());
        assertNull(playerInventory(player).getItem(5));
        assertEquals("Bottom Item", slotTitle(access, inventory, 31));
        assertEquals(1, scheduled.size());

        runNextTick(scheduled);

        runtime.onInventoryClick(click(player, inventory, 31, ClickType.LEFT));

        assertEquals("Bottom Item", itemTitle(playerInventory(player).getItem(5)));
        assertEquals("Click An Inventory Stack", slotTitle(access, inventory, 31));
    }

    @Test
    void inventoryTransitionsFromClickAreDeferredUntilScheduled() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(),
                new RecordingSoundCueService(), sh.harold.creative.library.menu.core.MenuTickScheduler.unsupported(),
                queuedScheduler(scheduled));

        runtime.open(player, launcherMenu());
        Inventory rootInventory = access.lastOpenedInventory();

        InventoryClickEvent openChild = click(player, rootInventory, 10, ClickType.LEFT);
        runtime.onInventoryClick(openChild);

        assertTrue(openChild.isCancelled());
        assertEquals(1, access.openedInventories.size());
        assertEquals(2, scheduled.size());

        runNextTick(scheduled);
        runNextTick(scheduled);

        assertEquals(2, access.openedInventories.size());
        assertEquals("Gallery", inventoryTitle(access, access.lastOpenedInventory()));
    }

    @Test
    void inventoryCloseFromClickIsDeferredUntilScheduled() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(),
                new RecordingSoundCueService(), sh.harold.creative.library.menu.core.MenuTickScheduler.unsupported(),
                queuedScheduler(scheduled));

        runtime.open(player, pagedMenu());
        Inventory inventory = access.lastOpenedInventory();

        InventoryClickEvent close = click(player, inventory, 49, ClickType.LEFT);
        runtime.onInventoryClick(close);

        assertTrue(close.isCancelled());
        assertTrue(access.closedPlayers.isEmpty());
        assertEquals(1, scheduled.size());

        runNextTick(scheduled);

        assertEquals(List.of(viewerId), access.closedPlayers);
    }

    @Test
    void traceLogsReactiveOpenAndClickSummariesWhenEnabled() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        MenuTraceController trace = new MenuTraceController();
        trace.traceAll();
        List<String> logs = new ArrayList<>();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(),
                new RecordingSoundCueService(), sh.harold.creative.library.menu.core.MenuTickScheduler.unsupported(),
                Runnable::run, trace, logs::add);

        runtime.open(player, reactiveClickRoutingMenu());

        String openSummary = summaryLine(logs, "open");
        assertTrue(openSummary.contains("host=\"paper\""));
        assertTrue(openSummary.contains("menu=\"Reactive Routing\""));
        assertTrue(openSummary.contains("placementCount=\"1\""));
        assertTrue(openSummary.contains("runtime.inventoryPatch="));

        logs.clear();
        Inventory inventory = access.lastOpenedInventory();
        runtime.onInventoryClick(click(player, inventory, 22, ClickType.LEFT));

        String clickSummary = summaryLine(logs, "click");
        assertTrue(clickSummary.contains("menu=\"Reactive Routing\""));
        assertTrue(clickSummary.contains("button=\"LEFT\""));
        assertTrue(clickSummary.contains("runtime.reactiveDispatch="));
    }

    @Test
    void traceCountsSuppressedReactiveDuplicateClicks() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        MenuTraceController trace = new MenuTraceController();
        trace.traceAll();
        List<String> logs = new ArrayList<>();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(),
                new RecordingSoundCueService(), sh.harold.creative.library.menu.core.MenuTickScheduler.unsupported(),
                queuedScheduler(scheduled), trace, logs::add);

        runtime.open(player, reactiveClickRoutingMenu());
        logs.clear();
        Inventory inventory = access.lastOpenedInventory();

        runtime.onInventoryClick(click(player, inventory, 22, ClickType.LEFT));
        logs.clear();

        runtime.onInventoryClick(click(player, inventory, 22, ClickType.LEFT));

        String clickSummary = summaryLine(logs, "click");
        assertTrue(clickSummary.contains("inputGuard=\"duplicate\""));
        assertTrue(clickSummary.contains("guardInputKind=\"reactive-top-click\""));
        assertTrue(clickSummary.contains("suppressedInputs=\"1\""));
        assertTrue(clickSummary.contains("suppressedInputDuplicates=\"1\""));
        assertEquals(1, scheduled.size());
    }

    @Test
    void traceFiltersByMenuTitle() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        MenuTraceController trace = new MenuTraceController();
        trace.traceMenuTitles(List.of("Reactive Routing"));
        List<String> logs = new ArrayList<>();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(),
                new RecordingSoundCueService(), sh.harold.creative.library.menu.core.MenuTickScheduler.unsupported(),
                Runnable::run, trace, logs::add);

        runtime.open(player, pagedMenu());
        assertTrue(logs.isEmpty());

        runtime.open(player, reactiveClickRoutingMenu());
        assertTrue(logs.stream().anyMatch(line -> line.startsWith("summary ") && line.contains("menu=\"Reactive Routing\"")));
    }

    @Test
    void inertCompiledChromeClicksDoNotEmitTraceSummaries() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        MenuTraceController trace = new MenuTraceController();
        trace.traceAll();
        List<String> logs = new ArrayList<>();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(),
                new RecordingSoundCueService(), sh.harold.creative.library.menu.core.MenuTickScheduler.unsupported(),
                Runnable::run, trace, logs::add);

        runtime.open(player, overflowGalleryMenu());
        Inventory inventory = access.lastOpenedInventory();
        runtime.onInventoryClick(click(player, inventory, 8, ClickType.RIGHT));

        assertEquals("Tab 3", slotTitle(access, inventory, 1));
        assertEquals("Tab 0 Item 0", slotTitle(access, inventory, 19));

        logs.clear();
        InventoryClickEvent inert = click(player, inventory, 8, ClickType.LEFT);
        runtime.onInventoryClick(inert);

        assertTrue(inert.isCancelled());
        assertTrue(logs.isEmpty());
        assertEquals("Tab 3", slotTitle(access, inventory, 1));
        assertEquals("Tab 0 Item 0", slotTitle(access, inventory, 19));
    }

    @Test
    void reactiveListUsesHousePagingChrome() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());

        runtime.open(player, reactiveListMenu(0));
        Inventory firstPage = access.lastOpenedInventory();

        assertEquals("Profiles (1/2)", inventoryTitle(access, firstPage));
        assertEquals("Item Item 0", slotTitle(access, firstPage, 10));
        assertEquals("Next Page", slotTitle(access, firstPage, 53));

        runtime.onInventoryClick(click(player, firstPage, 53, ClickType.LEFT));
        Inventory secondPage = access.lastOpenedInventory();

        assertEquals("Profiles (2/2)", inventoryTitle(access, secondPage));
        assertEquals("Item Item 28", slotTitle(access, secondPage, 10));
        assertEquals("Previous Page", slotTitle(access, secondPage, 45));

        runtime.onInventoryClick(click(player, secondPage, 45, ClickType.LEFT));
        Inventory returnedFirstPage = access.lastOpenedInventory();

        assertEquals("Profiles (1/2)", inventoryTitle(access, returnedFirstPage));
        assertEquals("Item Item 0", slotTitle(access, returnedFirstPage, 10));
        assertEquals("Next Page", slotTitle(access, returnedFirstPage, 53));
    }

    @Test
    void reactiveTabsScrollVisibleStripWithoutChangingActiveTabContent() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());

        runtime.open(player, reactiveTabsMenu("tab-0", 1, 1));
        Inventory inventory = access.lastOpenedInventory();

        assertEquals("Reactive Tabs", inventoryTitle(access, inventory));
        assertEquals("Tab 1", slotTitle(access, inventory, 1));
        assertEquals("Tab 7", slotTitle(access, inventory, 7));
        assertEquals("Tab 0 Item 21", slotTitle(access, inventory, 19));
        assertEquals("Previous Page", slotTitle(access, inventory, 45));

        runtime.onInventoryClick(click(player, inventory, 0, ClickType.LEFT));

        assertEquals("Tab 0", slotTitle(access, inventory, 1));
        assertEquals("Tab 6", slotTitle(access, inventory, 7));
        assertEquals("Tab 0 Item 21", slotTitle(access, inventory, 19));

        runtime.onInventoryClick(click(player, inventory, 45, ClickType.LEFT));

        assertEquals("Tab 0 Item 0", slotTitle(access, inventory, 19));
        assertEquals("Tab 0 Item 20", slotTitle(access, inventory, 43));
        assertEquals("Next Page", slotTitle(access, inventory, 53));
    }

    @Test
    void tabsPassCustomHeadIconsThroughToRenderer() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        AtomicReference<MenuIcon> firstTabIcon = new AtomicReference<>();
        AtomicReference<MenuIcon> secondTabIcon = new AtomicReference<>();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, slot -> {
            if (slot.slot() == 3) {
                firstTabIcon.set(slot.icon());
            }
            if (slot.slot() == 4) {
                secondTabIcon.set(slot.icon());
            }
            return PaperMenuTestSupport.renderedItem(slot.icon().key(), slot.amount(), slot.title(), slot.lore(), slot.glow());
        }, new RecordingSoundCueService());

        runtime.open(player, customHeadGalleryMenu());

        assertEquals(MenuIcon.customHead("dG9vbC10ZXh0dXJl"), firstTabIcon.get());
        assertEquals(MenuIcon.customHead("Y2hhbWJlci10ZXh0dXJl"), secondTabIcon.get());
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

    private static Menu counterMenu(AtomicInteger count) {
        return new StandardMenuService().list()
                .title("Counter")
                .addItem(MenuButton.builder(MenuIcon.vanilla("stone"))
                        .name("Increment")
                        .action(ActionVerb.VIEW, context -> count.incrementAndGet())
                        .build())
                .build();
    }

    private static Menu dualCounterMenu(AtomicInteger first, AtomicInteger second) {
        return new StandardMenuService().list()
                .title("Dual Counter")
                .addItem(MenuButton.builder(MenuIcon.vanilla("stone"))
                        .name("First")
                        .action(ActionVerb.VIEW, context -> first.incrementAndGet())
                        .build())
                .addItem(MenuButton.builder(MenuIcon.vanilla("lever"))
                        .name("Second")
                        .action(ActionVerb.VIEW, context -> second.incrementAndGet())
                        .build())
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

    private static ReactiveMenu reactivePromptMenu(PromptState initialState) {
        return new StandardMenuService().reactiveList()
                .state(initialState)
                .render(state -> ReactiveListView.builder("Reactive Prompt")
                        .addItem(MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                                .name(state.query().isBlank() ? "No Query" : "Query: " + state.query())
                                .build())
                        .utility(UtilitySlot.RIGHT_1, MenuButton.builder(MenuIcon.vanilla("oak_sign"))
                                .name(state.query().isBlank() ? "Search" : "Search: " + state.query())
                                .emit(ActionVerb.BROWSE, "search", "open-search")
                                .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.Click click && "open-search".equals(click.message())) {
                        return ReactiveMenuResult.of(state, new ReactiveMenuEffect.RequestTextPrompt(
                                ReactiveTextPromptRequest.sign("prompt-search", "Search", state.query())));
                    }
                    if (input instanceof ReactiveMenuInput.TextPromptSubmitted submitted
                            && submitted.key().equals("prompt-search")) {
                        return ReactiveMenuResult.stay(new PromptState(submitted.value()));
                    }
                    if (input instanceof ReactiveMenuInput.TextPromptCancelled cancelled
                            && cancelled.key().equals("prompt-search")) {
                        return ReactiveMenuResult.stay(state);
                    }
                    return ReactiveMenuResult.stay(state);
                })
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

    private static ReactiveMenu reactiveRefreshMenu(AtomicBoolean enabled) {
        return new StandardMenuService().reactiveCanvas()
                .state(enabled)
                .render(state -> ReactiveMenuView.builder("Reactive Refresh")
                        .place(22, MenuButton.builder(MenuIcon.vanilla("lever"))
                                .name(state.get() ? "Reactive Refresh: On" : "Reactive Refresh: Off")
                                .action(ActionVerb.TOGGLE, context -> {
                                    state.set(!state.get());
                                    context.refresh();
                                })
                                .build())
                        .build())
                .reduce((state, input) -> ReactiveMenuResult.stay(state))
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

    private static Player player(UUID uuid) {
        return PaperMenuTestSupport.player(uuid);
    }

    private static ItemStack namedBukkitItem(Material material, String name, int amount) {
        return PaperMenuTestSupport.namedItem(material, name, amount);
    }

    private static InventoryClickEvent click(Player player, Inventory inventory, int rawSlot, ClickType clickType) {
        InventoryView view = view(player, inventory);
        InventoryClickEvent event = new InventoryClickEvent(
                view,
                view.getSlotType(rawSlot),
                rawSlot,
                clickType,
                InventoryAction.PICKUP_ALL);
        event.setCurrentItem(view.getItem(rawSlot));
        event.setCursor(view.getCursor());
        return event;
    }

    private static InventoryDragEvent dragEvent(Player player, Inventory inventory, Set<Integer> rawSlots, ItemStack oldCursor) {
        Map<Integer, ItemStack> newItems = new java.util.HashMap<>();
        rawSlots.forEach(rawSlot -> newItems.put(rawSlot, oldCursor));
        return new InventoryDragEvent(view(player, inventory), oldCursor, oldCursor, false, newItems);
    }

    private static InventoryView view(Player player, Inventory topInventory) {
        return new TestInventoryView(topInventory, playerInventory(player), player);
    }

    private static PlayerInventory playerInventory(Player player) {
        return player.getInventory();
    }

    private static String slotTitle(TestPaperMenuAccess access, Inventory inventory, int slot) {
        ItemStack itemStack = inventory.getItem(slot);
        return flatten(itemStack.getItemMeta().displayName());
    }

    private static List<String> slotLore(TestPaperMenuAccess access, Inventory inventory, int slot) {
        ItemStack itemStack = inventory.getItem(slot);
        List<Component> lore = itemStack.getItemMeta().lore();
        if (lore == null) {
            return List.of();
        }
        return lore.stream().map(PaperMenuRuntimeTest::flatten).toList();
    }

    private static String inventoryTitle(TestPaperMenuAccess access, Inventory inventory) {
        return flatten(access.model(inventory).title());
    }

    private static String itemTitle(ItemStack itemStack) {
        return flatten(itemStack.getItemMeta().displayName());
    }

    private static String flatten(Component component) {
        StringBuilder builder = new StringBuilder();
        append(builder, component);
        return builder.toString();
    }

    private static Function<Runnable, MenuTickHandle> queuedScheduler(Deque<Runnable> scheduled) {
        return action -> {
            scheduled.addLast(action);
            return () -> scheduled.remove(action);
        };
    }

    private record PromptState(String query) {
    }

    private static void runNextTick(Deque<Runnable> scheduled) {
        scheduled.removeFirst().run();
    }

    private static PaperMenuSlotRenderer renderer() {
        return slot -> PaperMenuTestSupport.renderedItem(slot.icon().key(), slot.amount(), slot.title(), slot.lore(), slot.glow());
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

    private static final class TestPaperMenuAccess implements PaperMenuAccess {

        private final Map<Inventory, InventoryModel> models = new IdentityHashMap<>();
        private final Map<Player, Inventory> topInventories = new IdentityHashMap<>();
        private final List<Inventory> openedInventories = new ArrayList<>();
        private final List<UUID> closedPlayers = new ArrayList<>();

        @Override
        public Inventory createInventory(InventoryHolder holder, int size, Component title) {
            Inventory inventory = PaperMenuTestSupport.inventory(holder, size);
            InventoryModel model = new InventoryModel(holder, size, title);
            models.put(inventory, model);
            return inventory;
        }

        @Override
        public void openInventory(Player player, Inventory inventory) {
            topInventories.put(player, inventory);
            openedInventories.add(inventory);
        }

        @Override
        public void closeInventory(Player player) {
            topInventories.remove(player);
            closedPlayers.add(player.getUniqueId());
        }

        @Override
        public Inventory topInventory(Player player) {
            return topInventories.get(player);
        }

        Inventory lastOpenedInventory() {
            return openedInventories.getLast();
        }

        InventoryModel model(Inventory inventory) {
            return models.get(inventory);
        }
    }

    private record InventoryModel(InventoryHolder holder, int size, Component title, Map<Integer, ItemStack> items) {

        private InventoryModel(InventoryHolder holder, int size, Component title) {
            this(holder, size, title, new java.util.HashMap<>());
        }
    }

    private static final class RecordingSoundCueService implements SoundCueService {

        private final StandardSoundCueRegistry registry = new StandardSoundCueRegistry();
        private final Map<SoundCue, Key> keysByCue = new IdentityHashMap<>();
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

    private static final class TestInventoryView implements InventoryView {

        private final Inventory topInventory;
        private final Inventory bottomInventory;
        private final HumanEntity player;
        private ItemStack cursor;

        private TestInventoryView(Inventory topInventory, Inventory bottomInventory, HumanEntity player) {
            this.topInventory = topInventory;
            this.bottomInventory = bottomInventory;
            this.player = player;
        }

        @Override
        public Inventory getTopInventory() {
            return topInventory;
        }

        @Override
        public Inventory getBottomInventory() {
            return bottomInventory;
        }

        @Override
        public HumanEntity getPlayer() {
            return player;
        }

        @Override
        public InventoryType getType() {
            return null;
        }

        @Override
        public void setItem(int slot, ItemStack item) {
            Inventory inventory = getInventory(slot);
            if (inventory == null) {
                return;
            }
            inventory.setItem(convertSlot(slot), item);
        }

        @Override
        public ItemStack getItem(int slot) {
            Inventory inventory = getInventory(slot);
            return inventory == null ? null : inventory.getItem(convertSlot(slot));
        }

        @Override
        public void setCursor(ItemStack item) {
            if (player instanceof Player paperPlayer) {
                paperPlayer.setItemOnCursor(item);
                return;
            }
            this.cursor = item;
        }

        @Override
        public ItemStack getCursor() {
            if (player instanceof Player paperPlayer) {
                return paperPlayer.getItemOnCursor();
            }
            return cursor;
        }

        @Override
        public Inventory getInventory(int slot) {
            return slot < topInventory.getSize() ? topInventory : bottomInventory;
        }

        @Override
        public int convertSlot(int rawSlot) {
            if (rawSlot < topInventory.getSize()) {
                return rawSlot;
            }
            return rawSlot - topInventory.getSize();
        }

        @Override
        public InventoryType.SlotType getSlotType(int slot) {
            if (slot < topInventory.getSize()) {
                return InventoryType.SlotType.CONTAINER;
            }
            int converted = convertSlot(slot);
            return converted < 9 ? InventoryType.SlotType.QUICKBAR : InventoryType.SlotType.CONTAINER;
        }

        @Override
        public void open() {
        }

        @Override
        public void close() {
        }

        @Override
        public int countSlots() {
            return topInventory.getSize() + bottomInventory.getSize();
        }

        @Override
        public boolean setProperty(Property prop, int value) {
            return false;
        }

        @Override
        public String getTitle() {
            return "Test";
        }

        @Override
        public String getOriginalTitle() {
            return "Test";
        }

        @Override
        public void setTitle(String title) {
        }

        @Override
        public org.bukkit.inventory.MenuType getMenuType() {
            return null;
        }
    }

    private record ClickInsertState(MenuStack stored, int storedSourceSlot, boolean locked) {
    }

    private record DragInsertState(MenuStack stored, int storedSourceSlot, MenuStack cursor, int cursorSourceSlot,
                                   boolean locked) {
    }

    private record StoredState(MenuStack stored) {
    }

    private record ReactiveTabsState(String activeTabId, int navStart, int pageIndex) {
    }
}
