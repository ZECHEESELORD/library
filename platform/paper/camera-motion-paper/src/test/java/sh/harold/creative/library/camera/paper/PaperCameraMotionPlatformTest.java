package sh.harold.creative.library.camera.paper;

import net.kyori.adventure.key.Key;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.camera.BlendMode;
import sh.harold.creative.library.camera.CameraMotion;
import sh.harold.creative.library.camera.CameraMotions;
import sh.harold.creative.library.camera.Waveform;
import sh.harold.creative.library.camera.core.StandardCameraMotionService;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class PaperCameraMotionPlatformTest {

    @Test
    void tickAppliesOneComposedRelativeTeleport() {
        Plugin plugin = mock(Plugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenReturn(task);

        StandardCameraMotionService motions = new StandardCameraMotionService();
        UUID viewerId = UUID.randomUUID();
        Player player = mock(Player.class);
        World world = mock(World.class);
        Location current = new Location(world, 4.0, 70.0, -2.0, 10.0f, 20.0f);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(current);
        when(player.teleport(any(Location.class), same(org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN), any(TeleportFlag[].class)))
                .thenReturn(true);

        PaperCameraMotionPlatform platform = new PaperCameraMotionPlatform(plugin, motions, scheduler, id -> id.equals(viewerId) ? player : null, false);
        platform.start(viewerId, impulse("recoil", 1.0, -2.0));

        platform.tick();

        ArgumentCaptor<Location> locationCaptor = ArgumentCaptor.forClass(Location.class);
        ArgumentCaptor<TeleportFlag[]> flagsCaptor = ArgumentCaptor.forClass(TeleportFlag[].class);
        verify(player).teleport(locationCaptor.capture(), same(org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN), flagsCaptor.capture());
        verify(player, never()).setRotation(any(Float.class), any(Float.class));

        Location target = locationCaptor.getValue();
        assertEquals(4.0, target.getX());
        assertEquals(70.0, target.getY());
        assertEquals(-2.0, target.getZ());
        assertEquals(1.0f, target.getYaw());
        assertEquals(-2.0f, target.getPitch());
        assertIterableEquals(
                java.util.List.of(
                        TeleportFlag.Relative.YAW,
                        TeleportFlag.Relative.PITCH,
                        TeleportFlag.Relative.VELOCITY_X,
                        TeleportFlag.Relative.VELOCITY_Y,
                        TeleportFlag.Relative.VELOCITY_Z,
                        TeleportFlag.EntityState.RETAIN_VEHICLE,
                        TeleportFlag.EntityState.RETAIN_OPEN_INVENTORY
                ),
                java.util.List.of(flagsCaptor.getValue())
        );

        platform.close();
        verify(task).cancel();
    }

    @Test
    void tickClampsPitchAgainstTheLivePlayerPitch() {
        Plugin plugin = mock(Plugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenReturn(task);

        StandardCameraMotionService motions = new StandardCameraMotionService();
        UUID viewerId = UUID.randomUUID();
        Player player = mock(Player.class);
        World world = mock(World.class);
        Location current = new Location(world, 0.0, 64.0, 0.0, 15.0f, 85.0f);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(current);
        when(player.teleport(any(Location.class), same(org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN), any(TeleportFlag[].class)))
                .thenReturn(true);

        PaperCameraMotionPlatform platform = new PaperCameraMotionPlatform(plugin, motions, scheduler, id -> id.equals(viewerId) ? player : null, false);
        platform.start(viewerId, impulse("clamp", 0.0, 10.0));

        platform.tick();

        ArgumentCaptor<Location> locationCaptor = ArgumentCaptor.forClass(Location.class);
        verify(player).teleport(locationCaptor.capture(), same(org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN), any(TeleportFlag[].class));
        assertEquals(5.0f, locationCaptor.getValue().getPitch());
    }

    @Test
    void missingPlayersAreDiscardedFromTheControllerMap() {
        Plugin plugin = mock(Plugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenReturn(task);

        StandardCameraMotionService motions = new StandardCameraMotionService();
        UUID viewerId = UUID.randomUUID();
        PaperCameraMotionPlatform platform = new PaperCameraMotionPlatform(plugin, motions, scheduler, ignored -> null, false);
        platform.start(viewerId, impulse("orphaned", 1.0, 0.0));

        platform.tick();

        assertEquals(java.util.List.of(), motions.activeViewers().stream().toList());
    }

    @Test
    void lifecycleCleanupDiscardsViewerState() {
        Plugin plugin = mock(Plugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenReturn(task);

        StandardCameraMotionService motions = new StandardCameraMotionService();
        UUID viewerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(viewerId);

        PaperCameraMotionPlatform platform = new PaperCameraMotionPlatform(plugin, motions, scheduler, ignored -> player, false);
        platform.start(viewerId, impulse("cleanup", 1.0, 0.0));

        org.bukkit.event.player.PlayerQuitEvent quit = mock(org.bukkit.event.player.PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);

        platform.onPlayerQuit(quit);

        assertEquals(java.util.List.of(), motions.activeViewers().stream().toList());
    }

    private static CameraMotion impulse(String key, double yaw, double pitch) {
        return CameraMotions.motion(
                Key.key("test", key),
                BlendMode.ADD,
                CameraMotions.axis(yaw, 1L, 0L, Waveform.IMPULSE),
                CameraMotions.axis(pitch, 1L, 0L, Waveform.IMPULSE),
                CameraMotions.constant(1L, 1.0)
        );
    }
}
