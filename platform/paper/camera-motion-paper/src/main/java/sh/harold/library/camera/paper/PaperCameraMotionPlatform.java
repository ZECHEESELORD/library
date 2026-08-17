package sh.harold.library.camera.paper;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.library.camera.CameraDelta;
import sh.harold.library.camera.CameraMotion;
import sh.harold.library.camera.CameraMotionPlayback;
import sh.harold.library.camera.CameraMotionService;
import sh.harold.library.camera.core.StandardCameraMotionService;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Paper/Folia camera motion adapter. Every native player mutation runs on that player's scheduler. */
public final class PaperCameraMotionPlatform implements CameraMotionService, Listener {

    private final Plugin plugin;
    private final StandardCameraMotionService motions;
    private final Function<UUID, Player> playerLookup;
    private final boolean scheduleTicks;
    private final Map<UUID, ScheduledTask> tickTasks = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public PaperCameraMotionPlatform(JavaPlugin plugin) {
        this(plugin, new StandardCameraMotionService(), plugin.getServer()::getPlayer, true);
    }

    PaperCameraMotionPlatform(
            Plugin plugin,
            StandardCameraMotionService motions,
            Function<UUID, Player> playerLookup,
            boolean registerListener
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.motions = Objects.requireNonNull(motions, "motions");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.scheduleTicks = registerListener;
        if (registerListener) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
        }
    }

    public CameraMotionPlayback start(Player player, CameraMotion motion) {
        return start(player.getUniqueId(), motion);
    }

    public boolean stop(Player player, Key key) {
        return stop(player.getUniqueId(), key);
    }

    public void stopAll(Player player) {
        stopAll(player.getUniqueId());
    }

    @Override
    public CameraMotionPlayback start(UUID viewerId, CameraMotion motion) {
        ensureOpen();
        CameraMotionPlayback playback = motions.start(viewerId, motion);
        if (scheduleTicks) {
            ensureTicking(viewerId);
        }
        return playback;
    }

    @Override
    public boolean stop(UUID viewerId, Key key) {
        boolean stopped = motions.stop(viewerId, key);
        cancelIfIdle(viewerId);
        return stopped;
    }

    @Override
    public void stopAll(UUID viewerId) {
        motions.stopAll(viewerId);
        cancelIfIdle(viewerId);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        tickTasks.values().forEach(ScheduledTask::cancel);
        tickTasks.clear();
        HandlerList.unregisterAll(this);
        motions.close();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        discard(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        discard(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        discard(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        discard(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        discard(event.getPlayer().getUniqueId());
    }

    void tick() {
        for (UUID viewerId : motions.activeViewers()) {
            tickViewer(viewerId);
        }
    }

    private void ensureTicking(UUID viewerId) {
        if (tickTasks.containsKey(viewerId)) {
            return;
        }
        Player player = playerLookup.apply(viewerId);
        if (player == null || !player.isOnline()) {
            motions.discardViewer(viewerId);
            return;
        }
        ScheduledTask task = player.getScheduler().runAtFixedRate(
                plugin,
                ignored -> tickViewer(viewerId),
                () -> discard(viewerId),
                1L,
                1L
        );
        if (task == null) {
            motions.discardViewer(viewerId);
            return;
        }
        ScheduledTask existing = tickTasks.putIfAbsent(viewerId, task);
        if (existing != null) {
            task.cancel();
        }
    }

    private void tickViewer(UUID viewerId) {
        if (closed) {
            discard(viewerId);
            return;
        }
        Player player = playerLookup.apply(viewerId);
        if (player == null || !player.isOnline()) {
            discard(viewerId);
            return;
        }
        CameraDelta delta = motions.tick(viewerId);
        if (!delta.isZero()) {
            applyDelta(player, delta);
        }
        cancelIfIdle(viewerId);
    }

    private void discard(UUID viewerId) {
        motions.discardViewer(viewerId);
        ScheduledTask task = tickTasks.remove(viewerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void cancelIfIdle(UUID viewerId) {
        if (motions.activeViewers().contains(viewerId)) {
            return;
        }
        ScheduledTask task = tickTasks.remove(viewerId);
        if (task != null) {
            task.cancel();
        }
    }

    private static void applyDelta(Player player, CameraDelta delta) {
        Location current = player.getLocation();
        float yaw = (float) (current.getYaw() + delta.yawDegrees());
        float pitch = clampPitch((float) (current.getPitch() + delta.pitchDegrees()));
        player.setRotation(yaw, pitch);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Paper camera motion platform is closed");
        }
    }

    private static float clampPitch(float pitch) {
        return Math.max(-90.0f, Math.min(90.0f, pitch));
    }
}
