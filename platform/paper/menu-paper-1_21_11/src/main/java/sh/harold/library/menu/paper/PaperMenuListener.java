package sh.harold.library.menu.paper;

import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

final class PaperMenuListener implements Listener {

    private final PaperMenuRuntime runtime;

    PaperMenuListener(PaperMenuRuntime runtime) {
        this.runtime = runtime;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        runtime.onInventoryClick(event);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        runtime.onInventoryClose(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        runtime.onInventoryDrag(event);
    }

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        runtime.onAsyncChat(event);
    }

    @EventHandler
    public void onUncheckedSignChange(UncheckedSignChangeEvent event) {
        runtime.onUncheckedSignChange(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        runtime.onPlayerDisconnect(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKick(PlayerKickEvent event) {
        if (!event.isCancelled()) {
            runtime.onPlayerDisconnect(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        runtime.onPlayerDeath(event.getEntity(), event.getKeepInventory(), event.getDrops());
    }
}
