package sh.harold.creative.library.example.paper;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.creative.library.menu.paper.PaperMenuPlatform;
import sh.harold.creative.library.sound.SoundCueKeys;
import sh.harold.creative.library.sound.paper.PaperSoundCuePlatform;

public final class PaperExamplePlugin extends JavaPlugin implements Listener {

    private PaperMenuPlatform menus;
    private PaperMenuExampleMenus examples;
    private PaperSoundCuePlatform sounds;

    @Override
    public void onEnable() {
        sounds = new PaperSoundCuePlatform(this);
        menus = new PaperMenuPlatform(this, new sh.harold.creative.library.menu.core.StandardMenuService(), sounds);
        examples = new PaperMenuExampleMenus(menus);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Paper menu example ready. Joining players open the house-style gallery.");
    }

    @Override
    public void onDisable() {
        if (menus != null) {
            menus.close();
        }
        if (sounds != null) {
            sounds.close();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sounds.play(event.getPlayer(), SoundCueKeys.REWARD_DISCOVERY);
        Bukkit.getScheduler().runTask(this, () -> menus.open(event.getPlayer(), examples.gallery()));
    }
}
