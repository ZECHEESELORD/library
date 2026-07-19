package sh.harold.library.menu.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import sh.harold.library.menu.MenuDefinition;
import sh.harold.library.menu.MenuContext;
import sh.harold.library.menu.MenuFrame;
import sh.harold.library.menu.MenuSlot;
import sh.harold.library.menu.core.MenuTrace;
import sh.harold.library.menu.core.MenuCustodyLedger;
import sh.harold.library.menu.core.MenuSessionState;
import sh.harold.library.menu.core.MenuTickHandle;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

final class PaperMenuSession implements InventoryHolder, MenuContext.SessionControls {

    private final PaperMenuRuntime runtime;
    private final UUID viewerId;
    private final MenuSessionState state;
    private final AtomicLong actionVersion = new AtomicLong();
    private volatile Inventory inventory;
    private volatile Component title;
    private volatile List<MenuSlot> renderedSlots = List.of();
    private volatile MenuCustodyLedger<ItemStack> custody;
    private volatile long custodyObservationId;
    private volatile boolean custodyTransitioning;
    private boolean inventoryReplacementQueued;
    private int nativeInventoryReplacementDepth;
    private int inventoryInteractionDepth;
    private volatile MenuTickHandle tickHandle = MenuTickHandle.noop();
    private volatile MenuTickHandle inputGateHandle = MenuTickHandle.noop();
    private volatile long tickIntervalTicks;
    private volatile Object acceptedInput;
    private volatile boolean settledCustodyViewDirty;
    private boolean schedulingRetired;
    private int userCallbackDepth;
    private long lifecycleGeneration;
    private final ArrayDeque<Runnable> deferredLifecycle = new ArrayDeque<>();

    PaperMenuSession(PaperMenuRuntime runtime, UUID viewerId, MenuSessionState state) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.viewerId = Objects.requireNonNull(viewerId, "viewerId");
        this.state = Objects.requireNonNull(state, "state");
        this.custody = new MenuCustodyLedger<>(state.custodyTargets());
    }

    UUID viewerId() {
        return viewerId;
    }

    MenuSessionState state() {
        return state;
    }

    Inventory inventory() {
        return inventory;
    }

    Component title() {
        return title;
    }

    long actionVersion() {
        return actionVersion.get();
    }

    synchronized long lifecycleGeneration() {
        return lifecycleGeneration;
    }

    synchronized boolean deferLifecycle(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (userCallbackDepth == 0) {
            return false;
        }
        deferredLifecycle.addLast(action);
        lifecycleGeneration++;
        return true;
    }

    <T> T invokeUserCallback(Supplier<T> callback) {
        Objects.requireNonNull(callback, "callback");
        synchronized (this) {
            userCallbackDepth++;
        }
        boolean completed = false;
        try {
            T result = callback.get();
            completed = true;
            return result;
        } finally {
            List<Runnable> pending = List.of();
            synchronized (this) {
                userCallbackDepth--;
                if (userCallbackDepth == 0 && !deferredLifecycle.isEmpty()) {
                    if (completed) {
                        pending = List.copyOf(deferredLifecycle);
                    }
                    deferredLifecycle.clear();
                }
            }
            if (!pending.isEmpty()) {
                runtime.scheduleDeferredLifecycle(this, pending);
            }
        }
    }

    MenuCustodyLedger<ItemStack> custody() {
        return custody;
    }

    boolean custodyEnabled() {
        return !custody.targetSlots().isEmpty();
    }

    long nextCustodyObservationId() {
        return ++custodyObservationId;
    }

    synchronized boolean custodyTransitioning() {
        return custodyTransitioning;
    }

    synchronized boolean beginCustodyTransition() {
        if (custodyTransitioning) {
            return false;
        }
        custodyTransitioning = true;
        return true;
    }

    synchronized void endCustodyTransition() {
        custodyTransitioning = false;
        inventoryReplacementQueued = false;
        nativeInventoryReplacementDepth = 0;
    }

    synchronized void queueInventoryReplacement() {
        if (!custodyTransitioning) {
            throw new IllegalStateException("Inventory replacement requires an active custody transition");
        }
        inventoryReplacementQueued = true;
    }

    synchronized boolean cancelQueuedInventoryReplacement() {
        if (!inventoryReplacementQueued) {
            return false;
        }
        inventoryReplacementQueued = false;
        custodyTransitioning = false;
        return true;
    }

    synchronized void beginNativeInventoryReplacement() {
        if (!custodyTransitioning) {
            throw new IllegalStateException("Inventory replacement requires an active custody transition");
        }
        inventoryReplacementQueued = false;
        nativeInventoryReplacementDepth++;
    }

    synchronized void endNativeInventoryReplacement() {
        if (nativeInventoryReplacementDepth <= 0) {
            throw new IllegalStateException("No native inventory replacement is active");
        }
        nativeInventoryReplacementDepth--;
    }

    synchronized boolean nativeInventoryReplacementInProgress() {
        return nativeInventoryReplacementDepth > 0;
    }

    void inInventoryInteraction(Runnable action) {
        inventoryInteractionDepth++;
        try {
            action.run();
        } finally {
            inventoryInteractionDepth--;
        }
    }

    boolean shouldDeferInventoryTransitions() {
        return inventoryInteractionDepth > 0;
    }

    List<MenuSlot> renderedSlots() {
        return renderedSlots;
    }

    void markSettledCustodyViewDirty() {
        settledCustodyViewDirty = true;
    }

    void restoreSettledCustodyView(Player player) {
        if (settledCustodyViewDirty) {
            refresh(player);
        }
    }

    PreparedInventory prepareTransition(MenuSessionState.PreparedTransition transition) {
        Objects.requireNonNull(transition);
        MenuFrame frame = runtime.invokeUserCallback(this, transition::currentFrame);
        return prepareInventory(
                transition,
                transition.menu(),
                frame,
                transition.custodyTargets());
    }

    PreparedInventory prepareCurrent() {
        MenuFrame frame = runtime.invokeUserCallback(this, state::currentFrame);
        return prepareInventory(null, state.menu(), frame, state.custodyTargets());
    }

    private PreparedInventory prepareInventory(
            MenuSessionState.PreparedTransition transition,
            MenuDefinition menu,
            MenuFrame frame,
            java.util.Map<String, Integer> custodyTargets
    ) {
        List<MenuSlot> slots = frame.slots();
        Inventory nextInventory = runtime.access().createInventory(
                this,
                menu.rows() * 9,
                frame.title());
        runtime.render(nextInventory, null, slots);
        return new PreparedInventory(
                transition,
                nextInventory,
                frame.title(),
                slots,
                new MenuCustodyLedger<>(custodyTargets));
    }

    void commitTransition(PreparedInventory prepared) {
        prepared.transition().commit();
        commitInventory(prepared);
    }

    void commitInitial(PreparedInventory prepared) {
        if (prepared.transition() != null) {
            throw new IllegalArgumentException();
        }
        commitInventory(prepared);
    }

    private void commitInventory(PreparedInventory prepared) {
        inventory = prepared.inventory();
        title = prepared.title();
        renderedSlots = prepared.slots();
        custody = prepared.custody();
        actionVersion.incrementAndGet();
        updateTicking();
        settledCustodyViewDirty = false;
    }

    synchronized InputGateResult acceptInput(Object fingerprint) {
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (schedulingRetired) {
            return InputGateResult.TICK_CAP;
        }
        Object previous = acceptedInput;
        if (previous != null) {
            return previous.equals(fingerprint) ? InputGateResult.DUPLICATE : InputGateResult.TICK_CAP;
        }
        acceptedInput = fingerprint;
        MenuTrace.time("runtime.inputGateArm", () -> {
            inputGateHandle.cancel();
            inputGateHandle = runtime.scheduleNextTick(this, this::rearmInputGate);
        });
        return InputGateResult.ACCEPTED;
    }

    void open(Player player) {
        refresh(player);
    }

    void refresh(Player player) {
        actionVersion.incrementAndGet();
        Inventory current = inventory;
        MenuCustodyLedger<ItemStack> expectedCustody = custody;
        long expectedActionVersion = actionVersion.get();
        long expectedLifecycleGeneration = lifecycleGeneration();
        MenuFrame frame = runtime.invokeUserCallback(this, state::currentFrame);
        if (current != null && !runtime.userCallbackFenceHolds(
                this,
                player,
                current,
                expectedCustody,
                expectedActionVersion,
                expectedLifecycleGeneration,
                false)) {
            return;
        }
        Component nextTitle = frame.title();
        int nextSize = state.menu().rows() * 9;
        List<MenuSlot> nextSlots = frame.slots();
        if (current != null && !runtime.validateCustodyForRender(this, player, current)) {
            return;
        }
        if (current == null || current.getSize() != nextSize || !Objects.equals(title, nextTitle)) {
            if (current == null) {
                PreparedInventory prepared = prepareCurrent();
                runtime.access().openInventory(player, prepared.inventory());
                commitInitial(prepared);
                return;
            }
            if (!runtime.prepareInventoryRebuild(this, player)) {
                return;
            }
            try {
                runtime.openPreparedRefresh(this, player, prepareCurrent());
            } catch (RuntimeException exception) {
                endCustodyTransition();
                throw exception;
            }
            return;
        }

        runtime.render(this, current, renderedSlots, nextSlots);
        title = nextTitle;
        renderedSlots = nextSlots;
        if (runtime.access().topInventory(player) != current) {
            runtime.openInventory(this, player, current);
        }
        updateTicking();
        settledCustodyViewDirty = false;
    }

    void detach(Player player) {
        retireScheduling();
        if (!custody.empty()) {
            throw new IllegalStateException();
        }
    }

    boolean matches(Player player, Inventory inventory) {
        return viewerId.equals(player.getUniqueId()) && this.inventory == inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
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

    private synchronized void updateTicking() {
        if (schedulingRetired) {
            stopTicking();
            return;
        }
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
                () -> runtime.scheduleTicks(this, nextInterval, () -> runtime.onTick(this)));
        tickIntervalTicks = nextInterval;
    }

    private synchronized void stopTicking() {
        MenuTrace.time("runtime.tickCancel", tickHandle::cancel);
        tickHandle = MenuTickHandle.noop();
        tickIntervalTicks = 0L;
    }

    private synchronized void rearmInputGate() {
        acceptedInput = null;
        inputGateHandle = MenuTickHandle.noop();
    }

    private synchronized void stopInputGate() {
        acceptedInput = null;
        MenuTrace.time("runtime.inputGateCancel", inputGateHandle::cancel);
        inputGateHandle = MenuTickHandle.noop();
    }

    synchronized void retireScheduling() {
        schedulingRetired = true;
        stopTicking();
        stopInputGate();
    }

    enum InputGateResult {
        ACCEPTED,
        DUPLICATE,
        TICK_CAP
    }

    record PreparedInventory(
            MenuSessionState.PreparedTransition transition,
            Inventory inventory,
            Component title,
            List<MenuSlot> slots,
            MenuCustodyLedger<ItemStack> custody
    ) {

        PreparedInventory {
            Objects.requireNonNull(inventory);
            Objects.requireNonNull(title);
            slots = List.copyOf(slots);
            Objects.requireNonNull(custody);
        }
    }
}
