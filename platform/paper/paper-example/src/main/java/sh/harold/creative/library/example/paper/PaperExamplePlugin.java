package sh.harold.creative.library.example.paper;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.creative.library.camera.paper.PaperCameraMotionPlatform;
import sh.harold.creative.library.menu.paper.PaperMenuPlatform;
import sh.harold.creative.library.sound.SoundCueKeys;
import sh.harold.creative.library.sound.paper.PaperSoundCuePlatform;
import sh.harold.creative.library.overlay.paper.PaperScreenOverlayPlatform;
import sh.harold.creative.library.message.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class PaperExamplePlugin extends JavaPlugin implements Listener {

    private static final List<Material> TEST_HOTBAR_POOL = List.of(
            Material.COMPASS,
            Material.SLIME_BALL,
            Material.HOPPER,
            Material.CHEST,
            Material.GOLDEN_HOE,
            Material.BOOK,
            Material.FISHING_ROD,
            Material.ENDER_PEARL,
            Material.COOKIE,
            Material.CLOCK,
            Material.MAP,
            Material.SHIELD,
            Material.EMERALD,
            Material.WHEAT,
            Material.DIAMOND_SWORD,
            Material.BLAZE_POWDER,
            Material.NETHER_STAR,
            Material.SPYGLASS
    );

    private PaperMenuPlatform menus;
    private PaperMenuExampleMenus examples;
    private PaperSoundCuePlatform sounds;
    private PaperScreenOverlayPlatform overlays;
    private PaperScreenOverlayExamples overlayExamples;
    private PaperCameraMotionPlatform camera;
    private PaperPrimitiveExamples primitiveExamples;
    private PaperExampleMessages feedback;

    @Override
    public void onEnable() {
        feedback = new PaperExampleMessages();
        sounds = new PaperSoundCuePlatform(this);
        overlays = new PaperScreenOverlayPlatform(this);
        camera = new PaperCameraMotionPlatform(this);
        menus = new PaperMenuPlatform(this, new sh.harold.creative.library.menu.core.StandardMenuService(), sounds);
        examples = new PaperMenuExampleMenus(menus);
        overlayExamples = new PaperScreenOverlayExamples(this, overlays, feedback);
        primitiveExamples = new PaperPrimitiveExamples(this, sounds, overlays, camera, feedback);
        registerCommands();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Paper example ready. Joining players open the house-style gallery, and /testmenus, /testoverlays, plus /testprimitives expose the dev harness.");
    }

    @Override
    public void onDisable() {
        if (primitiveExamples != null) {
            primitiveExamples.close();
        }
        if (camera != null) {
            camera.close();
        }
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
        populateTestingHotbar(event.getPlayer());
        sounds.play(event.getPlayer(), SoundCueKeys.REWARD_DISCOVERY);
        Bukkit.getScheduler().runTask(this, () -> menus.open(event.getPlayer(), examples.gallery()));
        feedback.info(
                event.getPlayer(),
                "Use {menus} to reopen the menu gallery, {overlays} to preview the screen overlay subsystem, and {primitives} for the primitive harness commands.",
                Message.slot("menus", feedback.command("/testmenus")),
                Message.slot("overlays", feedback.command("/testoverlays")),
                Message.slot("primitives", feedback.command("/testprimitives help"))
        );
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        discardPrimitiveState(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        discardPrimitiveState(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        discardPrimitiveState(event.getEntity());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        discardPrimitiveState(event.getPlayer());
        Bukkit.getScheduler().runTask(this, () -> populateTestingHotbar(event.getPlayer()));
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        discardPrimitiveState(event.getPlayer());
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
                    event.registrar().register(
                            "testtweens",
                            "Run the tween primitive harness.",
                            List.of("testtween"),
                            playerVariantCommand(
                                    primitiveExamples.tweenUsage(),
                                    player -> primitiveExamples.playTween(player, "all"),
                                    primitiveExamples::playTween
                            )
                    );
                    event.registrar().register(
                            "testcurves",
                            "Run the curve primitive harness.",
                            List.of("testcurve"),
                            playerVariantCommand(
                                    primitiveExamples.curveUsage(),
                                    player -> primitiveExamples.playCurve(player, "all"),
                                    primitiveExamples::playCurve
                            )
                    );
                    event.registrar().register(
                            "testtelegraphs",
                            "Run the telegraph primitive harness.",
                            List.of("testtelegraph"),
                            playerVariantCommand(
                                    primitiveExamples.telegraphUsage(),
                                    player -> primitiveExamples.playTelegraph(player, "all"),
                                    primitiveExamples::playTelegraph
                            )
                    );
                    event.registrar().register(
                            "testtrajectories",
                            "Run the trajectory preview primitive harness.",
                            List.of("testtrajectory"),
                            playerVariantCommand(
                                    primitiveExamples.trajectoryUsage(),
                                    player -> primitiveExamples.playTrajectory(player, "all"),
                                    primitiveExamples::playTrajectory
                            )
                    );
                    event.registrar().register(
                            "testimpulses",
                            "Run the impulse primitive harness.",
                            List.of("testimpulse"),
                            playerVariantCommand(
                                    primitiveExamples.impulseUsage(),
                                    player -> primitiveExamples.playImpulse(player, "all"),
                                    primitiveExamples::playImpulse
                            )
                    );
                    event.registrar().register(
                            "testambient",
                            "Run the ambient zone primitive harness.",
                            List.of("testambientzone"),
                            playerVariantCommand(
                                    primitiveExamples.ambientUsage(),
                                    player -> primitiveExamples.playAmbient(player, "all"),
                                    primitiveExamples::playAmbient
                            )
                    );
                    event.registrar().register(
                            "testprimitives",
                            "Run the cross-primitive composition harness.",
                            List.of("testprimitive"),
                            playerVariantCommand(
                                    primitiveExamples.primitivesUsage(),
                                    player -> primitiveExamples.playPrimitives(player, "all"),
                                    primitiveExamples::playPrimitives
                            )
                    );
                });
    }

    private BasicCommand playerVariantCommand(
            String usage,
            Consumer<Player> defaultAction,
            BiConsumer<Player, String> action
    ) {
        return new BasicCommand() {
            @Override
            public void execute(CommandSourceStack stack, String[] args) {
                CommandSender sender = stack.getSender();
                if (!(sender instanceof Player player)) {
                    feedback.error(sender, "This command can only be used by a player.");
                    return;
                }

                if (args.length == 0) {
                    defaultAction.accept(player);
                    return;
                }

                String variant = args[0].toLowerCase(Locale.ROOT);
                if ("help".equals(variant)) {
                    feedback.info(player, usage);
                    return;
                }
                action.accept(player, variant);
            }
        };
    }

    private void discardPrimitiveState(Player player) {
        if (primitiveExamples != null) {
            primitiveExamples.discard(player.getUniqueId());
        }
    }

    private void populateTestingHotbar(Player player) {
        List<Material> materials = new ArrayList<>(TEST_HOTBAR_POOL);
        Collections.shuffle(materials);
        for (int slot = 0; slot < 9; slot++) {
            player.getInventory().setItem(slot, new ItemStack(materials.get(slot)));
        }
        player.getInventory().setHeldItemSlot(0);
    }
}
