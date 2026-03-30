package sh.harold.creative.library.overlay.paper;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import sh.harold.creative.library.overlay.ScreenOverlayHandle;
import sh.harold.creative.library.overlay.ScreenOverlayRequest;
import sh.harold.creative.library.overlay.core.OverlayFace;
import sh.harold.creative.library.overlay.core.ScreenOverlayComposite;
import sh.harold.creative.library.overlay.core.ScreenOverlayShellGeometry;
import sh.harold.creative.library.overlay.core.StandardScreenOverlayController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PaperScreenOverlayPlatform implements Listener, AutoCloseable {
    private static final Component BLANK_TEXT = Component.text(" ");
    private static final Display.Brightness FULL_BRIGHT = new Display.Brightness(15, 15);

    private final JavaPlugin plugin;
    private final Map<UUID, PaperOverlaySession> sessions = new ConcurrentHashMap<>();

    public PaperScreenOverlayPlatform(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public ScreenOverlayHandle show(Player player, ScreenOverlayRequest request) {
        requirePrimaryThread("show Paper screen overlays");
        Player target = Objects.requireNonNull(player, "player");
        PaperOverlaySession session = sessions.compute(target.getUniqueId(), (ignored, existing) ->
                existing == null || existing.closed() ? new PaperOverlaySession(target.getUniqueId()) : existing
        );
        return session.show(target, Objects.requireNonNull(request, "request"));
    }

    public void clear(Player player, Key key) {
        requirePrimaryThread("clear Paper screen overlays");
        PaperOverlaySession session = sessions.get(Objects.requireNonNull(player, "player").getUniqueId());
        if (session != null) {
            session.clear(Objects.requireNonNull(player, "player"), Objects.requireNonNull(key, "key"));
        }
    }

    public void clearAll(Player player) {
        requirePrimaryThread("clear Paper screen overlays");
        PaperOverlaySession session = sessions.get(Objects.requireNonNull(player, "player").getUniqueId());
        if (session != null) {
            session.clearAll(player);
        }
    }

    @Override
    public void close() {
        requirePrimaryThread("close Paper screen overlay platform");
        HandlerList.unregisterAll(this);
        List<PaperOverlaySession> currentSessions = new ArrayList<>(sessions.values());
        sessions.clear();
        currentSessions.forEach(PaperOverlaySession::close);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        for (PaperOverlaySession session : sessions.values()) {
            session.hideFrom(joining);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        cleanup(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        cleanup(event.getEntity());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        cleanup(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        cleanup(event.getPlayer());
    }

    private void cleanup(Player player) {
        PaperOverlaySession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.close();
        }
    }

    private static void requirePrimaryThread(String action) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(action + " must run on the Paper primary server thread");
        }
    }

    private final class PaperOverlaySession {
        private final UUID playerId;
        private final StandardScreenOverlayController controller = new StandardScreenOverlayController();

        private PaperOverlayShell shell;
        private BukkitTask tickTask;
        private boolean closed;

        private PaperOverlaySession(UUID playerId) {
            this.playerId = playerId;
        }

        private ScreenOverlayHandle show(Player player, ScreenOverlayRequest request) {
            ensureOpen();
            ScreenOverlayHandle handle = controller.show(request);
            reconcile(player);
            ensureTicking();
            return handle;
        }

        private void clear(Player player, Key key) {
            if (closed) {
                return;
            }
            controller.clear(key);
            reconcile(player);
        }

        private void clearAll(Player player) {
            if (closed) {
                return;
            }
            controller.clearAll();
            reconcile(player);
        }

        private void hideFrom(Player viewer) {
            if (shell != null && !viewer.getUniqueId().equals(playerId)) {
                shell.hideFrom(viewer);
            }
        }

        private boolean closed() {
            return closed;
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            controller.close();
            if (tickTask != null) {
                tickTask.cancel();
                tickTask = null;
            }
            if (shell != null) {
                shell.close();
                shell = null;
            }
        }

        private void ensureTicking() {
            if (tickTask == null) {
                tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
            }
        }

        private void tick() {
            if (closed) {
                return;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline() || player.isDead()) {
                sessions.remove(playerId, this);
                close();
                return;
            }
            controller.advance();
            reconcile(player);
        }

        private void reconcile(Player player) {
            if (closed) {
                return;
            }
            if (!controller.hasActiveOverlays()) {
                sessions.remove(playerId, this);
                close();
                return;
            }

            if (shell == null || shell.world() != player.getWorld()) {
                if (shell != null) {
                    shell.close();
                }
                shell = new PaperOverlayShell(player);
            }
            shell.update(player, controller.composite());
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("Paper overlay session is closed");
            }
        }
    }

    private final class PaperOverlayShell {
        private final List<TextDisplay> faces = new ArrayList<>(ScreenOverlayShellGeometry.faces().size());

        private PaperOverlayShell(Player owner) {
            Location anchor = anchorLocation(owner);
            for (OverlayFace face : ScreenOverlayShellGeometry.faces()) {
                TextDisplay display = owner.getWorld().spawn(anchor, TextDisplay.class, textDisplay -> configure(textDisplay, face));
                owner.showEntity(plugin, display);
                faces.add(display);
            }
            for (Player viewer : plugin.getServer().getOnlinePlayers()) {
                if (viewer.getUniqueId().equals(owner.getUniqueId())) {
                    continue;
                }
                hideFrom(viewer);
            }
        }

        private World world() {
            return faces.getFirst().getWorld();
        }

        private void update(Player owner, ScreenOverlayComposite composite) {
            Location anchor = anchorLocation(owner);
            Color background = Color.fromARGB(composite.argb());
            byte opacity = (byte) composite.alphaByte();
            for (TextDisplay display : faces) {
                display.teleport(anchor);
                display.setBackgroundColor(background);
                display.setTextOpacity(opacity);
            }
        }

        private void hideFrom(Player viewer) {
            for (TextDisplay display : faces) {
                viewer.hideEntity(plugin, display);
            }
        }

        private void close() {
            for (TextDisplay display : faces) {
                display.remove();
            }
            faces.clear();
        }

        private void configure(TextDisplay display, OverlayFace face) {
            display.text(BLANK_TEXT);
            display.setLineWidth(1);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setBillboard(Display.Billboard.FIXED);
            display.setBrightness(FULL_BRIGHT);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(1);
            display.setTeleportDuration(1);
            display.setViewRange(1.0f);
            display.setDisplayWidth(ScreenOverlayShellGeometry.faceWidth(face));
            display.setDisplayHeight(ScreenOverlayShellGeometry.faceHeight(face));
            display.setShadowRadius(0.0f);
            display.setShadowStrength(0.0f);
            display.setShadowed(false);
            display.setSeeThrough(true);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(0));
            display.setTextOpacity((byte) 0);
            display.setSilent(true);
            display.setGravity(false);
            display.setInvulnerable(true);
            display.setPersistent(false);
            display.setVisibleByDefault(false);
            display.setTransformationMatrix(new org.joml.Matrix4f(ScreenOverlayShellGeometry.faceTransform(face)));
        }

        private Location anchorLocation(Player owner) {
            Location base = owner.getLocation();
            return new Location(base.getWorld(), base.getX(), base.getY(), base.getZ(), 0.0f, 0.0f);
        }
    }
}
