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
        getLogger().info("Paper example ready. Joining players open the house-style gallery, and /testmenus plus /testoverlays expose the dev harness.");
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
                "Use {menus} to reopen the menu gallery and {overlays} to preview the screen overlay subsystem.",
                Message.slot("menus", feedback.command("/testmenus")),
                Message.slot("overlays", feedback.command("/testoverlays"))
        );
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                {
                    event.registrar().register(
                            "testmenus",
                            "Open the menu demo harness.",
                            java.util.List.of("testmenu"),
                            new BasicCommand() {
                                @Override
                                public void execute(CommandSourceStack stack, String[] args) {
                                    CommandSender sender = stack.getSender();
                                    if (!(sender instanceof Player player)) {
                                        feedback.error(sender, "This command can only be used by a player.");
                                        return;
                                    }

                                    if (args.length == 0 || "tabs".equalsIgnoreCase(args[0])) {
                                        menus.open(player, examples.tabsGallery());
                                        return;
                                    }

                                    switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                                        case "list" -> menus.open(player, examples.listGallery());
                                        case "reactive" -> menus.open(player, examples.reactiveGallery());
                                        case "snake" -> menus.open(player, examples.snakeDemo());
                                        case "lockdrag" -> menus.open(player, examples.lockDragDemo());
                                        case "lockclick" -> menus.open(player, examples.lockClickDemo());
                                        case "profile" -> menus.open(player, examples.profilePreview());
                                        case "farming" -> menus.open(player, examples.farmingPreview());
                                        case "museum" -> menus.open(player, examples.museumPreview());
                                        case "slot5" -> menus.open(player, examples.slotFivePreview());
                                        case "canvas" -> menus.open(player, examples.canvasGallery());
                                        case "help" -> feedback.info(
                                                player,
                                                "Use {command} tabs|list|reactive|snake|lockdrag|lockclick|profile|farming|museum|slot5|canvas.",
                                                Message.slot("command", feedback.command("/testmenus"))
                                        );
                                        default -> feedback.info(
                                                player,
                                                "Use {command} tabs|list|reactive|snake|lockdrag|lockclick|profile|farming|museum|slot5|canvas.",
                                                Message.slot("command", feedback.command("/testmenus"))
                                        );
                                    }
                                }
                            }
                    );
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
                    );
                });
    }
}
