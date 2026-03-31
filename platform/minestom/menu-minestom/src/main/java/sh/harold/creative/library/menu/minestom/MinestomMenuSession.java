package sh.harold.creative.library.menu.minestom;

import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
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

final class MinestomMenuSession implements MenuContext.SessionControls {

    private final MinestomMenuRuntime runtime;
    private final Player viewer;
    private final MenuSessionState state;
    private final AtomicLong actionVersion = new AtomicLong();
    private volatile Inventory inventory;
    private volatile Component title;
    private volatile List<MenuSlot> renderedSlots = List.of();
    private volatile MenuStack renderedCursor;
    private volatile MenuTickHandle tickHandle = MenuTickHandle.noop();
    private volatile long tickIntervalTicks;

    MinestomMenuSession(MinestomMenuRuntime runtime, Player viewer, MenuSessionState state) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.state = Objects.requireNonNull(state, "state");
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

    long actionVersion() {
        return actionVersion.get();
    }

    void open() {
        renderCurrentView();
    }

    void renderCurrentView() {
        actionVersion.incrementAndGet();
        MenuFrame frame = state.currentFrame();
        Inventory current = inventory;
        Component nextTitle = frame.title();
        InventoryType nextType = inventoryType(state.menu().rows());
        List<MenuSlot> nextSlots = frame.slots();
        MenuStack nextCursor = state.cursor();

        if (current == null || current.getInventoryType() != nextType || !Objects.equals(title, nextTitle)) {
            Inventory nextInventory = MenuTrace.time("runtime.inventoryCreate", () -> new Inventory(nextType, nextTitle));
            runtime.render(nextInventory, null, nextSlots);
            inventory = nextInventory;
            title = nextTitle;
            renderedSlots = nextSlots;
            runtime.syncCursor(viewer, renderedCursor, nextCursor);
            renderedCursor = nextCursor;
            MenuTrace.time("runtime.inventoryOpen", () -> viewer.openInventory(nextInventory));
            updateTicking();
            return;
        }

        runtime.render(current, renderedSlots, nextSlots);
        title = nextTitle;
        renderedSlots = nextSlots;
        runtime.syncCursor(viewer, renderedCursor, nextCursor);
        renderedCursor = nextCursor;
        updateTicking();
    }

    void detach() {
        state.closed();
        stopTicking();
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
}
