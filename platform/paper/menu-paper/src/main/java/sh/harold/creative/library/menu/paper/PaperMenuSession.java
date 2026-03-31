package sh.harold.creative.library.menu.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import sh.harold.creative.library.menu.MenuDefinition;
import sh.harold.creative.library.menu.MenuContext;
import sh.harold.creative.library.menu.MenuFrame;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.MenuStack;
import sh.harold.creative.library.menu.core.MenuTrace;
import sh.harold.creative.library.menu.core.MenuSessionState;
import sh.harold.creative.library.menu.core.MenuTickHandle;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

final class PaperMenuSession implements InventoryHolder, MenuContext.SessionControls {

    private final PaperMenuRuntime runtime;
    private final UUID viewerId;
    private final MenuSessionState state;
    private final AtomicLong actionVersion = new AtomicLong();
    private volatile Inventory inventory;
    private volatile Component title;
    private volatile List<MenuSlot> renderedSlots = List.of();
    private volatile MenuStack renderedCursor;
    private volatile MenuTickHandle tickHandle = MenuTickHandle.noop();
    private volatile long tickIntervalTicks;

    PaperMenuSession(PaperMenuRuntime runtime, UUID viewerId, MenuSessionState state) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.viewerId = Objects.requireNonNull(viewerId, "viewerId");
        this.state = Objects.requireNonNull(state, "state");
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

    long actionVersion() {
        return actionVersion.get();
    }

    void open(Player player) {
        refresh(player);
    }

    void refresh(Player player) {
        actionVersion.incrementAndGet();
        MenuFrame frame = state.currentFrame();
        Inventory current = inventory;
        Component nextTitle = frame.title();
        int nextSize = state.menu().rows() * 9;
        List<MenuSlot> nextSlots = frame.slots();
        MenuStack nextCursor = state.cursor();

        if (current == null || current.getSize() != nextSize || !Objects.equals(title, nextTitle)) {
            Inventory nextInventory = MenuTrace.time("runtime.inventoryCreate",
                    () -> runtime.access().createInventory(this, nextSize, nextTitle));
            runtime.render(nextInventory, null, nextSlots);
            inventory = nextInventory;
            title = nextTitle;
            renderedSlots = nextSlots;
            runtime.syncCursor(player, renderedCursor, nextCursor);
            renderedCursor = nextCursor;
            runtime.openInventory(this, player, nextInventory);
            updateTicking();
            return;
        }

        runtime.render(current, renderedSlots, nextSlots);
        title = nextTitle;
        renderedSlots = nextSlots;
        runtime.syncCursor(player, renderedCursor, nextCursor);
        renderedCursor = nextCursor;
        updateTicking();
    }

    void detach() {
        state.closed();
        stopTicking();
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
}
