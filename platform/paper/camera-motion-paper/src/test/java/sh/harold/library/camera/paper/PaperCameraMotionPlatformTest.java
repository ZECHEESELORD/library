package sh.harold.library.camera.paper;

import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import sh.harold.library.camera.BlendMode;
import sh.harold.library.camera.CameraMotion;
import sh.harold.library.camera.CameraMotions;
import sh.harold.library.camera.Waveform;
import sh.harold.library.camera.core.StandardCameraMotionService;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperCameraMotionPlatformTest {

    @Test
    void tickAppliesOneComposedRelativeRotation() {
        Plugin plugin = mock(Plugin.class);

        StandardCameraMotionService motions = new StandardCameraMotionService();
        UUID viewerId = UUID.randomUUID();
        Player player = mock(Player.class);
        World world = mock(World.class);
        Location current = new Location(world, 4.0, 70.0, -2.0, 10.0f, 20.0f);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(current);

        PaperCameraMotionPlatform platform = new PaperCameraMotionPlatform(plugin, motions, id -> id.equals(viewerId) ? player : null, false);
        platform.start(viewerId, impulse("recoil", 1.0, -2.0));

        platform.tick();

        verify(player).setRotation(11.0f, 18.0f);

        platform.close();
    }

    @Test
    void tickClampsPitchAgainstTheLivePlayerPitch() {
        Plugin plugin = mock(Plugin.class);

        StandardCameraMotionService motions = new StandardCameraMotionService();
        UUID viewerId = UUID.randomUUID();
        Player player = mock(Player.class);
        World world = mock(World.class);
        Location current = new Location(world, 0.0, 64.0, 0.0, 15.0f, 85.0f);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(current);

        PaperCameraMotionPlatform platform = new PaperCameraMotionPlatform(plugin, motions, id -> id.equals(viewerId) ? player : null, false);
        platform.start(viewerId, impulse("clamp", 0.0, 10.0));

        platform.tick();

        verify(player).setRotation(15.0f, 90.0f);
    }

    @Test
    void missingPlayersAreDiscardedFromTheControllerMap() {
        Plugin plugin = mock(Plugin.class);

        StandardCameraMotionService motions = new StandardCameraMotionService();
        UUID viewerId = UUID.randomUUID();
        PaperCameraMotionPlatform platform = new PaperCameraMotionPlatform(plugin, motions, ignored -> null, false);
        platform.start(viewerId, impulse("orphaned", 1.0, 0.0));

        platform.tick();

        assertEquals(java.util.List.of(), motions.activeViewers().stream().toList());
    }

    @Test
    void lifecycleCleanupDiscardsViewerState() {
        Plugin plugin = mock(Plugin.class);

        StandardCameraMotionService motions = new StandardCameraMotionService();
        UUID viewerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(viewerId);

        PaperCameraMotionPlatform platform = new PaperCameraMotionPlatform(plugin, motions, ignored -> player, false);
        platform.start(viewerId, impulse("cleanup", 1.0, 0.0));

        org.bukkit.event.player.PlayerQuitEvent quit =
                new org.bukkit.event.player.PlayerQuitEvent(player, net.kyori.adventure.text.Component.text("quit"));

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
