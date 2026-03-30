package sh.harold.creative.library.menu.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuContext;
import sh.harold.creative.library.menu.core.MenuSessionState;

import java.util.Objects;
import java.util.UUID;

final class PaperMenuSession implements InventoryHolder, MenuContext.SessionControls {

    private final PaperMenuRuntime runtime;
    private final UUID viewerId;
    private final MenuSessionState state;
    private volatile Inventory inventory;
    private volatile Component title;

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

    void open(Player player) {
        refresh(player);
    }

    void refresh(Player player) {
        Inventory current = inventory;
        Component currentTitle = title;
        Component nextTitle = state.currentFrame().title();
        int nextSize = state.menu().rows() * 9;

        if (current == null || current.getSize() != nextSize || !Objects.equals(currentTitle, nextTitle)) {
            Inventory nextInventory = runtime.access().createInventory(this, nextSize, nextTitle);
            inventory = nextInventory;
            title = nextTitle;
            runtime.render(this, nextInventory);
            runtime.access().openInventory(player, nextInventory);
            return;
        }

        runtime.render(this, current);
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
    public void open(Menu menu) {
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
}
