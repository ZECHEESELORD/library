package sh.harold.creative.library.menu.minestom;

import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuContext;
import sh.harold.creative.library.menu.core.MenuSessionState;

import java.util.Objects;

final class MinestomMenuSession implements MenuContext.SessionControls {

    private final MinestomMenuRuntime runtime;
    private final Player viewer;
    private final MenuSessionState state;
    private volatile Inventory inventory;
    private volatile Component title;

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

    void open() {
        renderCurrentFrame();
    }

    void renderCurrentFrame() {
        Inventory current = inventory;
        Component nextTitle = state.currentFrame().title();
        InventoryType nextType = inventoryType(state.menu().rows());

        if (current == null || current.getInventoryType() != nextType || !Objects.equals(title, nextTitle)) {
            Inventory nextInventory = new Inventory(nextType, nextTitle);
            inventory = nextInventory;
            title = nextTitle;
            runtime.render(this, nextInventory);
            viewer.openInventory(nextInventory);
            return;
        }

        runtime.render(this, current);
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
