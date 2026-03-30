package sh.harold.creative.library.camera.minestom;

import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.RelativeFlags;
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerTickEndEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.camera.BlendMode;
import sh.harold.creative.library.camera.CameraMotion;
import sh.harold.creative.library.camera.CameraMotions;
import sh.harold.creative.library.camera.Waveform;
import sh.harold.creative.library.camera.core.StandardCameraMotionService;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MinestomCameraMotionPlatformTest {

    private static boolean serverInitialized;

    @BeforeAll
    static void initServer() {
        if (!serverInitialized) {
            MinecraftServer.init();
            serverInitialized = true;
        }
    }

    @Test
    void tickEndAppliesOneRelativeTeleportForThePlayer() {
        StandardCameraMotionService motions = new StandardCameraMotionService();
        MinestomCameraMotionPlatform platform = new MinestomCameraMotionPlatform(motions, false);
        TestPlayer player = new TestPlayer(UUID.randomUUID());
        platform.start(player, impulse("recoil", 1.0, -2.0));

        platform.onPlayerTickEnd(new PlayerTickEndEvent(player));

        assertEquals(List.of(new TeleportUpdate(new Pos(0.0, 0.0, 0.0, 1.0f, -2.0f), null, RelativeFlags.COORD | RelativeFlags.VIEW)), player.teleports());
    }

    @Test
    void tickEndClampsPitchAgainstTheLivePlayerPitch() {
        StandardCameraMotionService motions = new StandardCameraMotionService();
        MinestomCameraMotionPlatform platform = new MinestomCameraMotionPlatform(motions, false);
        TestPlayer player = new TestPlayer(UUID.randomUUID());
        player.position = new Pos(12.0, 64.0, -5.0, 30.0f, 85.0f);
        platform.start(player, impulse("clamp", 0.0, 10.0));

        platform.onPlayerTickEnd(new PlayerTickEndEvent(player));

        assertEquals(1, player.teleports().size());
        TeleportUpdate update = player.teleports().getFirst();
        assertEquals(0.0, update.target().x());
        assertEquals(0.0, update.target().y());
        assertEquals(0.0, update.target().z());
        assertEquals(0.0f, update.target().yaw());
        assertEquals(5.0f, update.target().pitch());
        assertNull(update.chunks());
        assertEquals(RelativeFlags.COORD | RelativeFlags.VIEW, update.flags());
    }

    @Test
    void disconnectDiscardsViewerState() {
        StandardCameraMotionService motions = new StandardCameraMotionService();
        MinestomCameraMotionPlatform platform = new MinestomCameraMotionPlatform(motions, false);
        TestPlayer player = new TestPlayer(UUID.randomUUID());
        platform.start(player, impulse("disconnect", 1.0, 0.0));

        platform.onPlayerDisconnect(new PlayerDisconnectEvent(player));

        assertEquals(java.util.List.of(), motions.activeViewers().stream().toList());
    }

    @Test
    void removingAPlayerFromAnInstanceDiscardsViewerState() {
        StandardCameraMotionService motions = new StandardCameraMotionService();
        MinestomCameraMotionPlatform platform = new MinestomCameraMotionPlatform(motions, false);
        TestPlayer player = new TestPlayer(UUID.randomUUID());
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        platform.start(player, impulse("transfer", 1.0, 0.0));

        platform.onRemoveEntityFromInstance(new RemoveEntityFromInstanceEvent(instance, player));

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

    private static final class TestPlayer extends Player {

        private final List<TeleportUpdate> teleports = new ArrayList<>();
        private Pos position = Pos.ZERO;

        private TestPlayer(UUID uuid) {
            super(new TestPlayerConnection(), new GameProfile(uuid, "camera-test"));
        }

        @Override
        public Pos getPosition() {
            return position;
        }

        @Override
        public CompletableFuture<Void> teleport(Pos target, long[] chunks, int flags) {
            teleports.add(new TeleportUpdate(target, chunks, flags));
            float yaw = (flags & RelativeFlags.YAW) != 0 ? position.yaw() + target.yaw() : target.yaw();
            float pitch = (flags & RelativeFlags.PITCH) != 0 ? position.pitch() + target.pitch() : target.pitch();
            double x = (flags & RelativeFlags.X) != 0 ? position.x() + target.x() : target.x();
            double y = (flags & RelativeFlags.Y) != 0 ? position.y() + target.y() : target.y();
            double z = (flags & RelativeFlags.Z) != 0 ? position.z() + target.z() : target.z();
            position = new Pos(x, y, z, yaw, pitch);
            return CompletableFuture.completedFuture(null);
        }

        private List<TeleportUpdate> teleports() {
            return List.copyOf(teleports);
        }
    }

    private record TeleportUpdate(Pos target, long[] chunks, int flags) {
    }

    private static final class TestPlayerConnection extends PlayerConnection {

        @Override
        public void sendPacket(SendablePacket packet) {
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 25565);
        }
    }
}
