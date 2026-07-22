package sh.harold.library.menu.fabric;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import sh.harold.library.menu.MenuContext;
import sh.harold.library.menu.MenuDefinition;
import sh.harold.library.menu.MenuFrame;
import sh.harold.library.menu.MenuSlot;
import sh.harold.library.menu.core.MenuTrace;
import sh.harold.library.menu.core.MenuSessionState;
import sh.harold.library.menu.core.MenuTickHandle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class FabricMenuSession implements MenuContext.SessionControls {

    private final FabricMenuRuntime runtime;
    private final ServerPlayer viewer;
    private final MenuSessionState state;
    private final FabricMenuCallbackGate callbackGate;
    private final AtomicLong actionVersion = new AtomicLong();
    private volatile FabricMenuContainer container;
    private volatile net.kyori.adventure.text.Component title;
    private volatile List<MenuSlot> renderedSlots = List.of();
    private volatile FabricMenuCustody custody;
    private final Map<String, ItemStack> custodyBaseItems = new HashMap<>();
    private volatile long custodyObservationId;
    private final FabricMenuTickController tickController;
    private final SettledCustodyView settledCustodyView = new SettledCustodyView();
    private final DeathAttempt deathAttempt = new DeathAttempt();
    private volatile MenuTickHandle inputGateHandle = MenuTickHandle.noop();
    private volatile Object acceptedInput;

    FabricMenuSession(FabricMenuRuntime runtime, ServerPlayer viewer, MenuSessionState state) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.state = Objects.requireNonNull(state, "state");
        this.callbackGate = new FabricMenuCallbackGate(action -> runtime.scheduleNextTick(action));
        this.tickController = new FabricMenuTickController(runtime.tickScheduler(), () -> runtime.onTick(this));
        this.custody = new FabricMenuCustody(state.custodyTargets());
    }

    ServerPlayer viewer() {
        return viewer;
    }

    MenuSessionState state() {
        return state;
    }

    FabricMenuContainer container() {
        return container;
    }

    net.kyori.adventure.text.Component renderedTitle() {
        return title;
    }

    void attachContainer(FabricMenuContainer container) {
        this.container = Objects.requireNonNull(container, "container");
    }

    void clearContainer(FabricMenuContainer container) {
        if (this.container == container) {
            this.container = null;
        }
    }

    long actionVersion() {
        return actionVersion.get();
    }

    long callbackGeneration() {
        return callbackGate.generation();
    }

    boolean callbackGenerationUnchanged(long expectedGeneration) {
        return callbackGate.unchanged(expectedGeneration);
    }

    boolean deferLifecycle(Runnable lifecycle) {
        return callbackGate.defer(lifecycle);
    }

    <T> T invokeUserCallback(Supplier<T> callback) {
        return callbackGate.invoke(callback);
    }

    void invokeUserCallback(Runnable callback) {
        callbackGate.invoke(callback);
    }

    FabricMenuCustody custody() {
        return custody;
    }

    boolean custodyEnabled() {
        return custody.enabled();
    }

    long nextCustodyObservationId() {
        return ++custodyObservationId;
    }

    List<MenuSlot> renderedSlots() {
        return renderedSlots;
    }

    ItemStack custodyBaseItem(String key) {
        return custodyBaseItems.get(key);
    }

    void cacheCustodyBaseItem(String key, ItemStack item) {
        custodyBaseItems.put(key, item == null || item.isEmpty() ? ItemStack.EMPTY : item.copy());
    }

    void resetCustody() {
        if (!custody.empty()) {
            throw new IllegalStateException("Cannot replace a non-empty custody ledger");
        }
        custody = new FabricMenuCustody(state.custodyTargets());
        custodyBaseItems.clear();
    }

    void markSettledCustodyViewDirty() {
        settledCustodyView.markDirty();
    }

    boolean restoreSettledCustodyView() {
        return settledCustodyView.restore(() -> renderCurrentView(true));
    }

    long beginDeathAttempt() {
        return deathAttempt.begin();
    }

    boolean consumeDeathAttempt(long token) {
        return deathAttempt.consume(token);
    }

    boolean matches(ServerPlayer player, FabricMenuContainer container) {
        return viewer == player && this.container == container;
    }

    InputGateResult acceptInput(Object fingerprint) {
        Objects.requireNonNull(fingerprint, "fingerprint");
        Object previous = acceptedInput;
        if (previous != null) {
            return previous.equals(fingerprint) ? InputGateResult.DUPLICATE : InputGateResult.TICK_CAP;
        }
        acceptedInput = fingerprint;
        MenuTrace.time("runtime.inputGateArm", () -> {
            inputGateHandle.cancel();
            inputGateHandle = runtime.scheduleNextTick(this::rearmInputGate);
        });
        return InputGateResult.ACCEPTED;
    }

    void renderCurrentView() {
        renderCurrentView(false);
    }

    boolean renderCurrentView(boolean trustedCustodyMutation) {
        if (!runtime.active(this)) {
            return false;
        }
        actionVersion.incrementAndGet();
        long callbackGeneration = callbackGeneration();
        MenuFrame frame = invokeUserCallback(state::currentFrame);
        if (!runtime.activeAfterCallback(this, callbackGeneration)) {
            return false;
        }
        FabricMenuContainer current = container;
        net.kyori.adventure.text.Component nextTitle = frame.title();
        int nextRows = state.menu().rows();
        List<MenuSlot> nextSlots = frame.slots();

        if (current != null && !current.closed() && !trustedCustodyMutation
                && !runtime.validateCustodyView(this, current)) {
            return false;
        }

        if (current != null
                && !current.closed()
                && containerMetadataChanged(current.rows(), title, nextRows, nextTitle)
                && !custody.empty()) {
            if (!runtime.settleForRebuild(this)) {
                return false;
            }
            callbackGeneration = callbackGeneration();
            frame = invokeUserCallback(state::currentFrame);
            if (!runtime.activeAfterCallback(this, callbackGeneration)) {
                return false;
            }
            nextTitle = frame.title();
            nextRows = state.menu().rows();
            nextSlots = frame.slots();
        }

        if (current == null || current.closed() || containerMetadataChanged(current.rows(), title, nextRows, nextTitle)) {
            SimpleContainer nextInventory = new SimpleContainer(nextRows * 9);
            runtime.render(this, nextInventory, null, nextSlots, viewer.level().registryAccess());
            FabricMenuContainer nextContainer = runtime.openMenu(this, viewer, nextRows, nextTitle, nextInventory);
            if (nextContainer == null) {
                if (current == null || current.closed()) {
                    throw new IllegalStateException("Fabric menu container did not open");
                }
                return false;
            }
            if (!runtime.active(this)) {
                return false;
            }
            title = nextTitle;
            renderedSlots = nextSlots;
            custodyBaseItems.clear();
            boolean changed = runtime.renderCustody(this, nextInventory, null, nextSlots,
                    viewer.level().registryAccess());
            changed |= runtime.syncCustodyCursor(this, nextContainer);
            if (!runtime.active(this)) {
                return false;
            }
            if (changed) {
                nextContainer.broadcastChanges();
            }
            if (!runtime.active(this)) {
                return false;
            }
            settledCustodyView.rendered();
            updateTicking();
            return true;
        }

        boolean changed = runtime.render(this, current.topContainer(), renderedSlots, nextSlots,
                viewer.level().registryAccess());
        title = nextTitle;
        List<MenuSlot> previousSlots = renderedSlots;
        renderedSlots = nextSlots;
        changed |= runtime.renderCustody(this, current.topContainer(), previousSlots, nextSlots,
                viewer.level().registryAccess());
        changed |= runtime.syncCustodyCursor(this, current);
        if (!runtime.active(this)) {
            return false;
        }
        if (changed || trustedCustodyMutation) {
            current.broadcastChanges();
        }
        settledCustodyView.rendered();
        updateTicking();
        return true;
    }

    boolean applyTransition(MenuSessionState.PreparedTransition transition) {
        Objects.requireNonNull(transition, "transition");
        if (!runtime.active(this)) {
            return false;
        }
        long callbackGeneration = callbackGeneration();
        MenuFrame frame = invokeUserCallback(transition::currentFrame);
        if (!runtime.activeAfterCallback(this, callbackGeneration)) {
            return false;
        }
        int rows = transition.menu().rows();
        SimpleContainer nextInventory = new SimpleContainer(rows * 9);
        runtime.render(this, nextInventory, null, frame.slots(), viewer.level().registryAccess());
        FabricMenuContainer nextContainer = runtime.openMenu(this, viewer, rows, frame.title(), nextInventory);
        if (nextContainer == null) {
            return false;
        }
        try {
            transition.commit();
        } catch (RuntimeException exception) {
            nextContainer.markTransitionClose();
            clearContainer(nextContainer);
            if (viewer.containerMenu == nextContainer) {
                viewer.closeContainer();
            }
            throw exception;
        }
        resetCustody();
        title = frame.title();
        renderedSlots = frame.slots();
        custodyBaseItems.clear();
        boolean changed = runtime.renderCustody(this, nextInventory, null, frame.slots(),
                viewer.level().registryAccess());
        changed |= runtime.syncCustodyCursor(this, nextContainer);
        if (!runtime.active(this)) {
            return false;
        }
        if (changed) {
            nextContainer.broadcastChanges();
        }
        settledCustodyView.rendered();
        updateTicking();
        return true;
    }

    void detach() {
        callbackGate.retire();
        deathAttempt.retire();
        stopTicking();
        stopInputGate();
    }

    void suspendTickingForPrompt() {
        stopTicking();
    }

    static boolean containerMetadataChanged(int currentRows, net.kyori.adventure.text.Component currentTitle,
                                            int nextRows, net.kyori.adventure.text.Component nextTitle) {
        return currentRows != nextRows || !Objects.equals(currentTitle, nextTitle);
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

    private void updateTicking() {
        long nextInterval = state.tickIntervalTicks();
        MenuTrace.field("tickInterval", nextInterval);
        MenuTrace.time("runtime.tickSchedule", () -> tickController.update(nextInterval));
    }

    private void stopTicking() {
        MenuTrace.time("runtime.tickCancel", tickController::stop);
    }

    private void rearmInputGate() {
        acceptedInput = null;
        inputGateHandle = MenuTickHandle.noop();
    }

    private void stopInputGate() {
        acceptedInput = null;
        MenuTrace.time("runtime.inputGateCancel", inputGateHandle::cancel);
        inputGateHandle = MenuTickHandle.noop();
    }

    enum InputGateResult {
        ACCEPTED,
        DUPLICATE,
        TICK_CAP
    }

    static final class SettledCustodyView {

        private volatile boolean dirty;

        void markDirty() {
            dirty = true;
        }

        boolean restore(BooleanSupplier render) {
            Objects.requireNonNull(render, "render");
            if (!dirty) {
                return true;
            }
            if (!render.getAsBoolean()) {
                return false;
            }
            dirty = false;
            return true;
        }

        void rendered() {
            dirty = false;
        }

        boolean dirty() {
            return dirty;
        }
    }

    static final class DeathAttempt {

        private long nextToken;
        private long pendingToken;

        synchronized long begin() {
            if (pendingToken != 0L) {
                return 0L;
            }
            pendingToken = ++nextToken;
            return pendingToken;
        }

        synchronized boolean consume(long token) {
            if (token == 0L || pendingToken != token) {
                return false;
            }
            pendingToken = 0L;
            return true;
        }

        synchronized void retire() {
            pendingToken = 0L;
            nextToken++;
        }
    }
}
