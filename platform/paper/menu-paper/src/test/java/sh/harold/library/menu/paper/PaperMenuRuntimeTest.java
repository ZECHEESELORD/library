package sh.harold.library.menu.paper;

import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import io.papermc.paper.math.Position;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import sh.harold.library.menu.ActionVerb;
import sh.harold.library.menu.Menu;
import sh.harold.library.menu.MenuButton;
import sh.harold.library.menu.MenuClick;
import sh.harold.library.menu.MenuDisplayItem;
import sh.harold.library.menu.MenuCustodyDecision;
import sh.harold.library.menu.MenuCustodyDestination;
import sh.harold.library.menu.MenuCustodyGesture;
import sh.harold.library.menu.MenuIcon;
import sh.harold.library.menu.MenuFrame;
import sh.harold.library.menu.MenuGeometry;
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
import sh.harold.library.menu.ReactiveTextPromptMode;
import sh.harold.library.menu.ReactiveTextPromptRequest;
import sh.harold.library.menu.ReactiveTabsView;
import sh.harold.library.menu.ReactiveMenuView;
import sh.harold.library.menu.UtilitySlot;
import sh.harold.library.menu.core.MenuTickHandle;
import sh.harold.library.menu.core.StandardMenuService;
import sh.harold.library.sound.CuePlayback;
import sh.harold.library.sound.SoundCue;
import sh.harold.library.sound.SoundCueKeys;
import sh.harold.library.sound.SoundCuePacks;
import sh.harold.library.sound.SoundCueRegistry;
import sh.harold.library.sound.SoundCueService;
import sh.harold.library.sound.SoundTarget;
import sh.harold.library.sound.core.StandardSoundCueRegistry;

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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

        inventory = access.lastOpenedInventory();
        assertEquals("Enabled", slotTitle(access, inventory, 10));
        assertEquals(2, access.openedInventories.size());
    }

    @Test
    void actionReplaceCurrentMenuPreservesParentBackHistory() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());
        AtomicBoolean enabled = new AtomicBoolean(false);

        runtime.open(player, toggleLauncherMenu(enabled));
        Inventory rootInventory = access.lastOpenedInventory();
        assertEquals("Open Toggle", slotTitle(access, rootInventory, 10));

        InventoryClickEvent openChild = click(player, rootInventory, 10, ClickType.LEFT);
        runtime.onInventoryClick(openChild);

        assertTrue(openChild.isCancelled());
        Inventory childInventory = access.lastOpenedInventory();
        assertEquals("Disabled", slotTitle(access, childInventory, 10));
        assertEquals("Go Back", slotTitle(access, childInventory, 48));

        InventoryClickEvent toggle = click(player, childInventory, 10, ClickType.LEFT);
        runtime.onInventoryClick(toggle);

        assertTrue(toggle.isCancelled());
        childInventory = access.lastOpenedInventory();
        assertEquals("Enabled", slotTitle(access, childInventory, 10));
        assertEquals("Go Back", slotTitle(access, childInventory, 48));

        InventoryClickEvent back = click(player, childInventory, 48, ClickType.LEFT);
        runtime.onInventoryClick(back);

        assertTrue(back.isCancelled());
        assertEquals("Open Toggle", slotTitle(access, access.lastOpenedInventory(), 10));
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
                new PaperMenuRuntime.PreparedVirtualSign(PaperMenuTestSupport.blockData(), PaperMenuTestSupport.tileState());
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(),
                new RecordingSoundCueService(), sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
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
    void signRestoreSchedulingFailureStillUnlocksAndReopensTheSession() {
        UUID viewerId = UUID.randomUUID();
        org.bukkit.World world = org.mockito.Mockito.mock(org.bukkit.World.class);
        PaperMenuTestSupport.TrackedPlayer trackedPlayer = PaperMenuTestSupport.trackedPlayer(
                viewerId,
                new Location(world, 0.0, 64.0, 0.0));
        Player player = trackedPlayer.player();
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        PaperMenuRuntime.PaperVirtualSignSupport virtualSigns = (request, allowedEditorId) ->
                new PaperMenuRuntime.PreparedVirtualSign(
                        PaperMenuTestSupport.blockData(),
                        PaperMenuTestSupport.tileState());
        PaperMenuTaskScheduler taskScheduler = PaperMenuTaskScheduler.testing(
                sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
                queuedScheduler(scheduled),
                (location, action) -> {
                    throw new IllegalStateException("location scheduler unavailable");
                });
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                renderer(),
                new RecordingSoundCueService(),
                taskScheduler,
                new MenuTraceController(),
                message -> { },
                virtualSigns,
                (viewer, request, submit, cancel) -> { });

        runtime.open(player, reactivePromptMenu(new PromptState("")));
        Inventory inventory = access.lastOpenedInventory();
        runtime.onInventoryClick(click(
                player,
                inventory,
                UtilitySlot.RIGHT_1.resolveSlot(45),
                ClickType.LEFT));
        runNextTick(scheduled);
        runNextTick(scheduled);
        runtime.onInventoryClose(new InventoryCloseEvent(view(player, inventory)));
        runNextTick(scheduled);

        Location signLocation = trackedPlayer.state().blockChangeLocations().getFirst();
        UncheckedSignChangeEvent signChange = new UncheckedSignChangeEvent(
                player,
                Position.block(signLocation),
                Side.FRONT,
                List.of(Component.text("pain"), Component.empty(), Component.empty(), Component.empty()));
        runtime.onUncheckedSignChange(signChange);

        PaperMenuSession session = (PaperMenuSession) inventory.getHolder(false);
        assertTrue(signChange.isCancelled());
        assertFalse(session.custodyTransitioning());

        runNextTick(scheduled);

        Inventory reopened = access.lastOpenedInventory();
        assertEquals("Search: pain", slotTitle(access, reopened, UtilitySlot.RIGHT_1.resolveSlot(45)));
        assertEquals(reopened, access.topInventory(player));
    }


    @Test
    void olderSignRestoreCannotOverwriteANewerPromptAtTheSameBlock() {
        UUID viewerId = UUID.randomUUID();
        org.bukkit.World world = org.mockito.Mockito.mock(org.bukkit.World.class);
        org.bukkit.block.Block block = org.mockito.Mockito.mock(org.bukkit.block.Block.class);
        org.bukkit.block.data.BlockData liveBlockData =
                org.mockito.Mockito.mock(org.bukkit.block.data.BlockData.class);
        org.bukkit.block.data.BlockData restoredBlockData = PaperMenuTestSupport.blockData();
        org.bukkit.block.TileState restoredTileState = PaperMenuTestSupport.tileState();
        org.mockito.Mockito.when(world.getBlockAt(
                org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(block);
        org.mockito.Mockito.when(block.getBlockData()).thenReturn(liveBlockData);
        org.mockito.Mockito.when(liveBlockData.clone()).thenReturn(restoredBlockData);
        org.mockito.Mockito.when(block.getState()).thenReturn(restoredTileState);

        PaperMenuTestSupport.TrackedPlayer trackedPlayer = PaperMenuTestSupport.trackedPlayer(
                viewerId,
                new Location(world, 0.0, 64.0, 0.0));
        Player player = trackedPlayer.player();
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        Deque<Runnable> viewerTasks = new ArrayDeque<>();
        Deque<Runnable> locationTasks = new ArrayDeque<>();
        PaperMenuTaskScheduler taskScheduler = PaperMenuTaskScheduler.testing(
                sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
                queuedScheduler(viewerTasks),
                (location, action) -> {
                    locationTasks.addLast(action);
                    return () -> locationTasks.remove(action);
                });
        PaperMenuRuntime.PaperVirtualSignSupport virtualSigns = (request, allowedEditorId) ->
                new PaperMenuRuntime.PreparedVirtualSign(
                        PaperMenuTestSupport.blockData(),
                        PaperMenuTestSupport.tileState());
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                renderer(),
                new RecordingSoundCueService(),
                taskScheduler,
                new MenuTraceController(),
                message -> { },
                virtualSigns,
                (viewer, request, submit, cancel) -> { });

        runtime.open(player, chainedSignPromptMenu());
        Inventory inventory = access.lastOpenedInventory();
        PaperMenuSession session = (PaperMenuSession) inventory.getHolder(false);
        runtime.onInventoryClick(click(
                player,
                inventory,
                UtilitySlot.RIGHT_1.resolveSlot(45),
                ClickType.LEFT));
        runNextTick(viewerTasks);
        runNextTick(viewerTasks);
        runtime.onInventoryClose(new InventoryCloseEvent(view(player, inventory)));
        runNextTick(viewerTasks);

        Location signLocation = trackedPlayer.state().blockChangeLocations().getFirst();
        UncheckedSignChangeEvent firstSubmission = new UncheckedSignChangeEvent(
                player,
                Position.block(signLocation),
                Side.FRONT,
                List.of(Component.text("first"), Component.empty(), Component.empty(), Component.empty()));
        runtime.onUncheckedSignChange(firstSubmission);
        assertTrue(firstSubmission.isCancelled());
        assertEquals(1, locationTasks.size());

        runNextTick(viewerTasks);

        assertTrue(session.custodyTransitioning());
        assertEquals(2, trackedPlayer.state().openedVirtualSigns().size());
        assertEquals(2, trackedPlayer.state().blockChanges().size());
        assertEquals(2, trackedPlayer.state().blockUpdates().size());

        runNextTick(locationTasks);
        runNextTick(viewerTasks);

        assertEquals(2, trackedPlayer.state().blockChanges().size());
        assertEquals(2, trackedPlayer.state().blockUpdates().size());

        UncheckedSignChangeEvent secondSubmission = new UncheckedSignChangeEvent(
                player,
                Position.block(signLocation),
                Side.FRONT,
                List.of(Component.text("final"), Component.empty(), Component.empty(), Component.empty()));
        runtime.onUncheckedSignChange(secondSubmission);

        assertTrue(secondSubmission.isCancelled());
        assertFalse(session.custodyTransitioning());
        assertEquals(1, locationTasks.size());

        runNextTick(locationTasks);
        while (!viewerTasks.isEmpty()) {
            runNextTick(viewerTasks);
        }

        assertEquals(3, trackedPlayer.state().blockChanges().size());
        assertEquals(restoredBlockData, trackedPlayer.state().blockChanges().getLast());
        assertEquals(3, trackedPlayer.state().blockUpdates().size());
        assertEquals(restoredTileState, trackedPlayer.state().blockUpdates().getLast());
        assertEquals(access.lastOpenedInventory(), access.topInventory(player));
    }

    @Test
    void promptPromptClosesMenuOpensDialogAndReopensReactiveMenuOnSubmit() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        AtomicInteger promptOpens = new AtomicInteger();
        AtomicReference<ReactiveTextPromptRequest> openedRequest = new AtomicReference<>();
        AtomicReference<java.util.function.Consumer<String>> submit = new AtomicReference<>();
        AtomicReference<Runnable> cancel = new AtomicReference<>();
        PaperMenuRuntime.PaperDialogPromptSupport dialogPrompts = (viewer, request, onSubmit, onCancel) -> {
            promptOpens.incrementAndGet();
            openedRequest.set(request);
            submit.set(onSubmit);
            cancel.set(onCancel);
        };
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(),
                new RecordingSoundCueService(), sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
                queuedScheduler(scheduled), new MenuTraceController(), message -> { },
                PaperMenuRuntime.PaperVirtualSignSupport.live(), dialogPrompts);

        runtime.open(player, reactivePromptDialogMenu(new PromptState("")));
        Inventory inventory = access.lastOpenedInventory();

        runtime.onInventoryClick(click(player, inventory, UtilitySlot.RIGHT_1.resolveSlot(45), ClickType.LEFT));

        assertEquals(0, promptOpens.get());

        runNextTick(scheduled);
        runNextTick(scheduled);
        assertNull(access.topInventory(player));

        runtime.onInventoryClose(new InventoryCloseEvent(view(player, inventory)));

        assertEquals(0, promptOpens.get());

        runNextTick(scheduled);

        assertEquals(1, promptOpens.get());
        assertEquals(ReactiveTextPromptMode.PROMPT, openedRequest.get().preferredMode());
        assertEquals("Search", openedRequest.get().prompt());

        submit.get().accept("pain");

        runNextTick(scheduled);

        Inventory reopened = access.lastOpenedInventory();
        assertEquals("Search: pain", slotTitle(access, reopened, UtilitySlot.RIGHT_1.resolveSlot(45)));
        assertEquals(reopened, access.topInventory(player));
        assertTrue(cancel.get() != null);
    }

    @Test
    void promptTimeoutCancelsExactlyOnceIgnoresLateCallbacksAndRestoresTheMenu() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        Deque<Runnable> timeouts = new ArrayDeque<>();
        AtomicLong timeoutDelay = new AtomicLong();
        AtomicReference<java.util.function.Consumer<String>> submit = new AtomicReference<>();
        AtomicReference<Runnable> cancel = new AtomicReference<>();
        PaperMenuTaskScheduler taskScheduler = PaperMenuTaskScheduler.testing(
                sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
                queuedScheduler(scheduled),
                (location, action) -> {
                    action.run();
                    return MenuTickHandle.noop();
                },
                (viewer, delayTicks, action) -> {
                    timeoutDelay.set(delayTicks);
                    timeouts.addLast(action);
                    return () -> timeouts.remove(action);
                });
        PaperMenuRuntime.PaperDialogPromptSupport dialogPrompts = (viewer, request, onSubmit, onCancel) -> {
            submit.set(onSubmit);
            cancel.set(onCancel);
        };
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                renderer(),
                new RecordingSoundCueService(),
                taskScheduler,
                new MenuTraceController(),
                message -> { },
                PaperMenuRuntime.PaperVirtualSignSupport.live(),
                dialogPrompts);

        runtime.open(player, timeoutPromptMenu());
        Inventory inventory = access.lastOpenedInventory();
        runtime.onInventoryClick(click(player, inventory, UtilitySlot.RIGHT_1.resolveSlot(45), ClickType.LEFT));
        runNextTick(scheduled);
        runNextTick(scheduled);
        runtime.onInventoryClose(new InventoryCloseEvent(view(player, inventory)));
        runNextTick(scheduled);

        assertEquals(4L * 60L * 20L, timeoutDelay.get());
        assertEquals(1, timeouts.size());
        assertTrue(submit.get() != null);
        assertTrue(cancel.get() != null);
        assertNull(access.topInventory(player));

        runNextTick(timeouts);
        runNextTick(scheduled);

        Inventory reopened = access.lastOpenedInventory();
        assertEquals("Cancelled: 1", slotTitle(access, reopened, UtilitySlot.RIGHT_1.resolveSlot(45)));
        assertEquals(reopened, access.topInventory(player));
        int openedCount = access.openedInventories.size();

        submit.get().accept("late");
        cancel.get().run();
        while (!scheduled.isEmpty()) {
            runNextTick(scheduled);
        }

        assertEquals(openedCount, access.openedInventories.size());
        assertEquals("Cancelled: 1", slotTitle(access, reopened, UtilitySlot.RIGHT_1.resolveSlot(45)));
    }

    @Test
    void deathDuringPromptCancelsItsTimeoutAndMakesLateCallbacksInert() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        Deque<Runnable> timeouts = new ArrayDeque<>();
        AtomicReference<java.util.function.Consumer<String>> submit = new AtomicReference<>();
        AtomicReference<Runnable> cancel = new AtomicReference<>();
        PaperMenuTaskScheduler taskScheduler = PaperMenuTaskScheduler.testing(
                sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
                queuedScheduler(scheduled),
                (location, action) -> {
                    action.run();
                    return MenuTickHandle.noop();
                },
                (viewer, delayTicks, action) -> {
                    timeouts.addLast(action);
                    return () -> timeouts.remove(action);
                });
        PaperMenuRuntime.PaperDialogPromptSupport dialogPrompts = (viewer, request, onSubmit, onCancel) -> {
            submit.set(onSubmit);
            cancel.set(onCancel);
        };
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                renderer(),
                new RecordingSoundCueService(),
                taskScheduler,
                new MenuTraceController(),
                message -> { },
                PaperMenuRuntime.PaperVirtualSignSupport.live(),
                dialogPrompts);

        runtime.open(player, timeoutPromptMenu());
        Inventory inventory = access.lastOpenedInventory();
        runtime.onInventoryClick(click(player, inventory, UtilitySlot.RIGHT_1.resolveSlot(45), ClickType.LEFT));
        runNextTick(scheduled);
        runNextTick(scheduled);
        runtime.onInventoryClose(new InventoryCloseEvent(view(player, inventory)));
        runNextTick(scheduled);

        assertEquals(1, timeouts.size());
        int openedCount = access.openedInventories.size();

        runtime.onPlayerDeath(player, true, new ArrayList<>());

        assertTrue(timeouts.isEmpty());
        submit.get().accept("late");
        cancel.get().run();
        while (!scheduled.isEmpty()) {
            runNextTick(scheduled);
        }

        assertNull(access.topInventory(player));
        assertEquals(openedCount, access.openedInventories.size());
    }

    @Test
    void promptCompletionRenderFailureQuarantinesTheClosedSession() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        AtomicReference<java.util.function.Consumer<String>> submit = new AtomicReference<>();
        AtomicBoolean failNativeRender = new AtomicBoolean();
        PaperMenuSlotRenderer delegate = renderer();
        PaperMenuSlotRenderer flakyRenderer = slot -> {
            if (failNativeRender.get()) {
                throw new IllegalStateException("native render failed");
            }
            return delegate.render(slot);
        };
        PaperMenuRuntime.PaperDialogPromptSupport dialogPrompts = (viewer, request, onSubmit, onCancel) ->
                submit.set(onSubmit);
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null,
                flakyRenderer, new RecordingSoundCueService(),
                sh.harold.library.menu.core.MenuTickScheduler.unsupported(), queuedScheduler(scheduled),
                new MenuTraceController(), message -> { }, PaperMenuRuntime.PaperVirtualSignSupport.live(), dialogPrompts);

        runtime.open(player, reactivePromptDialogMenu(new PromptState("")));
        Inventory inventory = access.lastOpenedInventory();
        PaperMenuSession session = (PaperMenuSession) inventory.getHolder(false);
        runtime.onInventoryClick(click(player, inventory, UtilitySlot.RIGHT_1.resolveSlot(45), ClickType.LEFT));
        runNextTick(scheduled);
        runNextTick(scheduled);
        runtime.onInventoryClose(new InventoryCloseEvent(view(player, inventory)));
        runNextTick(scheduled);

        submit.get().accept("updated");
        failNativeRender.set(true);
        assertDoesNotThrow(() -> runNextTick(scheduled));

        failNativeRender.set(false);
        int openedCount = access.openedInventories.size();
        assertDoesNotThrow(() -> session.refresh());
        assertEquals(openedCount, access.openedInventories.size());
        assertNull(access.topInventory(player));
    }

    @Test
    void compiledMenusCancelUnsupportedClickVariants() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());
        AtomicInteger count = new AtomicInteger();

        runtime.open(player, counterMenu(count));
        Inventory inventory = access.lastOpenedInventory();

        for (ClickType unsupported : List.of(
                ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT, ClickType.NUMBER_KEY,
                ClickType.DOUBLE_CLICK, ClickType.MIDDLE, ClickType.DROP,
                ClickType.CONTROL_DROP, ClickType.CREATIVE, ClickType.SWAP_OFFHAND,
                ClickType.UNKNOWN)) {
            InventoryClickEvent click = click(player, inventory, 10, unsupported);
            runtime.onInventoryClick(click);
            assertTrue(click.isCancelled());
        }
        assertEquals(0, count.get());
    }

    @Test
    void compiledMenusCancelDragsBeforeVanillaCanMoveDisplayItems() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());

        runtime.open(player, pagedMenu());
        Inventory inventory = access.lastOpenedInventory();
        InventoryDragEvent drag = dragEvent(player, inventory, Set.of(10), namedBukkitItem(Material.STONE, Material.STONE.name(), 1));

        runtime.onInventoryDrag(drag);

        assertTrue(drag.isCancelled());
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
                new RecordingSoundCueService(), sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
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
        assertTrue(staleClick.isCancelled());

        InventoryDragEvent staleDrag = dragEvent(player, ownedInventory, Set.of(10), namedBukkitItem(Material.STONE, Material.STONE.name(), 1));
        runtime.onInventoryDrag(staleDrag);
        assertTrue(staleDrag.isCancelled());
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
        assertTrue(click.isCancelled());
    }

    @Test
    void runtimeCloseFencesNewOpensAndKeepsOffRegionInventoriesGuarded() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null,
                renderer(), new RecordingSoundCueService());

        runtime.open(player, pagedMenu());
        Inventory inventory = access.lastOpenedInventory();
        runtime.close();

        assertTrue(runtime.requiresStaleInventoryGuard());
        assertEquals(inventory, access.topInventory(player));
        runtime.open(player, counterMenu(new AtomicInteger()));
        assertEquals(1, access.openedInventories.size());

        InventoryClickEvent staleClick = click(player, inventory, 10, ClickType.LEFT);
        runtime.onInventoryClick(staleClick);

        assertTrue(staleClick.isCancelled());
        assertNull(access.topInventory(player));
    }

    @Test
    void throwingTickReducerQuarantinesAndCancelsTheSession() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        AtomicReference<Runnable> tickAction = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        sh.harold.library.menu.core.MenuTickScheduler tickScheduler = (interval, action) -> {
            tickAction.set(action);
            return () -> cancelled.set(true);
        };
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null,
                renderer(), new RecordingSoundCueService(), tickScheduler);

        runtime.open(player, throwingTickMenu());
        assertDoesNotThrow(() -> tickAction.get().run());

        assertTrue(cancelled.get());
        assertNull(access.topInventory(player));
        assertEquals(List.of(viewerId), access.closedPlayers);
        assertDoesNotThrow(() -> tickAction.get().run());
    }

    @Test
    void throwingOpenedReducerQuarantinesTheRegisteredSession() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null,
                renderer(), new RecordingSoundCueService());

        assertDoesNotThrow(() -> runtime.open(player, throwingOpenedMenu()));

        assertNull(access.topInventory(player));
        assertEquals(List.of(viewerId), access.closedPlayers);
    }

    @Test
    void custodyPolicyRootOpenCannotMoveAStackIntoTheDetachedSession() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        AtomicReference<PaperMenuRuntime> runtimeReference = new AtomicReference<>();
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                renderer(),
                new RecordingSoundCueService(),
                sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
                queuedScheduler(scheduled));
        runtimeReference.set(runtime);
        ItemStack source = namedBukkitItem(Material.EMERALD, "Reentrant Source", 7);

        runtime.open(player, reentrantCustodyMenu(() -> runtimeReference.get().open(player, pagedMenu())));
        Inventory original = access.lastOpenedInventory();
        playerInventory(player).setItem(8, source);

        InventoryClickEvent click = click(player, original, original.getSize() + 8, ClickType.LEFT);
        assertDoesNotThrow(() -> runtime.onInventoryClick(click));

        assertTrue(click.isCancelled());
        assertEquals(source, playerInventory(player).getItem(8));
        assertEquals("Custody Target", slotTitle(access, original, 31));
        assertEquals(original, access.topInventory(player));

        while (!scheduled.isEmpty()) {
            runNextTick(scheduled);
        }

        Inventory successor = access.lastOpenedInventory();
        assertEquals("Profiles (1/3)", inventoryTitle(access, successor));
        assertEquals(successor, access.topInventory(player));
        runtime.onInventoryClick(click(player, original, 31, ClickType.LEFT));
        assertEquals(successor, access.topInventory(player));
        assertEquals(source, playerInventory(player).getItem(8));
    }

    @Test
    void deathWithoutKeepInventoryHandsExactTargetAndCursorCustodyToDropsOnce() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                renderer(),
                new RecordingSoundCueService());
        ItemStack targetItem = PaperMenuTestSupport.renderedItem(
                "emerald",
                7,
                Component.text("Exact Target"),
                List.of(Component.text("target lore")),
                true);
        ItemStack cursorItem = PaperMenuTestSupport.renderedItem(
                "diamond",
                3,
                Component.text("Exact Cursor"),
                List.of(Component.text("cursor lore")),
                true);

        runtime.open(player, reactiveDragInsertMenu(false));
        Inventory inventory = access.lastOpenedInventory();
        playerInventory(player).setItem(5, targetItem);
        playerInventory(player).setItem(6, cursorItem);
        runtime.onInventoryClick(click(player, inventory, inventory.getSize() + 5, ClickType.SHIFT_LEFT));
        runtime.onInventoryClick(click(player, inventory, inventory.getSize() + 6, ClickType.LEFT));
        for (int slot = 0; slot < playerInventory(player).getStorageContents().length; slot++) {
            if (playerInventory(player).getItem(slot) == null) {
                playerInventory(player).setItem(slot, namedBukkitItem(Material.STONE, "Filler " + slot, 1));
            }
        }
        List<ItemStack> drops = new ArrayList<>();

        runtime.onPlayerDeath(player, false, drops);

        assertEquals(1L, drops.stream().filter(targetItem::equals).count());
        assertEquals(1L, drops.stream().filter(cursorItem::equals).count());
        assertNull(player.getItemOnCursor());
        assertEquals("Custody Target", slotTitle(access, inventory, 31));
        int delivered = drops.size();

        runtime.onPlayerDeath(player, false, drops);
        runtime.onInventoryClose(new InventoryCloseEvent(view(player, inventory)));

        assertEquals(delivered, drops.size());
        assertEquals(1L, drops.stream().filter(targetItem::equals).count());
        assertEquals(1L, drops.stream().filter(cursorItem::equals).count());
    }

    @Test
    void deathWithKeepInventorySettlesFullInventoryOverflowExactlyOnce() {
        UUID viewerId = UUID.randomUUID();
        org.bukkit.World world = org.mockito.Mockito.mock(org.bukkit.World.class);
        PaperMenuTestSupport.TrackedPlayer trackedPlayer = PaperMenuTestSupport.trackedPlayer(
                viewerId,
                new Location(world, 0.0, 64.0, 0.0));
        Player player = trackedPlayer.player();
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                renderer(),
                new RecordingSoundCueService());
        ItemStack exact = PaperMenuTestSupport.renderedItem(
                "emerald",
                11,
                Component.text("Retained Overflow"),
                List.of(Component.text("exact metadata")),
                true);
        ItemStack cursorBlocker = namedBukkitItem(Material.STONE, "Cursor Blocker", 1);

        runtime.open(player, reactiveDragInsertMenu(false));
        Inventory inventory = access.lastOpenedInventory();
        playerInventory(player).setItem(5, exact);
        runtime.onInventoryClick(click(player, inventory, inventory.getSize() + 5, ClickType.SHIFT_LEFT));
        for (int slot = 0; slot < playerInventory(player).getStorageContents().length; slot++) {
            playerInventory(player).setItem(slot, namedBukkitItem(Material.STONE, "Filler " + slot, 1));
        }
        player.setItemOnCursor(cursorBlocker);

        runtime.onPlayerDeath(player, true, new ArrayList<>());
        runtime.onPlayerDeath(player, true, new ArrayList<>());

        org.mockito.ArgumentCaptor<ItemStack> delivered = org.mockito.ArgumentCaptor.forClass(ItemStack.class);
        org.mockito.Mockito.verify(world, org.mockito.Mockito.times(1)).dropItemNaturally(
                org.mockito.ArgumentMatchers.any(Location.class),
                delivered.capture());
        assertEquals(exact, delivered.getValue());
        assertEquals(cursorBlocker, player.getItemOnCursor());
        assertEquals("Custody Target", slotTitle(access, inventory, 31));
    }

    @Test
    void throwingDeathSettlementReducerCannotDuplicateDeliveredDrop() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                renderer(),
                new RecordingSoundCueService());
        ItemStack exact = PaperMenuTestSupport.renderedItem(
                "diamond",
                5,
                Component.text("Throwing Settlement"),
                List.of(Component.text("survives reducer failure")),
                true);

        runtime.open(player, throwingDeathSettlementMenu());
        Inventory inventory = access.lastOpenedInventory();
        playerInventory(player).setItem(5, exact);
        runtime.onInventoryClick(click(player, inventory, inventory.getSize() + 5, ClickType.SHIFT_LEFT));
        List<ItemStack> drops = new ArrayList<>();

        assertDoesNotThrow(() -> runtime.onPlayerDeath(player, false, drops));
        runtime.onInventoryClose(new InventoryCloseEvent(view(player, inventory)));
        runtime.onPlayerDeath(player, false, drops);

        assertEquals(1L, drops.stream().filter(exact::equals).count());
        assertNull(access.topInventory(player));
    }

    @Test
    void cancelledKickKeepsTheSessionActiveAndSuccessfulKickCleansOnce() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                renderer(),
                new RecordingSoundCueService());
        PaperMenuListener listener = new PaperMenuListener(runtime);
        AtomicInteger clicks = new AtomicInteger();

        runtime.open(player, counterMenu(clicks));
        Inventory inventory = access.lastOpenedInventory();
        PlayerKickEvent cancelled = org.mockito.Mockito.mock(PlayerKickEvent.class);
        org.mockito.Mockito.when(cancelled.getPlayer()).thenReturn(player);
        org.mockito.Mockito.when(cancelled.isCancelled()).thenReturn(true);
        listener.onPlayerKick(cancelled);
        runtime.onInventoryClick(click(player, inventory, 10, ClickType.LEFT));

        assertEquals(1, clicks.get());

        PlayerKickEvent successful = org.mockito.Mockito.mock(PlayerKickEvent.class);
        org.mockito.Mockito.when(successful.getPlayer()).thenReturn(player);
        listener.onPlayerKick(successful);
        assertDoesNotThrow(() -> listener.onPlayerKick(successful));
        InventoryClickEvent staleClick = click(player, inventory, 10, ClickType.LEFT);
        runtime.onInventoryClick(staleClick);

        assertTrue(staleClick.isCancelled());
        assertEquals(1, clicks.get());
    }

    @Test
    void rootOpenStateFactoryFailureLeavesThePreviousSessionUsable() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                renderer(),
                new RecordingSoundCueService());

        runtime.open(player, pagedMenu());
        Inventory original = access.lastOpenedInventory();
        PaperMenuSession session = (PaperMenuSession) original.getHolder(false);

        assertDoesNotThrow(() -> runtime.open(player, throwingStateFactoryMenu()));

        assertEquals(original, access.topInventory(player));
        assertEquals(1, access.openedInventories.size());
        assertFalse(session.custodyTransitioning());

        runtime.onInventoryClick(click(player, original, 53, ClickType.LEFT));

        assertEquals("Profiles (2/3)", inventoryTitle(access, access.lastOpenedInventory()));
        assertFalse(session.custodyTransitioning());
    }

    @Test
    void childStateFactoryFailuresLeaveTheCurrentSessionUsable() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                renderer(),
                new RecordingSoundCueService());

        runtime.open(player, pagedMenu());
        Inventory original = access.lastOpenedInventory();
        PaperMenuSession session = (PaperMenuSession) original.getHolder(false);

        assertDoesNotThrow(() -> session.open(throwingStateFactoryMenu()));
        assertFalse(session.custodyTransitioning());
        assertDoesNotThrow(() -> session.replace(throwingStateFactoryMenu()));

        assertEquals(original, access.topInventory(player));
        assertEquals(1, access.openedInventories.size());
        assertFalse(session.custodyTransitioning());

        runtime.onInventoryClick(click(player, original, 53, ClickType.LEFT));

        assertEquals("Profiles (2/3)", inventoryTitle(access, access.lastOpenedInventory()));
        assertFalse(session.custodyTransitioning());
    }

    @Test
    void malformedFrameActionDoesNotWedgeTheSession() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        AtomicInteger usableAction = new AtomicInteger();
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                renderer(),
                new RecordingSoundCueService());

        runtime.open(player, malformedFrameMenu(usableAction));
        Inventory inventory = access.lastOpenedInventory();
        PaperMenuSession session = (PaperMenuSession) inventory.getHolder(false);

        assertDoesNotThrow(() -> runtime.onInventoryClick(click(player, inventory, 1, ClickType.LEFT)));
        assertFalse(session.custodyTransitioning());
        assertEquals(inventory, access.topInventory(player));

        runtime.onInventoryClick(click(player, inventory, 2, ClickType.LEFT));

        assertEquals(1, usableAction.get());
        assertFalse(session.custodyTransitioning());
    }

    @Test
    void rootBackWithoutHeldCustodyDoesNotRerender() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        AtomicInteger renderCalls = new AtomicInteger();
        PaperMenuSlotRenderer delegate = renderer();
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                slot -> {
                    renderCalls.incrementAndGet();
                    return delegate.render(slot);
                },
                new RecordingSoundCueService());

        runtime.open(player, settlementNavigationMenu(pagedMenu()));
        Inventory inventory = access.lastOpenedInventory();
        PaperMenuSession session = (PaperMenuSession) inventory.getHolder(false);
        int before = renderCalls.get();

        session.back();

        assertEquals(before, renderCalls.get());
        assertEquals(inventory, access.topInventory(player));
        assertFalse(session.custodyTransitioning());
    }

    @Test
    void compiledActionNavigationIsDeferredUntilTheCallbackCompletes() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                renderer(),
                new RecordingSoundCueService(),
                sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
                queuedScheduler(scheduled));

        runtime.open(player, launcherMenu());
        Inventory original = access.lastOpenedInventory();
        runtime.onInventoryClick(click(player, original, 10, ClickType.LEFT));

        assertEquals(1, access.openedInventories.size());
        assertEquals(original, access.topInventory(player));

        while (!scheduled.isEmpty()) {
            runNextTick(scheduled);
        }

        assertEquals("Gallery", inventoryTitle(access, access.lastOpenedInventory()));
        assertEquals(access.lastOpenedInventory(), access.topInventory(player));
    }

    @Test
    void throwingCompiledActionDiscardsQueuedNavigation() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                renderer(),
                new RecordingSoundCueService(),
                sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
                queuedScheduler(scheduled));

        runtime.open(player, throwingQueuedNavigationMenu());
        Inventory original = access.lastOpenedInventory();

        assertDoesNotThrow(() -> runtime.onInventoryClick(click(player, original, 10, ClickType.LEFT)));
        while (!scheduled.isEmpty()) {
            runNextTick(scheduled);
        }

        assertEquals(1, access.openedInventories.size());
        assertNull(access.topInventory(player));
        assertEquals(List.of(viewerId), access.closedPlayers);
    }


    @Test
    void reactiveReducerRootOpenSupersedesTheOldOutcomeOnTheNextTick() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        AtomicReference<PaperMenuRuntime> runtimeReference = new AtomicReference<>();
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                renderer(),
                new RecordingSoundCueService(),
                sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
                queuedScheduler(scheduled));
        runtimeReference.set(runtime);

        runtime.open(player, reentrantClickMenu(() -> runtimeReference.get().open(player, pagedMenu())));
        Inventory original = access.lastOpenedInventory();

        InventoryClickEvent click = click(player, original, 22, ClickType.LEFT);
        assertDoesNotThrow(() -> runtime.onInventoryClick(click));

        assertTrue(click.isCancelled());
        assertEquals(original, access.topInventory(player));
        assertEquals("Replace Later", slotTitle(access, original, 22));

        while (!scheduled.isEmpty()) {
            runNextTick(scheduled);
        }

        Inventory successor = access.lastOpenedInventory();
        assertEquals("Profiles (1/3)", inventoryTitle(access, successor));
        assertEquals(successor, access.topInventory(player));
    }

    @Test
    void throwingClickReducerQuarantinesTheRegisteredSession() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                renderer(),
                new RecordingSoundCueService());

        runtime.open(player, throwingClickMenu());
        Inventory inventory = access.lastOpenedInventory();
        InventoryClickEvent click = click(player, inventory, 22, ClickType.LEFT);

        assertDoesNotThrow(() -> runtime.onInventoryClick(click));

        assertTrue(click.isCancelled());
        assertNull(access.topInventory(player));
        assertEquals(List.of(viewerId), access.closedPlayers);
    }

    @Test
    void throwingReactiveRendererDuringClickQuarantinesTheRegisteredSession() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                renderer(),
                new RecordingSoundCueService());

        runtime.open(player, throwingClickRendererMenu());
        Inventory inventory = access.lastOpenedInventory();
        InventoryClickEvent click = click(player, inventory, 22, ClickType.LEFT);

        assertDoesNotThrow(() -> runtime.onInventoryClick(click));

        assertTrue(click.isCancelled());
        assertNull(access.topInventory(player));
        assertEquals(List.of(viewerId), access.closedPlayers);
    }


    @Test
    void childBackUsesBreadcrumbHistoryWithoutRecordingTabFrames() {
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
        inventory = access.lastOpenedInventory();
        assertEquals("Profiles", slotTitle(access, inventory, 3));
        assertEquals("Progress", slotTitle(access, inventory, 4));
        assertEquals("Farming XLIX", slotTitle(access, inventory, 19));

        InventoryClickEvent backToRoot = click(player, inventory, 48, ClickType.LEFT);
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
        inventory = access.lastOpenedInventory();
        assertEquals("Tab 1", slotTitle(access, inventory, 1));
        assertEquals("Tab 7", slotTitle(access, inventory, 7));
        assertEquals("Tab 0 Item 0", slotTitle(access, inventory, 19));

        InventoryClickEvent jumpEnd = click(player, inventory, 8, ClickType.RIGHT);
        runtime.onInventoryClick(jumpEnd);

        assertTrue(jumpEnd.isCancelled());
        inventory = access.lastOpenedInventory();
        assertEquals("Tab 3", slotTitle(access, inventory, 1));
        assertEquals("Tab 9", slotTitle(access, inventory, 7));
        assertEquals("Tab 0 Item 0", slotTitle(access, inventory, 19));

        InventoryClickEvent switchTab = click(player, inventory, 7, ClickType.LEFT);
        runtime.onInventoryClick(switchTab);

        assertTrue(switchTab.isCancelled());
        inventory = access.lastOpenedInventory();
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
        inventory = access.lastOpenedInventory();
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
    void outsideWindowBorderClicksReturnCursorCustodyToTheExactOrigin() {
        for (ClickType borderClick : List.of(ClickType.WINDOW_BORDER_LEFT, ClickType.WINDOW_BORDER_RIGHT)) {
            UUID viewerId = UUID.randomUUID();
            Player player = player(viewerId);
            TestPaperMenuAccess access = new TestPaperMenuAccess();
            PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null,
                    renderer(), new RecordingSoundCueService());
            ItemStack source = namedBukkitItem(Material.EMERALD, "Border Return", 3);

            runtime.open(player, reactiveDragInsertMenu(false));
            Inventory inventory = access.lastOpenedInventory();
            playerInventory(player).setItem(5, source);
            runtime.onInventoryClick(click(player, inventory, inventory.getSize() + 5, ClickType.LEFT));
            assertEquals(source, player.getItemOnCursor());

            InventoryClickEvent outside = click(player, inventory, -999, borderClick);
            runtime.onInventoryClick(outside);

            assertTrue(outside.isCancelled());
            assertEquals(source, playerInventory(player).getItem(5));
            assertNull(player.getItemOnCursor());
        }
    }

    @Test
    void externalTargetMutationRejectsWithoutRestoringASecondSourceCopy() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null,
                renderer(), new RecordingSoundCueService());
        ItemStack source = namedBukkitItem(Material.EMERALD, "Exact Source", 7);

        runtime.open(player, reactiveClickInsertMenu(false));
        Inventory inventory = access.lastOpenedInventory();
        playerInventory(player).setItem(5, source);
        runtime.onInventoryClick(click(player, inventory, inventory.getSize() + 5, ClickType.LEFT));
        assertNull(playerInventory(player).getItem(5));

        ItemStack external = namedBukkitItem(Material.DIAMOND, "External Mutation", 1);
        inventory.setItem(31, external);
        runtime.onInventoryClick(click(player, inventory, 31, ClickType.LEFT));

        assertNull(playerInventory(player).getItem(5));
        assertEquals(external, inventory.getItem(31));

        inventory.setItem(31, source.clone());
        runtime.onInventoryClose(new InventoryCloseEvent(view(player, inventory)));

        assertEquals(source, playerInventory(player).getItem(5));
        assertEquals("Click An Inventory Stack", slotTitle(access, inventory, 31));
    }

    @Test
    void refreshAfterExternalTargetRemovalQuarantinesWithoutRecreatingTheStack() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null,
                renderer(), new RecordingSoundCueService());
        ItemStack source = namedBukkitItem(Material.EMERALD, "Moved Elsewhere", 7);

        runtime.open(player, reactiveClickInsertMenu(false));
        Inventory inventory = access.lastOpenedInventory();
        PaperMenuSession session = (PaperMenuSession) inventory.getHolder(false);
        playerInventory(player).setItem(5, source);
        runtime.onInventoryClick(click(player, inventory, inventory.getSize() + 5, ClickType.LEFT));
        assertEquals(source, inventory.getItem(31));

        inventory.setItem(31, null);
        runtime.refresh(session);

        assertNull(inventory.getItem(31));
        assertNull(playerInventory(player).getItem(5));
        assertNull(access.topInventory(player));
        int openedCount = access.openedInventories.size();
        runtime.refresh(session);
        assertEquals(openedCount, access.openedInventories.size());
    }

    @Test
    void titleRebuildSettlesCustodyBeforeOpeningTheReplacementInventory() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null,
                renderer(), new RecordingSoundCueService());
        AtomicBoolean alternateTitle = new AtomicBoolean();
        ItemStack source = namedBukkitItem(Material.EMERALD, "Rebuild Source", 4);

        runtime.open(player, reactiveCustodyTitleMenu(alternateTitle));
        Inventory original = access.lastOpenedInventory();
        playerInventory(player).setItem(6, source);
        runtime.onInventoryClick(click(player, original, original.getSize() + 6, ClickType.LEFT));
        assertNull(playerInventory(player).getItem(6));
        assertEquals(source, original.getItem(31));

        alternateTitle.set(true);
        runtime.refresh((PaperMenuSession) original.getHolder(false));

        Inventory replacement = access.lastOpenedInventory();
        assertNotSame(original, replacement);
        assertEquals("Custody B", inventoryTitle(access, replacement));
        assertEquals(source, playerInventory(player).getItem(6));
        assertEquals("Custody Target", slotTitle(access, original, 31));
        assertEquals("Custody Target", slotTitle(access, replacement, 31));
        assertNull(player.getItemOnCursor());
    }

    @Test
    void ordinaryRefreshLeavesOccupiedTargetUntouchedAndAdvancesItsBase() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null,
                renderer(), new RecordingSoundCueService());
        AtomicBoolean alternateBase = new AtomicBoolean();
        ItemStack source = namedBukkitItem(Material.EMERALD, "Occupied Target", 4);

        runtime.open(player, reactiveCustodyBaseMenu(alternateBase));
        Inventory inventory = access.lastOpenedInventory();
        playerInventory(player).setItem(6, source);
        runtime.onInventoryClick(click(player, inventory, inventory.getSize() + 6, ClickType.LEFT));

        alternateBase.set(true);
        runtime.refresh((PaperMenuSession) inventory.getHolder(false));

        assertEquals(source, inventory.getItem(31));
        runtime.onInventoryClick(click(player, inventory, 31, ClickType.LEFT));
        assertEquals(source, playerInventory(player).getItem(6));
        assertEquals("New Target Base", slotTitle(access, inventory, 31));
    }

    @Test
    void rootReplacementSettlesTargetCustodyExactlyOnce() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null,
                renderer(), new RecordingSoundCueService());
        ItemStack source = namedBukkitItem(Material.EMERALD, "Navigation Source", 5);

        runtime.open(player, reactiveClickInsertMenu(false));
        Inventory original = access.lastOpenedInventory();
        playerInventory(player).setItem(7, source);
        runtime.onInventoryClick(click(player, original, original.getSize() + 7, ClickType.LEFT));

        runtime.open(player, pagedMenu());

        assertEquals(source, playerInventory(player).getItem(7));
        assertNull(player.getItemOnCursor());
        assertEquals("Click An Inventory Stack", slotTitle(access, original, 31));
        assertEquals("Profiles (1/3)", inventoryTitle(access, access.lastOpenedInventory()));
    }

    @Test
    void forcedCloseLeavesExactCursorCustodyForTheHostToResolve() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null,
                renderer(), new RecordingSoundCueService());
        ItemStack source = namedBukkitItem(Material.EMERALD, "Host Cursor", 2);

        runtime.open(player, reactiveDragInsertMenu(false));
        Inventory inventory = access.lastOpenedInventory();
        playerInventory(player).setItem(4, source);
        runtime.onInventoryClick(click(player, inventory, inventory.getSize() + 4, ClickType.LEFT));

        runtime.onInventoryClose(new InventoryCloseEvent(view(player, inventory)));

        assertNull(playerInventory(player).getItem(4));
        assertEquals(source, player.getItemOnCursor());
    }

    @Test
    void disconnectReturnsExactCursorCustodyToStorage() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null,
                renderer(), new RecordingSoundCueService());
        ItemStack source = namedBukkitItem(Material.EMERALD, "Disconnect Cursor", 2);

        runtime.open(player, reactiveDragInsertMenu(false));
        Inventory inventory = access.lastOpenedInventory();
        playerInventory(player).setItem(4, source);
        runtime.onInventoryClick(click(player, inventory, inventory.getSize() + 4, ClickType.LEFT));

        runtime.onPlayerDisconnect(player);

        assertEquals(source, playerInventory(player).getItem(4));
        assertNull(player.getItemOnCursor());
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
    void reactiveMenusCancelBottomOnlyDragsWhileOwningTheCursor() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(), new RecordingSoundCueService());

        runtime.open(player, reactiveDragInsertMenu(false));
        Inventory inventory = access.lastOpenedInventory();
        int topSize = inventory.getSize();
        ItemStack sourceItem = namedBukkitItem(Material.EMERALD, "Cursor Item", 2);
        playerInventory(player).setItem(4, sourceItem);

        runtime.onInventoryClick(click(player, inventory, topSize + 4, ClickType.LEFT));
        InventoryDragEvent drag = dragEvent(player, inventory, Set.of(topSize + 8), player.getItemOnCursor());
        runtime.onInventoryDrag(drag);

        assertTrue(drag.isCancelled());
        assertNull(playerInventory(player).getItem(4));
        assertEquals("Cursor Item", itemTitle(player.getItemOnCursor()));
        assertNull(playerInventory(player).getItem(8));
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
        assertEquals("Custody Target", itemTitle(inventory.getItem(31)));
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
                new RecordingSoundCueService(), sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
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
                new RecordingSoundCueService(), sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
                queuedScheduler(scheduled));

        runtime.open(player, launcherMenu());
        Inventory rootInventory = access.lastOpenedInventory();

        InventoryClickEvent openChild = click(player, rootInventory, 10, ClickType.LEFT);
        runtime.onInventoryClick(openChild);

        assertTrue(openChild.isCancelled());
        assertEquals(1, access.openedInventories.size());
        assertEquals(2, scheduled.size());

        InventoryClickEvent secondPacket = click(player, rootInventory, 10, ClickType.LEFT);
        runtime.onInventoryClick(secondPacket);

        assertTrue(secondPacket.isCancelled());
        assertEquals(2, scheduled.size());

        runNextTick(scheduled);
        runNextTick(scheduled);

        assertEquals(2, access.openedInventories.size());
        assertEquals("Gallery", inventoryTitle(access, access.lastOpenedInventory()));
    }

    @Test
    void manualCloseCancelsAQueuedNavigationInsteadOfLeavingAHeadlessSession() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                renderer(),
                new RecordingSoundCueService(),
                sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
                queuedScheduler(scheduled));

        runtime.open(player, launcherMenu());
        Inventory original = access.lastOpenedInventory();
        PaperMenuSession session = (PaperMenuSession) original.getHolder(false);
        runtime.onInventoryClick(click(player, original, 10, ClickType.LEFT));

        assertFalse(session.custodyTransitioning());
        access.closeInventory(player);
        runtime.onInventoryClose(new InventoryCloseEvent(view(player, original)));

        assertFalse(session.custodyTransitioning());
        assertNull(access.topInventory(player));

        while (!scheduled.isEmpty()) {
            runNextTick(scheduled);
        }

        assertEquals(1, access.openedInventories.size());
        assertNull(access.topInventory(player));

        session.refresh();

        assertEquals(1, access.openedInventories.size());
        assertNull(access.topInventory(player));
    }

    @Test
    void manualCloseCancelsAQueuedReactiveRebuildInsteadOfReopeningIt() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                renderer(),
                new RecordingSoundCueService(),
                sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
                queuedScheduler(scheduled));

        runtime.open(player, reactiveTitleChangingMenu());
        Inventory original = access.lastOpenedInventory();
        PaperMenuSession session = (PaperMenuSession) original.getHolder(false);
        runtime.onInventoryClick(click(player, original, 22, ClickType.LEFT));

        assertTrue(session.custodyTransitioning());
        assertEquals(1, access.openedInventories.size());

        access.closeInventory(player);
        runtime.onInventoryClose(new InventoryCloseEvent(view(player, original)));

        assertFalse(session.custodyTransitioning());

        while (!scheduled.isEmpty()) {
            runNextTick(scheduled);
        }

        assertEquals(1, access.openedInventories.size());
        assertNull(access.topInventory(player));

        session.refresh();

        assertEquals(1, access.openedInventories.size());
        assertNull(access.topInventory(player));
    }

    @Test
    void queuedNavigationSuspendsTicksAndRefreshesUntilItCommits() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        AtomicReference<Runnable> tickAction = new AtomicReference<>();
        AtomicInteger tickCalls = new AtomicInteger();
        sh.harold.library.menu.core.MenuTickScheduler tickScheduler = (interval, action) -> {
            tickAction.set(action);
            return MenuTickHandle.noop();
        };
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                renderer(),
                new RecordingSoundCueService(),
                tickScheduler,
                queuedScheduler(scheduled));
        Menu successor = pagedMenu();

        runtime.open(player, tickingNavigationMenu(successor, tickCalls));
        Inventory original = access.lastOpenedInventory();
        PaperMenuSession session = (PaperMenuSession) original.getHolder(false);
        runtime.onInventoryClick(click(player, original, 22, ClickType.LEFT));

        assertTrue(session.custodyTransitioning());

        session.refresh();
        tickAction.get().run();

        assertEquals(0, tickCalls.get());

        while (!scheduled.isEmpty()) {
            runNextTick(scheduled);
        }

        assertEquals(2, access.openedInventories.size());
        assertEquals("Profiles (1/3)", inventoryTitle(access, access.lastOpenedInventory()));
        assertEquals(access.lastOpenedInventory(), access.topInventory(player));
        assertEquals(successor, session.state().menu());
        assertFalse(session.custodyTransitioning());
    }

    @Test
    void rejectedChildNavigationRendersTheStateCommittedByCustodySettlement() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        PaperMenuRuntime runtime = new PaperMenuRuntime(
                access,
                id -> id.equals(viewerId) ? player : null,
                renderer(),
                new RecordingSoundCueService());
        ReactiveMenu menu = settlementNavigationMenu(pagedMenu());

        runtime.open(player, menu);
        Inventory inventory = access.lastOpenedInventory();
        PaperMenuSession session = (PaperMenuSession) inventory.getHolder(false);
        for (int depth = 0; depth < 32; depth++) {
            session.state().openChild(menu);
        }
        session.refresh(player);
        ItemStack source = namedBukkitItem(Material.EMERALD, "Exact Source", 7);
        playerInventory(player).setItem(5, source);

        runtime.onInventoryClick(click(player, inventory, inventory.getSize() + 5, ClickType.LEFT));
        runtime.onInventoryClick(click(player, inventory, 22, ClickType.LEFT));

        assertEquals(source, playerInventory(player).getItem(5));
        assertEquals("Settled Target", slotTitle(access, inventory, 31));
        assertEquals("Use Settled Action", slotTitle(access, inventory, 22));
        assertEquals(inventory, access.topInventory(player));
        assertEquals(menu, session.state().menu());
        assertFalse(session.custodyTransitioning());

        runtime.onInventoryClick(click(player, inventory, 22, ClickType.LEFT));

        assertEquals("Settled Action Used", slotTitle(access, inventory, 22));
    }

    @Test
    void inventoryCloseFromClickIsDeferredUntilScheduled() {
        UUID viewerId = UUID.randomUUID();
        Player player = player(viewerId);
        TestPaperMenuAccess access = new TestPaperMenuAccess();
        Deque<Runnable> scheduled = new ArrayDeque<>();
        PaperMenuRuntime runtime = new PaperMenuRuntime(access, id -> id.equals(viewerId) ? player : null, renderer(),
                new RecordingSoundCueService(), sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
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
                new RecordingSoundCueService(), sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
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
                new RecordingSoundCueService(), sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
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
                new RecordingSoundCueService(), sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
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
                new RecordingSoundCueService(), sh.harold.library.menu.core.MenuTickScheduler.unsupported(),
                Runnable::run, trace, logs::add);

        runtime.open(player, overflowGalleryMenu());
        Inventory inventory = access.lastOpenedInventory();
        runtime.onInventoryClick(click(player, inventory, 8, ClickType.RIGHT));

        inventory = access.lastOpenedInventory();
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
                            context.replace(toggleMenu(enabled));
                        })
                        .build())
                .build();
    }

    private static Menu toggleLauncherMenu(AtomicBoolean enabled) {
        return new StandardMenuService().list()
                .title("Settings")
                .addItem(MenuButton.builder(MenuIcon.vanilla("stone"))
                        .name("Open Toggle")
                        .action(ActionVerb.OPEN, context -> context.open(toggleMenu(enabled)))
                        .build())
                .build();
    }

    private static ReactiveMenu reactivePromptMenu(PromptState initialState) {
        return reactivePromptMenu(initialState,
                state -> ReactiveTextPromptRequest.sign("prompt-search", "Search", state.query()));
    }

    private static ReactiveMenu reactivePromptDialogMenu(PromptState initialState) {
        return reactivePromptMenu(initialState,
                state -> ReactiveTextPromptRequest.prompt("prompt-search", "Search", state.query()));
    }

    private static ReactiveMenu timeoutPromptMenu() {
        return new StandardMenuService().reactiveList()
                .stateFactory(() -> new TimeoutPromptState(0, ""))
                .render(state -> ReactiveListView.builder("Timed Prompt")
                        .utility(UtilitySlot.RIGHT_1, MenuButton.builder(MenuIcon.vanilla("oak_sign"))
                                .name(!state.submission().isBlank()
                                        ? "Submitted: " + state.submission()
                                        : state.cancellations() > 0
                                        ? "Cancelled: " + state.cancellations()
                                        : "Open Timed Prompt")
                                .emit(ActionVerb.BROWSE, "prompt", "open-prompt")
                                .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.Click click
                            && "open-prompt".equals(click.message())) {
                        return ReactiveMenuResult.effect(new ReactiveMenuEffect.RequestTextPrompt(
                                ReactiveTextPromptRequest.prompt("timed-prompt", "Timed Prompt", "")));
                    }
                    if (input instanceof ReactiveMenuInput.TextPromptCancelled cancelled
                            && "timed-prompt".equals(cancelled.key())) {
                        return ReactiveMenuResult.update(
                                new TimeoutPromptState(state.cancellations() + 1, state.submission()));
                    }
                    if (input instanceof ReactiveMenuInput.TextPromptSubmitted submitted
                            && "timed-prompt".equals(submitted.key())) {
                        return ReactiveMenuResult.update(
                                new TimeoutPromptState(state.cancellations(), submitted.value()));
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
    }

    private static ReactiveMenu reactivePromptMenu(
            PromptState initialState,
            java.util.function.Function<PromptState, ReactiveTextPromptRequest> promptFactory
    ) {
        return new StandardMenuService().reactiveList()
                .stateFactory(() -> initialState)
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
                        return ReactiveMenuResult.effect(new ReactiveMenuEffect.RequestTextPrompt(
                                promptFactory.apply(state)));
                    }
                    if (input instanceof ReactiveMenuInput.TextPromptSubmitted submitted
                            && submitted.key().equals("prompt-search")) {
                        return ReactiveMenuResult.update(new PromptState(submitted.value()));
                    }
                    if (input instanceof ReactiveMenuInput.TextPromptCancelled cancelled
                            && cancelled.key().equals("prompt-search")) {
                        return ReactiveMenuResult.unchanged();
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
    }

    private static ReactiveMenu chainedSignPromptMenu() {
        return new StandardMenuService().reactiveList()
                .stateFactory(() -> new PromptState(""))
                .render(state -> ReactiveListView.builder("Chained Sign Prompt")
                        .addItem(MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                                .name(state.query().isBlank() ? "No Query" : "Query: " + state.query())
                                .build())
                        .utility(UtilitySlot.RIGHT_1, MenuButton.builder(MenuIcon.vanilla("oak_sign"))
                                .name("Open Prompt")
                                .emit(ActionVerb.BROWSE, "prompt", "open-first")
                                .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.Click click
                            && "open-first".equals(click.message())) {
                        return ReactiveMenuResult.effect(new ReactiveMenuEffect.RequestTextPrompt(
                                ReactiveTextPromptRequest.sign("first-sign", "First", "")));
                    }
                    if (input instanceof ReactiveMenuInput.TextPromptSubmitted submitted
                            && submitted.key().equals("first-sign")) {
                        return ReactiveMenuResult.effect(new ReactiveMenuEffect.RequestTextPrompt(
                                ReactiveTextPromptRequest.sign("second-sign", "Second", submitted.value())));
                    }
                    if (input instanceof ReactiveMenuInput.TextPromptSubmitted submitted
                            && submitted.key().equals("second-sign")) {
                        return ReactiveMenuResult.update(new PromptState(submitted.value()));
                    }
                    return ReactiveMenuResult.unchanged();
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

    private static Menu throwingQueuedNavigationMenu() {
        return new StandardMenuService().list()
                .title("Throwing Navigation")
                .addItem(MenuButton.builder(MenuIcon.vanilla("stone"))
                        .name("Queue Then Throw")
                        .action(ActionVerb.OPEN, context -> {
                            context.open(galleryMenu());
                            throw new IllegalStateException("compiled action failed");
                        })
                        .build())
                .build();
    }

    private static Menu malformedFrameMenu(AtomicInteger usableAction) {
        MenuFrame root = new MenuFrame(
                Component.text("Malformed Frame"),
                List.of(
                        new MenuSlot(
                                1,
                                MenuIcon.vanilla("barrier"),
                                Component.text("Missing Frame"),
                                List.of(),
                                false,
                                Map.of(MenuClick.LEFT, MenuInteraction.of(
                                        ActionVerb.OPEN,
                                        new MenuSlotAction.OpenFrame("missing")))),
                        new MenuSlot(
                                2,
                                MenuIcon.vanilla("stone"),
                                Component.text("Still Usable"),
                                List.of(),
                                false,
                                Map.of(MenuClick.LEFT, MenuInteraction.of(
                                        ActionVerb.VIEW,
                                        new MenuSlotAction.Execute(context -> usableAction.incrementAndGet()))))));
        return new Menu() {
            @Override
            public Component title() {
                return Component.text("Malformed Frame");
            }

            @Override
            public String initialFrameId() {
                return "root";
            }

            @Override
            public Set<String> frameIds() {
                return Set.of("root");
            }

            @Override
            public MenuFrame frame(String frameId) {
                if (!"root".equals(frameId)) {
                    throw new IllegalArgumentException("Unknown frame: " + frameId);
                }
                return root;
            }

            @Override
            public MenuGeometry geometry() {
                return MenuGeometry.CANVAS;
            }

            @Override
            public int rows() {
                return 1;
            }
        };
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
                .stateFactory(() -> locked)
                .custodyTarget("center", 31)
                .custodyPolicy((state, gesture, snapshot) -> {
                    if (state) {
                        return MenuCustodyDecision.reject();
                    }
                    if (gesture instanceof MenuCustodyGesture.ViewerClick viewer
                            && viewer.slot().item() != null
                            && snapshot.cursor().isEmpty()
                            && !snapshot.targets().containsKey("center")) {
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
                        .place(31, MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                        .name("Click An Inventory Stack")
                                        .description("The source slot clears when the stack loads.")
                                        .build())
                        .build())
                .reduce((state, input) -> ReactiveMenuResult.unchanged())
                .build();
    }

    private static ReactiveMenu settlementNavigationMenu(Menu successor) {
        return new StandardMenuService().reactiveCanvas()
                .fillWithBlackPane(false)
                .stateFactory(() -> new SettlementNavigationState(false, false))
                .custodyTarget("center", 31)
                .custodyPolicy((state, gesture, snapshot) -> {
                    if (gesture instanceof MenuCustodyGesture.ViewerClick viewer
                            && viewer.slot().item() != null
                            && !snapshot.targets().containsKey("center")) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.target("center"));
                    }
                    return MenuCustodyDecision.reject();
                })
                .render(state -> ReactiveMenuView.builder("Settlement Navigation")
                        .place(22, MenuButton.builder(MenuIcon.vanilla("book"))
                                .name(state.actionUsed()
                                        ? "Settled Action Used"
                                        : state.settled()
                                        ? "Use Settled Action"
                                        : "Open Child")
                                .emit(ActionVerb.OPEN, "open",
                                        state.settled() ? "use-settled-action" : "open-child")
                                .build())
                        .place(31, MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                .name(state.settled() ? "Settled Target" : "Custody Target")
                                .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.CustodyCommitted committed
                            && committed.gesture() instanceof MenuCustodyGesture.Settle) {
                        return ReactiveMenuResult.update(
                                new SettlementNavigationState(true, state.actionUsed()));
                    }
                    if (input instanceof ReactiveMenuInput.Click click
                            && "open-child".equals(click.message())) {
                        return ReactiveMenuResult.effect(new ReactiveMenuEffect.Open(successor));
                    }
                    if (input instanceof ReactiveMenuInput.Click click
                            && "use-settled-action".equals(click.message())) {
                        return ReactiveMenuResult.update(new SettlementNavigationState(true, true));
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
    }

    private static ReactiveMenu reactiveCustodyTitleMenu(AtomicBoolean alternateTitle) {
        return new StandardMenuService().reactiveCanvas()
                .fillWithBlackPane(false)
                .stateFactory(() -> false)
                .custodyTarget("center", 31)
                .custodyPolicy((state, gesture, snapshot) -> {
                    if (gesture instanceof MenuCustodyGesture.ViewerClick viewer
                            && viewer.slot().item() != null
                            && !snapshot.targets().containsKey("center")) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.target("center"));
                    }
                    if (gesture instanceof MenuCustodyGesture.TargetClick
                            && snapshot.targets().containsKey("center")) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.origin());
                    }
                    return MenuCustodyDecision.reject();
                })
                .render(state -> ReactiveMenuView.builder(alternateTitle.get() ? "Custody B" : "Custody A")
                        .place(31, MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                .name("Custody Target")
                                .build())
                        .build())
                .reduce((state, input) -> ReactiveMenuResult.unchanged())
                .build();
    }

    private static ReactiveMenu reactiveCustodyBaseMenu(AtomicBoolean alternateBase) {
        return new StandardMenuService().reactiveCanvas()
                .fillWithBlackPane(false)
                .stateFactory(() -> false)
                .custodyTarget("center", 31)
                .custodyPolicy((state, gesture, snapshot) -> {
                    if (gesture instanceof MenuCustodyGesture.ViewerClick viewer
                            && viewer.slot().item() != null
                            && !snapshot.targets().containsKey("center")) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.target("center"));
                    }
                    if (gesture instanceof MenuCustodyGesture.TargetClick
                            && snapshot.targets().containsKey("center")) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.origin());
                    }
                    return MenuCustodyDecision.reject();
                })
                .render(state -> ReactiveMenuView.builder("Custody Base")
                        .place(31, MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                .name(alternateBase.get() ? "New Target Base" : "Old Target Base")
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

    private static ReactiveMenu reactiveTitleChangingMenu() {
        return new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> false)
                .render(changed -> ReactiveMenuView.builder(changed ? "Reactive Rebuild B" : "Reactive Rebuild A")
                        .place(22, MenuButton.builder(MenuIcon.vanilla("lever"))
                                .name("Change Title")
                                .emit(ActionVerb.TOGGLE, "title", "change-title")
                                .build())
                        .build())
                .reduce((state, input) -> input instanceof ReactiveMenuInput.Click
                        ? ReactiveMenuResult.update(true)
                        : ReactiveMenuResult.unchanged())
                .build();
    }

    private static ReactiveMenu tickingNavigationMenu(Menu successor, AtomicInteger tickCalls) {
        return new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> false)
                .tickEvery(1L)
                .render(state -> ReactiveMenuView.builder("Ticking Navigation")
                        .place(22, MenuButton.builder(MenuIcon.vanilla("stone"))
                                .name("Open Successor")
                                .emit(ActionVerb.OPEN, "open", "open-successor")
                                .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.Tick) {
                        tickCalls.incrementAndGet();
                        return ReactiveMenuResult.unchanged();
                    }
                    if (input instanceof ReactiveMenuInput.Click) {
                        return ReactiveMenuResult.effect(new ReactiveMenuEffect.Open(successor));
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
    }

    private static ReactiveMenu throwingTickMenu() {
        return new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> false)
                .tickEvery(1L)
                .render(state -> ReactiveMenuView.builder("Throwing Tick").build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.Tick) {
                        throw new IllegalStateException("tick reducer failed");
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
    }

    private static ReactiveMenu reentrantCustodyMenu(Runnable openSuccessor) {
        return new StandardMenuService().reactiveCanvas()
                .fillWithBlackPane(false)
                .stateFactory(() -> false)
                .custodyTarget("center", 31)
                .custodyPolicy((state, gesture, snapshot) -> {
                    if (gesture instanceof MenuCustodyGesture.ViewerClick viewer
                            && viewer.slot().item() != null
                            && !snapshot.targets().containsKey("center")) {
                        openSuccessor.run();
                        return MenuCustodyDecision.move(MenuCustodyDestination.target("center"));
                    }
                    return MenuCustodyDecision.reject();
                })
                .render(state -> ReactiveMenuView.builder("Reentrant Custody")
                        .place(31, MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                .name("Custody Target")
                                .build())
                        .build())
                .reduce((state, input) -> ReactiveMenuResult.unchanged())
                .build();
    }

    private static ReactiveMenu throwingStateFactoryMenu() {
        return new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> {
                    throw new IllegalStateException("state factory failed");
                })
                .render(state -> ReactiveMenuView.builder("Never Rendered").build())
                .reduce((state, input) -> ReactiveMenuResult.unchanged())
                .build();
    }


    private static ReactiveMenu reentrantClickMenu(Runnable openSuccessor) {
        return new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> false)
                .render(replaced -> ReactiveMenuView.builder("Reentrant Click")
                        .place(22, MenuButton.builder(MenuIcon.vanilla("stone"))
                                .name(replaced ? "Stale Outcome" : "Replace Later")
                                .emit(ActionVerb.OPEN, "replace", "replace")
                                .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.Click) {
                        openSuccessor.run();
                        return ReactiveMenuResult.update(true);
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
    }

    private static ReactiveMenu throwingClickMenu() {
        return new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> false)
                .render(state -> ReactiveMenuView.builder("Throwing Click")
                        .place(22, MenuButton.builder(MenuIcon.vanilla("stone"))
                                .name("Throw")
                                .emit(ActionVerb.VIEW, "throw", "throw")
                                .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.Click) {
                        throw new IllegalStateException("click reducer failed");
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
    }

    private static ReactiveMenu throwingClickRendererMenu() {
        return new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> false)
                .render(throwNow -> {
                    if (throwNow) {
                        throw new IllegalStateException("reactive renderer failed");
                    }
                    return ReactiveMenuView.builder("Throwing Renderer")
                            .place(22, MenuButton.builder(MenuIcon.vanilla("stone"))
                                    .name("Throw During Render")
                                    .emit(ActionVerb.VIEW, "throw", "throw")
                                    .build())
                            .build();
                })
                .reduce((state, input) -> input instanceof ReactiveMenuInput.Click
                        ? ReactiveMenuResult.update(true)
                        : ReactiveMenuResult.unchanged())
                .build();
    }


    private static ReactiveMenu throwingOpenedMenu() {
        return new StandardMenuService().reactiveCanvas()
                .stateFactory(() -> false)
                .render(state -> ReactiveMenuView.builder("Throwing Opened").build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.Opened) {
                        throw new IllegalStateException("opened reducer failed");
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
    }

    private static ReactiveMenu reactiveDragInsertMenu(boolean locked) {
        return new StandardMenuService().reactiveCanvas()
                .fillWithBlackPane(false)
                .stateFactory(() -> locked)
                .custodyTarget("center", 31)
                .custodyPolicy((state, gesture, snapshot) -> {
                    if (state) {
                        return MenuCustodyDecision.reject();
                    }
                    if (gesture instanceof MenuCustodyGesture.ViewerClick viewer) {
                        if (viewer.slot().item() != null && snapshot.cursor().isEmpty()) {
                            return MenuCustodyDecision.move(viewer.shift()
                                    ? MenuCustodyDestination.target("center")
                                    : MenuCustodyDestination.cursor());
                        }
                        if (viewer.slot().item() == null && snapshot.cursor().isPresent()) {
                            return MenuCustodyDecision.move(MenuCustodyDestination.viewerSlot(viewer.slot()));
                        }
                    }
                    if (gesture instanceof MenuCustodyGesture.TargetDrag
                            && snapshot.cursor().isPresent()
                            && !snapshot.targets().containsKey("center")) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.target("center"));
                    }
                    if (gesture instanceof MenuCustodyGesture.TargetClick target
                            && snapshot.targets().containsKey("center")
                            && snapshot.cursor().isEmpty()) {
                        return MenuCustodyDecision.move(target.shift()
                                ? MenuCustodyDestination.origin()
                                : MenuCustodyDestination.cursor());
                    }
                    if (gesture instanceof MenuCustodyGesture.TargetClick
                            && snapshot.cursor().isPresent()
                            && !snapshot.targets().containsKey("center")) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.target("center"));
                    }
                    if (gesture instanceof MenuCustodyGesture.OutsideClick
                            && snapshot.cursor().isPresent()) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.origin());
                    }
                    return MenuCustodyDecision.reject();
                })
                .render(state -> ReactiveMenuView.builder("Reactive Drag")
                            .place(13, MenuDisplayItem.builder(MenuIcon.vanilla("hopper"))
                                    .name("Shift Or Drag")
                                    .description("Move an exact native stack through runtime-owned custody, then drag or click it into the center slot.")
                                    .build())
                            .place(31, MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                    .name("Custody Target")
                                    .build())
                            .build())
                .reduce((state, input) -> ReactiveMenuResult.unchanged())
                .build();
    }

    private static ReactiveMenu throwingDeathSettlementMenu() {
        return new StandardMenuService().reactiveCanvas()
                .fillWithBlackPane(false)
                .stateFactory(() -> false)
                .custodyTarget("center", 31)
                .custodyPolicy((state, gesture, snapshot) -> {
                    if (gesture instanceof MenuCustodyGesture.ViewerClick viewer
                            && viewer.shift()
                            && viewer.slot().item() != null
                            && !snapshot.targets().containsKey("center")) {
                        return MenuCustodyDecision.move(MenuCustodyDestination.target("center"));
                    }
                    return MenuCustodyDecision.reject();
                })
                .render(state -> ReactiveMenuView.builder("Throwing Death Settlement")
                        .place(31, MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                .name("Custody Target")
                                .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.CustodyCommitted committed
                            && committed.gesture() instanceof MenuCustodyGesture.Settle settle
                            && settle.reason() == MenuCustodyGesture.SettleReason.DEATH) {
                        throw new IllegalStateException("death settlement reducer failed");
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
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

    private record TimeoutPromptState(int cancellations, String submission) {
    }

    private record SettlementNavigationState(boolean settled, boolean actionUsed) {
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

    private record StoredState(MenuStack stored) {
    }

    private record ReactiveTabsState(String activeTabId, int navStart, int pageIndex) {
    }
}
