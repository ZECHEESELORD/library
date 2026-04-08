package sh.harold.creative.library.camera.minestom;

import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.RelativeFlags;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent;
import net.minestom.server.event.player.PlayerDeathEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerRespawnEvent;
import net.minestom.server.event.player.PlayerTickEndEvent;
import sh.harold.creative.library.camera.CameraDelta;
import sh.harold.creative.library.camera.CameraMotion;
import sh.harold.creative.library.camera.CameraMotionPlayback;
import sh.harold.creative.library.camera.CameraMotionService;
import sh.harold.creative.library.camera.core.StandardCameraMotionService;

import java.util.Objects;
import java.util.UUID;

public final class MinestomCameraMotionPlatform implements CameraMotionService {

    private final StandardCameraMotionService motions;
    private final EventNode<Event> eventNode;
    private final boolean attached;
    private boolean closed;

    public MinestomCameraMotionPlatform() {
        this(new StandardCameraMotionService(), true);
    }

    MinestomCameraMotionPlatform(StandardCameraMotionService motions, boolean attachToGlobalHandler) {
        this.motions = Objects.requireNonNull(motions, "motions");
        this.eventNode = EventNode.all("library-camera-motion");
        eventNode.addListener(PlayerTickEndEvent.class, this::onPlayerTickEnd);
        eventNode.addListener(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        eventNode.addListener(PlayerDeathEvent.class, this::onPlayerDeath);
        eventNode.addListener(PlayerRespawnEvent.class, this::onPlayerRespawn);
        eventNode.addListener(RemoveEntityFromInstanceEvent.class, this::onRemoveEntityFromInstance);
        this.attached = attachToGlobalHandler;
        if (attachToGlobalHandler) {
            MinecraftServer.getGlobalEventHandler().addChild(eventNode);
        }
    }

    public CameraMotionPlayback start(Player player, CameraMotion motion) {
        return start(player.getUuid(), motion);
    }

    public boolean stop(Player player, Key key) {
        return stop(player.getUuid(), key);
    }

    public void stopAll(Player player) {
        stopAll(player.getUuid());
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
        if (attached) {
            MinecraftServer.getGlobalEventHandler().removeChild(eventNode);
        }
        motions.close();
    }

    void onPlayerTickEnd(PlayerTickEndEvent event) {
        CameraDelta delta = motions.tick(event.getPlayer().getUuid());
        if (!delta.isZero()) {
            applyDelta(event.getPlayer(), delta);
        }
    }

    void onPlayerDisconnect(PlayerDisconnectEvent event) {
        motions.discardViewer(event.getPlayer().getUuid());
    }

    void onPlayerDeath(PlayerDeathEvent event) {
        motions.discardViewer(event.getPlayer().getUuid());
    }

    void onPlayerRespawn(PlayerRespawnEvent event) {
        motions.discardViewer(event.getPlayer().getUuid());
    }

    void onRemoveEntityFromInstance(RemoveEntityFromInstanceEvent event) {
        if (event.getEntity() instanceof Player player) {
            motions.discardViewer(player.getUuid());
        }
    }

    private static void applyDelta(Player player, CameraDelta delta) {
        float pitchDelta = clampPitchDelta(player.getPosition().pitch(), delta.pitchDegrees());
        MinestomFutureGuard.requireCompleted(player.teleport(
                new Pos(0.0, 0.0, 0.0, (float) delta.yawDegrees(), pitchDelta),
                null,
                RelativeFlags.COORD | RelativeFlags.VIEW
        ), "camera motion teleport");
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
