package sh.harold.creative.library.menu.paper;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.DragType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuDisplayItem;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuStack;
import sh.harold.creative.library.menu.MenuTab;
import sh.harold.creative.library.menu.MenuTraceController;
import sh.harold.creative.library.menu.ReactiveMenu;
import sh.harold.creative.library.menu.ReactiveMenuEffect;
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

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperMenuRuntimeTest {

    private static final Key SPECIAL_SOUND = Key.key("test", "menu/special");
    private static final Map<Player, PlayerInventory> PLAYER_INVENTORIES = new IdentityHashMap<>();

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

        InventoryView view = view(player, inventory);
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getView()).thenReturn(view);
        when(click.getWhoClicked()).thenReturn(player);
        when(click.getRawSlot()).thenReturn(topSize + 5);
        when(click.getSlot()).thenReturn(5);
        when(click.isLeftClick()).thenReturn(true);
        when(click.isRightClick()).thenReturn(false);
        when(click.isShiftClick()).thenReturn(false);
        when(click.getCurrentItem()).thenReturn(bottomItem);
        when(click.getCursor()).thenReturn(null);
        runtime.onInventoryClick(click);

        verify(click).setCancelled(true);
        assertNull(playerInventory(player).getItem(5));
        assertNull(player.getItemOnCursor());
        assertEquals("Bottom Item", slotTitle(access, inventory, 31));

        InventoryClickEvent returnClick = mock(InventoryClickEvent.class);
        when(returnClick.getView()).thenReturn(view(player, inventory));
        when(returnClick.getWhoClicked()).thenReturn(player);
        when(returnClick.getRawSlot()).thenReturn(31);
        when(returnClick.getSlot()).thenReturn(31);
        when(returnClick.isLeftClick()).thenReturn(true);
        when(returnClick.isRightClick()).thenReturn(false);
        when(returnClick.isShiftClick()).thenReturn(false);
        when(returnClick.getCurrentItem()).thenReturn(access.model(inventory).items.get(31));
        when(returnClick.getCursor()).thenReturn(null);
        runtime.onInventoryClick(returnClick);

        verify(returnClick).setCancelled(true);
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

        InventoryView view = view(player, inventory);
        InventoryClickEvent pickup = mock(InventoryClickEvent.class);
        when(pickup.getView()).thenReturn(view);
        when(pickup.getWhoClicked()).thenReturn(player);
        when(pickup.getRawSlot()).thenReturn(topSize + 4);
        when(pickup.getSlot()).thenReturn(4);
        when(pickup.isLeftClick()).thenReturn(true);
        when(pickup.isRightClick()).thenReturn(false);
        when(pickup.isShiftClick()).thenReturn(false);
        when(pickup.getCurrentItem()).thenReturn(sourceItem);
        when(pickup.getCursor()).thenReturn(null);
        runtime.onInventoryClick(pickup);

        verify(pickup).setCancelled(true);
        assertNull(playerInventory(player).getItem(4));

        ItemStack cursorItem = sourceItem;
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getView()).thenReturn(view(player, inventory));
        when(drag.getWhoClicked()).thenReturn(player);
        when(drag.getRawSlots()).thenReturn(Set.of(31));
        when(drag.getType()).thenReturn(DragType.EVEN);
        when(drag.getOldCursor()).thenReturn(cursorItem);

        runtime.onInventoryDrag(drag);

        verify(drag).setCancelled(true);
        assertEquals("Dragged Item", slotTitle(access, inventory, 31));

        InventoryClickEvent returnClick = mock(InventoryClickEvent.class);
        when(returnClick.getView()).thenReturn(view(player, inventory));
        when(returnClick.getWhoClicked()).thenReturn(player);
        when(returnClick.getRawSlot()).thenReturn(31);
        when(returnClick.getSlot()).thenReturn(31);
        when(returnClick.isLeftClick()).thenReturn(true);
        when(returnClick.isRightClick()).thenReturn(false);
        when(returnClick.isShiftClick()).thenReturn(true);
        when(returnClick.getCurrentItem()).thenReturn(access.model(inventory).items.get(31));
        when(returnClick.getCursor()).thenReturn(null);

        runtime.onInventoryClick(returnClick);

        verify(returnClick).setCancelled(true);
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

        InventoryClickEvent pickup = mock(InventoryClickEvent.class);
        when(pickup.getView()).thenReturn(view(player, inventory));
        when(pickup.getWhoClicked()).thenReturn(player);
        when(pickup.getRawSlot()).thenReturn(topSize + 4);
        when(pickup.getSlot()).thenReturn(4);
        when(pickup.isLeftClick()).thenReturn(true);
        when(pickup.isRightClick()).thenReturn(false);
        when(pickup.isShiftClick()).thenReturn(false);
        when(pickup.getCurrentItem()).thenReturn(sourceItem);
        when(pickup.getCursor()).thenReturn(null);
        runtime.onInventoryClick(pickup);

        runtime.onInventoryDrag(dragEvent(player, inventory, Set.of(31), sourceItem));

        InventoryClickEvent pickupFromCenter = mock(InventoryClickEvent.class);
        when(pickupFromCenter.getView()).thenReturn(view(player, inventory));
        when(pickupFromCenter.getWhoClicked()).thenReturn(player);
        when(pickupFromCenter.getRawSlot()).thenReturn(31);
        when(pickupFromCenter.getSlot()).thenReturn(31);
        when(pickupFromCenter.isLeftClick()).thenReturn(true);
        when(pickupFromCenter.isRightClick()).thenReturn(false);
        when(pickupFromCenter.isShiftClick()).thenReturn(false);
        when(pickupFromCenter.getCurrentItem()).thenReturn(access.model(inventory).items.get(31));
        when(pickupFromCenter.getCursor()).thenReturn(null);
        runtime.onInventoryClick(pickupFromCenter);

        verify(pickupFromCenter).setCancelled(true);
        assertEquals("Dragged Item", itemTitle(player.getItemOnCursor()));

        InventoryClickEvent placeIntoInventory = mock(InventoryClickEvent.class);
        when(placeIntoInventory.getView()).thenReturn(view(player, inventory));
        when(placeIntoInventory.getWhoClicked()).thenReturn(player);
        when(placeIntoInventory.getRawSlot()).thenReturn(topSize + 8);
        when(placeIntoInventory.getSlot()).thenReturn(8);
        when(placeIntoInventory.isLeftClick()).thenReturn(true);
        when(placeIntoInventory.isRightClick()).thenReturn(false);
        when(placeIntoInventory.isShiftClick()).thenReturn(false);
        when(placeIntoInventory.getCurrentItem()).thenReturn(null);
        when(placeIntoInventory.getCursor()).thenReturn(player.getItemOnCursor());
        runtime.onInventoryClick(placeIntoInventory);

        verify(placeIntoInventory).setCancelled(true);
        assertEquals("Dragged Item", itemTitle(playerInventory(player).getItem(8)));
        assertNull(player.getItemOnCursor());
        assertNull(access.model(inventory).items.get(31));
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

        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getView()).thenReturn(view(player, inventory));
        when(click.getWhoClicked()).thenReturn(player);
        when(click.getRawSlot()).thenReturn(topSize + 2);
        when(click.getSlot()).thenReturn(2);
        when(click.isLeftClick()).thenReturn(true);
        when(click.isRightClick()).thenReturn(false);
        when(click.isShiftClick()).thenReturn(false);
        when(click.getCurrentItem()).thenReturn(sourceItem);
        when(click.getCursor()).thenReturn(null);

        runtime.onInventoryClick(click);

        verify(click).setCancelled(true);
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
    void inventoryTransitionsFromClickAreDeferredUntilScheduled() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(),
                new RecordingSoundCueService(), sh.harold.creative.library.menu.core.MenuTickScheduler.unsupported(), scheduled::addLast);

        runtime.open(player, launcherMenu());
        Inventory rootInventory = access.lastOpenedInventory();

        InventoryClickEvent openChild = click(player, rootInventory, 10, ClickType.LEFT);
        runtime.onInventoryClick(openChild);

        assertTrue(openChild.isCancelled());
        assertEquals(1, access.openedInventories.size());
        assertEquals(1, scheduled.size());

        scheduled.removeFirst().run();

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
                new RecordingSoundCueService(), sh.harold.creative.library.menu.core.MenuTickScheduler.unsupported(), scheduled::addLast);

        runtime.open(player, pagedMenu());
        Inventory inventory = access.lastOpenedInventory();

        InventoryClickEvent close = click(player, inventory, 49, ClickType.LEFT);
        runtime.onInventoryClick(close);

        assertTrue(close.isCancelled());
        assertTrue(access.closedPlayers.isEmpty());
        assertEquals(1, scheduled.size());

        scheduled.removeFirst().run();

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

    private static ReactiveMenu reactiveClickInsertMenu(boolean locked) {
        return new StandardMenuService().reactive()
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
        return new StandardMenuService().reactive()
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

    private static Player player(UUID uuid) {
        Map<Integer, ItemStack> items = new java.util.HashMap<>();
        AtomicReference<ItemStack> cursor = new AtomicReference<>();
        PlayerInventory inventory = mock(PlayerInventory.class, invocation -> {
            String method = invocation.getMethod().getName();
            if (method.equals("getSize")) {
                return 36;
            }
            if (method.equals("setItem")) {
                items.put(invocation.getArgument(0, Integer.class), invocation.getArgument(1, ItemStack.class));
                return null;
            }
            if (method.equals("getItem")) {
                return items.get(invocation.getArgument(0, Integer.class));
            }
            return Mockito.RETURNS_DEFAULTS.answer(invocation);
        });
        Player mockedPlayer = mock(Player.class, invocation -> {
            String method = invocation.getMethod().getName();
            if (method.equals("getUniqueId")) {
                return uuid;
            }
            if (method.equals("getInventory")) {
                return inventory;
            }
            if (method.equals("setItemOnCursor")) {
                cursor.set(invocation.getArgument(0, ItemStack.class));
                return null;
            }
            if (method.equals("getItemOnCursor")) {
                return cursor.get();
            }
            return Mockito.RETURNS_DEFAULTS.answer(invocation);
        });
        PLAYER_INVENTORIES.put(mockedPlayer, inventory);
        return mockedPlayer;
    }

    private static ItemStack namedBukkitItem(Material material, String name, int amount) {
        ItemStack itemStack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(itemStack.getType()).thenReturn(material);
        when(itemStack.getAmount()).thenReturn(amount);
        when(itemStack.getItemMeta()).thenReturn(meta);
        when(meta.displayName()).thenReturn(Component.text(name));
        return itemStack;
    }

    private static InventoryClickEvent click(Player player, Inventory inventory, int rawSlot, ClickType clickType) {
        return new InventoryClickEvent(
                view(player, inventory),
                InventoryType.SlotType.CONTAINER,
                rawSlot,
                clickType,
                InventoryAction.PICKUP_ALL);
    }

    private static InventoryDragEvent dragEvent(Player player, Inventory inventory, Set<Integer> rawSlots, ItemStack oldCursor) {
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getView()).thenReturn(view(player, inventory));
        when(drag.getWhoClicked()).thenReturn(player);
        when(drag.getRawSlots()).thenReturn(rawSlots);
        when(drag.getType()).thenReturn(DragType.EVEN);
        when(drag.getOldCursor()).thenReturn(oldCursor);
        return drag;
    }

    private static InventoryView view(Player player, Inventory topInventory) {
        return new TestInventoryView(topInventory, playerInventory(player), player);
    }

    private static PlayerInventory playerInventory(Player player) {
        return PLAYER_INVENTORIES.get(player);
    }

    private static String slotTitle(TestPaperMenuAccess access, Inventory inventory, int slot) {
        ItemStack itemStack = access.model(inventory).items.get(slot);
        return flatten(itemStack.getItemMeta().displayName());
    }

    private static List<String> slotLore(TestPaperMenuAccess access, Inventory inventory, int slot) {
        ItemStack itemStack = access.model(inventory).items.get(slot);
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

    private static PaperMenuSlotRenderer renderer() {
        return slot -> {
            ItemStack itemStack = mock(ItemStack.class);
            ItemMeta meta = mock(ItemMeta.class);
            when(itemStack.getItemMeta()).thenReturn(meta);
            when(meta.displayName()).thenReturn(slot.title());
            when(meta.lore()).thenReturn(slot.lore());
            when(meta.getEnchantmentGlintOverride()).thenReturn(slot.glow() ? Boolean.TRUE : null);
            return itemStack;
        };
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
            Inventory inventory = mock(Inventory.class);
            InventoryModel model = new InventoryModel(holder, size, title);
            models.put(inventory, model);
            when(inventory.getSize()).thenReturn(size);
            when(inventory.getHolder(false)).thenReturn(holder);
            doAnswer(invocation -> {
                model.items.put(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(inventory).setItem(anyInt(), any());
            when(inventory.getItem(anyInt())).thenAnswer(invocation -> model.items.get(invocation.getArgument(0)));
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
            return InventoryType.CHEST;
        }

        @Override
        public void setItem(int slot, ItemStack item) {
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot < topInventory.getSize() ? topInventory.getItem(slot) : null;
        }

        @Override
        public void setCursor(ItemStack item) {
            this.cursor = item;
        }

        @Override
        public ItemStack getCursor() {
            return cursor;
        }

        @Override
        public Inventory getInventory(int slot) {
            return slot < topInventory.getSize() ? topInventory : bottomInventory;
        }

        @Override
        public int convertSlot(int rawSlot) {
            return rawSlot;
        }

        @Override
        public InventoryType.SlotType getSlotType(int slot) {
            return slot < topInventory.getSize() ? InventoryType.SlotType.CONTAINER : InventoryType.SlotType.QUICKBAR;
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
            return org.bukkit.inventory.MenuType.GENERIC_9X6;
        }
    }

    private record ClickInsertState(MenuStack stored, int storedSourceSlot, boolean locked) {
    }

    private record DragInsertState(MenuStack stored, int storedSourceSlot, MenuStack cursor, int cursorSourceSlot,
                                   boolean locked) {
    }

    private record StoredState(MenuStack stored) {
    }
}
