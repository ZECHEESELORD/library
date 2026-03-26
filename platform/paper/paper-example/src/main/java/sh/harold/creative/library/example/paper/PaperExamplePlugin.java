package sh.harold.creative.library.example.paper;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.creative.library.menu.paper.PaperMenuPlatform;

public final class PaperExamplePlugin extends JavaPlugin implements Listener {

    private PaperMenuPlatform menus;
    private PaperMenuExampleMenus examples;

    @Override
    public void onEnable() {
        menus = new PaperMenuPlatform(this);
        examples = new PaperMenuExampleMenus(menus);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Paper menu example ready. Joining players open the house-style gallery.");
    }

    @Override
    public void onDisable() {
        if (menus != null) {
            menus.close();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(this, () -> menus.open(event.getPlayer(), examples.gallery()));
    }
}
