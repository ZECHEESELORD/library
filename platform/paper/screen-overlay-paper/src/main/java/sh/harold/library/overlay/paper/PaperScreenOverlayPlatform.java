package sh.harold.library.overlay.paper;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.joml.Vector3f;
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
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import sh.harold.library.overlay.ScreenOverlayHandle;
import sh.harold.library.overlay.ScreenOverlayRequest;
import sh.harold.library.overlay.core.OverlayFace;
import sh.harold.library.overlay.core.ScreenOverlayComposite;
import sh.harold.library.overlay.core.ScreenOverlayShellGeometry;
import sh.harold.library.overlay.core.StandardScreenOverlayController;

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
        Player target = Objects.requireNonNull(player, "player");
        requireOwned(target, "show Paper screen overlays");
        PaperOverlaySession session = sessions.compute(target.getUniqueId(), (ignored, existing) ->
                existing == null || existing.closed() ? new PaperOverlaySession(target.getUniqueId()) : existing
        );
        return session.show(target, Objects.requireNonNull(request, "request"));
    }

    public void clear(Player player, Key key) {
        Player target = Objects.requireNonNull(player, "player");
        requireOwned(target, "clear Paper screen overlays");
        PaperOverlaySession session = sessions.get(target.getUniqueId());
        if (session != null) {
            session.clear(Objects.requireNonNull(player, "player"), Objects.requireNonNull(key, "key"));
        }
    }

    public void clearAll(Player player) {
        Player target = Objects.requireNonNull(player, "player");
        requireOwned(target, "clear Paper screen overlays");
        PaperOverlaySession session = sessions.get(target.getUniqueId());
        if (session != null) {
            session.clearAll(player);
        }
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        List<PaperOverlaySession> currentSessions = new ArrayList<>(sessions.values());
        sessions.clear();
        currentSessions.forEach(PaperOverlaySession::closeOnOwner);
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

    private static void requireOwned(Player player, String action) {
        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            throw new IllegalStateException(action + " must run on the player's owning region thread");
        }
    }

    private final class PaperOverlaySession {
        private final UUID playerId;
        private final StandardScreenOverlayController controller = new StandardScreenOverlayController();

        private PaperOverlayShell shell;
        private ScheduledTask tickTask;
        private boolean closed;

        private PaperOverlaySession(UUID playerId) {
            this.playerId = playerId;
        }

        private ScreenOverlayHandle show(Player player, ScreenOverlayRequest request) {
            ensureOpen();
            ScreenOverlayHandle handle = controller.show(request);
            reconcile(player);
            ensureTicking(player);
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

        private void closeOnOwner() {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline() || Bukkit.isOwnedByCurrentRegion(player)) {
                close();
                return;
            }
            ScheduledTask task = player.getScheduler().run(plugin, ignored -> close(), this::close);
            if (task == null) {
                close();
            }
        }

        private void ensureTicking(Player player) {
            if (tickTask == null) {
                tickTask = player.getScheduler().runAtFixedRate(
                        plugin,
                        ignored -> tick(),
                        () -> {
                            sessions.remove(playerId, this);
                            close();
                        },
                        1L,
                        1L
                );
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
        private final World world;
        private final List<PaperOverlayFace> faces = new ArrayList<>(ScreenOverlayShellGeometry.faces().size());

        private PaperOverlayShell(Player owner) {
            this.world = owner.getWorld();
            for (OverlayFace face : ScreenOverlayShellGeometry.faces()) {
                TextDisplay display = world.spawn(faceLocation(owner, face), TextDisplay.class, textDisplay -> configure(textDisplay, face));
                owner.showEntity(plugin, display);
                faces.add(new PaperOverlayFace(face, display));
            }
        }

        private World world() {
            return world;
        }

        private void update(Player owner, ScreenOverlayComposite composite) {
            Color background = Color.fromARGB(composite.argb());
            byte opacity = (byte) composite.alphaByte();
            for (PaperOverlayFace face : faces) {
                TextDisplay display = face.display();
                Location target = faceLocation(owner, face.face());
                Runnable update = () -> {
                    display.teleportAsync(target);
                    display.setBackgroundColor(background);
                    display.setTextOpacity(opacity);
                };
                if (plugin.getServer().isOwnedByCurrentRegion(display)) {
                    update.run();
                } else {
                    display.getScheduler().execute(plugin, update, () -> { }, 1L);
                }
            }
        }

        private void close() {
            for (PaperOverlayFace face : faces) {
                TextDisplay display = face.display();
                if (plugin.getServer().isOwnedByCurrentRegion(display)) {
                    display.remove();
                } else {
                    display.getScheduler().execute(plugin, display::remove, () -> { }, 1L);
                }
            }
            faces.clear();
        }

        private void configure(TextDisplay display, OverlayFace face) {
            display.setVisibleByDefault(false);
            display.text(BLANK_TEXT);
            display.setLineWidth(ScreenOverlayShellGeometry.BLANK_TEXT_LINE_WIDTH);
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
            display.setTransformationMatrix(new org.joml.Matrix4f(ScreenOverlayShellGeometry.localFaceTransform(face)));
        }

        private Location faceLocation(Player owner, OverlayFace face) {
            Location base = owner.getLocation();
            Vector3f offset = ScreenOverlayShellGeometry.faceCenterOffset(face);
            return new Location(base.getWorld(), base.getX() + offset.x, base.getY() + offset.y, base.getZ() + offset.z, 0.0f, 0.0f);
        }
    }

    private record PaperOverlayFace(OverlayFace face, TextDisplay display) {
    }
}
