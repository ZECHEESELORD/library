package sh.harold.creative.library.menu.paper;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

final class PaperMenuListener implements Listener {

    private final PaperMenuRuntime runtime;

    PaperMenuListener(PaperMenuRuntime runtime) {
        this.runtime = runtime;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        runtime.onInventoryClick(event);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        runtime.onInventoryClose(event);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        runtime.onInventoryDrag(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        runtime.onPlayerDisconnect(event.getPlayer());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        runtime.onPlayerDisconnect(event.getPlayer());
    }
}
