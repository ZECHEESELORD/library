package sh.harold.creative.library.example.paper;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.creative.library.menu.paper.PaperMenuPlatform;
import sh.harold.creative.library.sound.SoundCueKeys;
import sh.harold.creative.library.sound.paper.PaperSoundCuePlatform;
import sh.harold.creative.library.overlay.paper.PaperScreenOverlayPlatform;
import sh.harold.creative.library.message.Message;

public final class PaperExamplePlugin extends JavaPlugin implements Listener {

    private PaperMenuPlatform menus;
    private PaperMenuExampleMenus examples;
    private PaperSoundCuePlatform sounds;
    private PaperScreenOverlayPlatform overlays;
    private PaperScreenOverlayExamples overlayExamples;
    private PaperExampleMessages feedback;

    @Override
    public void onEnable() {
        feedback = new PaperExampleMessages();
        sounds = new PaperSoundCuePlatform(this);
        overlays = new PaperScreenOverlayPlatform(this);
        menus = new PaperMenuPlatform(this, new sh.harold.creative.library.menu.core.StandardMenuService(), sounds);
        examples = new PaperMenuExampleMenus(menus);
        overlayExamples = new PaperScreenOverlayExamples(this, overlays, feedback);
        registerCommands();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Paper example ready. Joining players open the house-style gallery, and /testoverlays previews the screen shell.");
    }

    @Override
    public void onDisable() {
        if (overlays != null) {
            overlays.close();
        }
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
        feedback.info(
                event.getPlayer(),
                "Use {command} to preview the screen overlay subsystem.",
                Message.slot("command", feedback.command("/testoverlays"))
        );
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(
                        "testoverlays",
                        "Preview the screen overlay demo sequence.",
                        java.util.List.of("testoverlay"),
                        new BasicCommand() {
                            @Override
                            public void execute(CommandSourceStack stack, String[] args) {
                                CommandSender sender = stack.getSender();
                                if (!(sender instanceof Player player)) {
                                    feedback.error(sender, "This command can only be used by a player.");
                                    return;
                                }

                                if (args.length == 0 || "demo".equalsIgnoreCase(args[0])) {
                                    overlayExamples.runDemo(player);
                                    return;
                                }
                                if ("clear".equalsIgnoreCase(args[0])) {
                                    overlayExamples.clear(player);
                                    feedback.success(player, "Cleared active screen overlays.");
                                    return;
                                }

                                feedback.info(
                                        player,
                                        "Use {command} demo|clear.",
                                        Message.slot("command", feedback.command("/testoverlays"))
                                );
                            }
                        }
                ));
    }
}
