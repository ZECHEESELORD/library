package sh.harold.library.menu.minestom;

import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import sh.harold.library.menu.MenuContext;
import sh.harold.library.menu.MenuDefinition;
import sh.harold.library.menu.MenuFrame;
import sh.harold.library.menu.MenuSlot;
import sh.harold.library.menu.core.MenuTrace;
import sh.harold.library.menu.core.MenuCustodyLedger;
import sh.harold.library.menu.core.MenuSessionState;
import sh.harold.library.menu.core.MenuTickHandle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

final class MinestomMenuSession implements MenuContext.SessionControls {

    private final MinestomMenuRuntime runtime;
    private final Player viewer;
    private final MenuSessionState state;
    private final AtomicLong actionVersion = new AtomicLong();
    private volatile Inventory inventory;
    private volatile Inventory attemptedInventory;
    private volatile Component title;
    private volatile List<MenuSlot> renderedSlots = List.of();
    private volatile MenuTickHandle tickHandle = MenuTickHandle.noop();
    private volatile MenuTickHandle inputGuardRearmHandle = MenuTickHandle.noop();
    private volatile boolean inputGuardLocked;
    private volatile long tickIntervalTicks;
    private volatile long inventoryGeneration;
    private volatile ManagedInventoryTransition managedInventoryTransition;
    private volatile MenuCustodyLedger<ItemStack> custodyLedger;
    private volatile boolean quarantined;
    private volatile boolean admitted;
    private volatile boolean settledCustodyViewDirty;
    private final ArrayDeque<Runnable> deferredLifecycle = new ArrayDeque<>();
    private volatile long lifecycleGeneration;
    private int userCallbackDepth;
    private long nextViewerObservationId;

    MinestomMenuSession(MinestomMenuRuntime runtime, Player viewer, MenuSessionState state) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.state = Objects.requireNonNull(state, "state");
        this.custodyLedger = new MenuCustodyLedger<>(state.custodyTargets());
    }

    MinestomMenuSession(MinestomMenuRuntime runtime, Player viewer, MenuDefinition menu) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.state = userCallback(() -> new MenuSessionState(Objects.requireNonNull(menu, "menu")));
        this.custodyLedger = new MenuCustodyLedger<>(state.custodyTargets());
    }

    Player viewer() {
        return viewer;
    }

    MenuSessionState state() {
        return state;
    }

    Inventory inventory() {
        return inventory;
    }

    Inventory attemptedInventory() {
        return attemptedInventory;
    }

    Component renderedTitle() {
        return Objects.requireNonNull(title, "rendered session title");
    }

    long actionVersion() {
        return actionVersion.get();
    }

    long inventoryGeneration() {
        return inventoryGeneration;
    }

    long lifecycleGeneration() {
        return lifecycleGeneration;
    }

    MenuCustodyLedger<ItemStack> custodyLedger() {
        return custodyLedger;
    }

    boolean custodyEnabled() {
        return !custodyLedger.targetSlots().isEmpty();
    }

    boolean quarantined() {
        return quarantined;
    }

    boolean admitted() {
        return admitted;
    }

    void admit() {
        admitted = true;
    }

    void quarantine() {
        quarantined = true;
        stopTicking();
        stopInputGuard();
    }

    long nextViewerObservationId() {
        return ++nextViewerObservationId;
    }

    <T> T userCallback(Supplier<T> callback) {
        Objects.requireNonNull(callback, "callback");
        int deferredStart;
        synchronized (this) {
            deferredStart = deferredLifecycle.size();
            userCallbackDepth++;
        }
        boolean completed = false;
        runtime.enterUserCallback(this);
        try {
            T result = callback.get();
            completed = true;
            return result;
        } finally {
            runtime.exitUserCallback(this);
            List<Runnable> ready = List.of();
            synchronized (this) {
                userCallbackDepth--;
                if (!completed) {
                    while (deferredLifecycle.size() > deferredStart) {
                        deferredLifecycle.removeLast();
                    }
                }
                if (userCallbackDepth == 0 && !deferredLifecycle.isEmpty()) {
                    ready = new ArrayList<>(deferredLifecycle);
                    deferredLifecycle.clear();
                }
            }
            if (!ready.isEmpty()) {
                runtime.scheduleDeferredLifecycle(ready);
            }
        }
    }

    synchronized boolean deferLifecycle(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (userCallbackDepth == 0) {
            return false;
        }
        lifecycleGeneration++;
        deferredLifecycle.addLast(action);
        return true;
    }

    boolean open() {
        if (!admitted && lifecycleGeneration != 0L) {
            return false;
        }
        return renderCurrentView(false, false);
    }

    void renderCurrentView() {
        renderCurrentView(false, false);
    }

    void renderAfterCustodyCommit() {
        renderCurrentView(false, true);
    }

    void markSettledCustodyViewDirty() {
        settledCustodyViewDirty = true;
    }

    boolean restoreSettledCustodyView() {
        return !settledCustodyViewDirty || renderCurrentView(false, true);
    }

    boolean reopenCurrentView() {
        return renderCurrentView(true, false);
    }

    boolean applyTransition(MenuSessionState.PreparedTransition transition) {
        Objects.requireNonNull(transition, "transition");
        actionVersion.incrementAndGet();
        MinestomMenuRuntime.CallbackFence fence = runtime.callbackFence(this);
        MenuFrame frame = userCallback(transition::currentFrame);
        if (!runtime.callbackFrameCurrent(this, fence)) {
            return false;
        }
        Inventory previousInventory = inventory;
        Component nextTitle = frame.title();
        InventoryType nextType = inventoryType(transition.menu().rows());
        List<MenuSlot> nextSlots = frame.slots();
        MenuCustodyLedger<ItemStack> nextCustodyLedger = new MenuCustodyLedger<>(transition.custodyTargets());
        Inventory nextInventory = MenuTrace.time("runtime.inventoryCreate", () -> new Inventory(nextType, nextTitle));
        runtime.render(nextInventory, null, nextSlots, nextCustodyLedger);
        runtime.renderCustodyTargets(nextInventory, nextSlots, nextCustodyLedger);
        managedInventoryTransition = previousInventory == null
                ? null
                : new ManagedInventoryTransition(previousInventory, inventoryGeneration);
        boolean opened;
        try {
            opened = MenuTrace.time("runtime.inventoryOpen", () -> viewer.openInventory(nextInventory));
        } catch (RuntimeException exception) {
            runtime.retireInventory(viewer, nextInventory);
            boolean restored = restorePreviousInventory(previousInventory);
            managedInventoryTransition = null;
            if (!restored) {
                runtime.abandonFailedTransition(this, nextInventory);
            }
            return false;
        }
        if (!opened) {
            managedInventoryTransition = null;
            return false;
        }
        try {
            transition.commit();
        } catch (RuntimeException exception) {
            runtime.retireInventory(viewer, nextInventory);
            boolean restored = restorePreviousInventory(previousInventory);
            managedInventoryTransition = null;
            if (!restored) {
                runtime.abandonFailedTransition(this, nextInventory);
            }
            return false;
        }
        custodyLedger = nextCustodyLedger;
        inventory = nextInventory;
        inventoryGeneration++;
        title = nextTitle;
        renderedSlots = nextSlots;
        settledCustodyViewDirty = false;
        managedInventoryTransition = null;
        runtime.retireInventory(viewer, previousInventory);
        updateTicking();
        return true;
    }

    boolean ignoresManagedClose(Inventory closedInventory) {
        ManagedInventoryTransition transition = managedInventoryTransition;
        return transition != null
                && transition.inventory() == closedInventory
                && transition.generation() == inventoryGeneration;
    }

    void expectManagedClose() {
        Inventory current = inventory;
        managedInventoryTransition = current == null
                ? null
                : new ManagedInventoryTransition(current, inventoryGeneration);
    }

    void clearManagedCloseExpectation() {
        managedInventoryTransition = null;
    }

    private boolean renderCurrentView(boolean reopen, boolean trustedCustodyMutation) {
        actionVersion.incrementAndGet();
        boolean custodySettlementRender = settledCustodyViewDirty;
        MinestomMenuRuntime.CallbackFence fence = runtime.callbackFence(this);
        MenuFrame frame = userCallback(state::currentFrame);
        if (!runtime.callbackFrameCurrent(this, fence)) {
            return false;
        }
        Inventory current = inventory;
        Component nextTitle = frame.title();
        InventoryType nextType = inventoryType(state.menu().rows());
        List<MenuSlot> nextSlots = frame.slots();

        if (current == null || current.getInventoryType() != nextType) {
            Inventory nextInventory = MenuTrace.time("runtime.inventoryCreate", () -> new Inventory(nextType, nextTitle));
            attemptedInventory = nextInventory;
            runtime.render(nextInventory, null, nextSlots, custodyLedger);
            runtime.renderCustodyTargets(nextInventory, nextSlots, custodyLedger);
            managedInventoryTransition = current == null
                    ? null
                    : new ManagedInventoryTransition(current, inventoryGeneration);
            boolean opened;
            try {
                opened = MenuTrace.time("runtime.inventoryOpen", () -> viewer.openInventory(nextInventory));
            } finally {
                managedInventoryTransition = null;
            }
            if (!opened) {
                attemptedInventory = null;
                return false;
            }
            inventory = nextInventory;
            inventoryGeneration++;
            title = nextTitle;
            renderedSlots = nextSlots;
            settledCustodyViewDirty = false;
            updateTicking();
            attemptedInventory = null;
            return true;
        }

        if (!trustedCustodyMutation
                && !custodySettlementRender
                && !runtime.validateCustodyView(this, current, renderedSlots, custodyLedger)) {
            return false;
        }
        if (!Objects.equals(title, nextTitle)) {
            MenuTrace.time("runtime.inventoryTitlePatch", () -> current.setTitle(nextTitle));
        }
        runtime.render(current, renderedSlots, nextSlots, custodyLedger);
        runtime.renderCustodyTargets(current, nextSlots, custodyLedger);
        title = nextTitle;
        renderedSlots = nextSlots;
        if (reopen && viewer.getOpenInventory() != current) {
            if (viewer.getOpenInventory() != null
                    || !MenuTrace.time("runtime.inventoryOpen", () -> viewer.openInventory(current))) {
                return false;
            }
            inventoryGeneration++;
        }
        settledCustodyViewDirty = false;
        updateTicking();
        return true;
    }

    void detach() {
        stopTicking();
        stopInputGuard();
    }

    @Override
    public void refresh() {
        runtime.refresh(this);
    }

    @Override
    public void open(MenuDefinition menu) {
        runtime.replace(this, menu);
    }

    @Override
    public void replace(MenuDefinition menu) {
        runtime.replaceCurrent(this, menu);
    }

    @Override
    public void back() {
        runtime.back(this);
    }

    @Override
    public void close() {
        runtime.close(this);
    }

    boolean tryAcquireInputGuard() {
        if (inputGuardLocked) {
            return false;
        }
        inputGuardLocked = true;
        scheduleInputGuardRearm();
        return true;
    }

    void rearmInputGuard() {
        inputGuardRearmHandle = MenuTickHandle.noop();
        inputGuardLocked = false;
    }

    private void scheduleInputGuardRearm() {
        MenuTrace.time("runtime.inputGuardSchedule", () -> {
            inputGuardRearmHandle.cancel();
            inputGuardRearmHandle = runtime.scheduleNextTick(() -> runtime.rearmInputGuard(this));
        });
    }

    private void stopInputGuard() {
        MenuTrace.time("runtime.inputGuardCancel", inputGuardRearmHandle::cancel);
        inputGuardRearmHandle = MenuTickHandle.noop();
        inputGuardLocked = false;
    }

    private void updateTicking() {
        long nextInterval = state.tickIntervalTicks();
        MenuTrace.field("tickInterval", nextInterval);
        if (nextInterval <= 0L) {
            stopTicking();
            return;
        }
        if (tickIntervalTicks == nextInterval) {
            return;
        }
        stopTicking();
        tickHandle = MenuTrace.time("runtime.tickSchedule",
                () -> runtime.tickScheduler().schedule(nextInterval, () -> runtime.onTick(this)));
        tickIntervalTicks = nextInterval;
    }

    private void stopTicking() {
        MenuTrace.time("runtime.tickCancel", tickHandle::cancel);
        tickHandle = MenuTickHandle.noop();
        tickIntervalTicks = 0L;
    }

    private boolean restorePreviousInventory(Inventory previousInventory) {
        if (previousInventory == null) {
            return false;
        }
        if (viewer.getOpenInventory() == previousInventory) {
            return true;
        }
        try {
            return MenuTrace.time("runtime.inventoryRestore", () -> viewer.openInventory(previousInventory));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static InventoryType inventoryType(int rows) {
        return switch (rows) {
            case 1 -> InventoryType.CHEST_1_ROW;
            case 2 -> InventoryType.CHEST_2_ROW;
            case 3 -> InventoryType.CHEST_3_ROW;
            case 4 -> InventoryType.CHEST_4_ROW;
            case 5 -> InventoryType.CHEST_5_ROW;
            case 6 -> InventoryType.CHEST_6_ROW;
            default -> throw new IllegalArgumentException("Unsupported menu row count: " + rows);
        };
    }

    private record ManagedInventoryTransition(Inventory inventory, long generation) {
    }
}
