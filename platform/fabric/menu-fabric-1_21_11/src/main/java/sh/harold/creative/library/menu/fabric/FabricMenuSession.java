package sh.harold.creative.library.menu.fabric;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import sh.harold.creative.library.menu.MenuContext;
import sh.harold.creative.library.menu.MenuDefinition;
import sh.harold.creative.library.menu.MenuFrame;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.MenuStack;
import sh.harold.creative.library.menu.core.MenuTrace;
import sh.harold.creative.library.menu.core.MenuSessionState;
import sh.harold.creative.library.menu.core.MenuTickHandle;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

final class FabricMenuSession implements MenuContext.SessionControls {

    private final FabricMenuRuntime runtime;
    private final ServerPlayer viewer;
    private final MenuSessionState state;
    private final AtomicLong actionVersion = new AtomicLong();
    private volatile FabricMenuContainer container;
    private volatile net.kyori.adventure.text.Component title;
    private volatile List<MenuSlot> renderedSlots = List.of();
    private volatile MenuStack renderedCursor;
    private volatile MenuTickHandle tickHandle = MenuTickHandle.noop();
    private volatile MenuTickHandle inputGateHandle = MenuTickHandle.noop();
    private volatile long tickIntervalTicks;
    private volatile Object acceptedInput;

    FabricMenuSession(FabricMenuRuntime runtime, ServerPlayer viewer, MenuSessionState state) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.state = Objects.requireNonNull(state, "state");
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
        actionVersion.incrementAndGet();
        MenuFrame frame = state.currentFrame();
        FabricMenuContainer current = container;
        net.kyori.adventure.text.Component nextTitle = frame.title();
        int nextRows = state.menu().rows();
        List<MenuSlot> nextSlots = frame.slots();
        MenuStack nextCursor = state.cursor();

        if (current == null || current.closed() || current.rows() != nextRows || !Objects.equals(title, nextTitle)) {
            SimpleContainer nextInventory = new SimpleContainer(nextRows * 9);
            runtime.render(nextInventory, null, nextSlots, viewer.level().registryAccess());
            title = nextTitle;
            renderedSlots = nextSlots;
            FabricMenuContainer nextContainer = runtime.openMenu(this, viewer, nextRows, nextTitle, nextInventory);
            if (nextContainer != null) {
                runtime.syncCursor(nextContainer, renderedCursor, nextCursor, viewer.level().registryAccess());
            }
            renderedCursor = nextCursor;
            updateTicking();
            return;
        }

        runtime.render(current.topContainer(), renderedSlots, nextSlots, viewer.level().registryAccess());
        title = nextTitle;
        renderedSlots = nextSlots;
        runtime.syncCursor(current, renderedCursor, nextCursor, viewer.level().registryAccess());
        renderedCursor = nextCursor;
        current.broadcastFullState();
        updateTicking();
    }

    void detach() {
        state.closed();
        stopTicking();
        stopInputGate();
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
}
