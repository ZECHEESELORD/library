package sh.harold.creative.library.camera.paper;

import net.kyori.adventure.key.Key;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import sh.harold.creative.library.camera.CameraDelta;
import sh.harold.creative.library.camera.CameraMotion;
import sh.harold.creative.library.camera.CameraMotionPlayback;
import sh.harold.creative.library.camera.CameraMotionService;
import sh.harold.creative.library.camera.core.StandardCameraMotionService;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class PaperCameraMotionPlatform implements CameraMotionService, Listener {

    private static final TeleportFlag[] VIEW_TELEPORT_FLAGS = {
            TeleportFlag.Relative.YAW,
            TeleportFlag.Relative.PITCH,
            TeleportFlag.Relative.VELOCITY_X,
            TeleportFlag.Relative.VELOCITY_Y,
            TeleportFlag.Relative.VELOCITY_Z,
            TeleportFlag.EntityState.RETAIN_VEHICLE,
            TeleportFlag.EntityState.RETAIN_OPEN_INVENTORY
    };

    private final StandardCameraMotionService motions;
    private final Function<UUID, Player> playerLookup;
    private final BukkitTask tickTask;
    private boolean closed;

    public PaperCameraMotionPlatform(JavaPlugin plugin) {
        this(plugin, new StandardCameraMotionService(), plugin.getServer().getScheduler(), plugin.getServer()::getPlayer, true);
    }

    PaperCameraMotionPlatform(
            Plugin plugin,
            StandardCameraMotionService motions,
            BukkitScheduler scheduler,
            Function<UUID, Player> playerLookup,
            boolean registerListener
    ) {
        Plugin owningPlugin = Objects.requireNonNull(plugin, "plugin");
        this.motions = Objects.requireNonNull(motions, "motions");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        BukkitScheduler bukkitScheduler = Objects.requireNonNull(scheduler, "scheduler");
        if (registerListener) {
            owningPlugin.getServer().getPluginManager().registerEvents(this, owningPlugin);
        }
        this.tickTask = bukkitScheduler.runTaskTimer(owningPlugin, this::tick, 1L, 1L);
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
        return motions.start(viewerId, motion);
    }

    @Override
    public boolean stop(UUID viewerId, Key key) {
        return motions.stop(viewerId, key);
    }

    @Override
    public void stopAll(UUID viewerId) {
        motions.stopAll(viewerId);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        tickTask.cancel();
        HandlerList.unregisterAll(this);
        motions.close();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        motions.discardViewer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        motions.discardViewer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        motions.discardViewer(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        motions.discardViewer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        motions.discardViewer(event.getPlayer().getUniqueId());
    }

    void tick() {
        for (UUID viewerId : motions.activeViewers()) {
            Player player = playerLookup.apply(viewerId);
            if (player == null || !player.isOnline()) {
                motions.discardViewer(viewerId);
                continue;
            }
            CameraDelta delta = motions.tick(viewerId);
            if (!delta.isZero()) {
                applyDelta(player, delta);
            }
        }
    }

    private static void applyDelta(Player player, CameraDelta delta) {
        Location current = player.getLocation();
        float pitchDelta = clampPitchDelta(current.getPitch(), delta.pitchDegrees());
        Location target = current.clone();
        target.setYaw((float) delta.yawDegrees());
        target.setPitch(pitchDelta);
        player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN, VIEW_TELEPORT_FLAGS);
    }

    private static float clampPitchDelta(float currentPitch, double pitchDelta) {
        return clampPitch((float) (currentPitch + pitchDelta)) - currentPitch;
    }

    private static float clampPitch(float pitch) {
        if (pitch < -90.0f) {
            return -90.0f;
        }
        if (pitch > 90.0f) {
            return 90.0f;
        }
        return pitch;
    }
}
