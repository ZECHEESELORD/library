package sh.harold.creative.library.camera.core;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.camera.BlendMode;
import sh.harold.creative.library.camera.CameraDelta;
import sh.harold.creative.library.camera.CameraMotion;
import sh.harold.creative.library.camera.CameraMotions;
import sh.harold.creative.library.camera.Waveform;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StandardCameraMotionServiceTest {

    @Test
    void explicitStopQueuesCompensationOnTheNextTick() {
        StandardCameraMotionService service = new StandardCameraMotionService();
        UUID viewerId = UUID.randomUUID();

        service.start(viewerId, impulse("recoil", BlendMode.ADD, 1.0, -2.0, 4L));

        assertEquals(new CameraDelta(1.0, -2.0), service.tick(viewerId));

        service.stop(viewerId, Key.key("test", "recoil"));

        assertEquals(new CameraDelta(-1.0, 2.0), service.tick(viewerId));
        assertEquals(CameraDelta.none(), service.tick(viewerId));
        assertEquals(java.util.List.of(), service.activeViewers().stream().toList());
    }

    @Test
    void sameKeyReplacementRestartsAndCompensatesThePreviousContribution() {
        StandardCameraMotionService service = new StandardCameraMotionService();
        UUID viewerId = UUID.randomUUID();

        service.start(viewerId, impulse("recoil", BlendMode.ADD, 1.0, 0.0, 6L));
        assertEquals(new CameraDelta(1.0, 0.0), service.tick(viewerId));

        service.start(viewerId, impulse("recoil", BlendMode.ADD, 2.0, 0.0, 6L));

        assertEquals(new CameraDelta(1.0, 0.0), service.tick(viewerId));
    }

    @Test
    void addAndMaxModesComposeOncePerTick() {
        StandardCameraMotionService service = new StandardCameraMotionService();
        UUID viewerId = UUID.randomUUID();

        service.start(viewerId, impulse("add", BlendMode.ADD, 1.0, 0.25, 2L));
        service.start(viewerId, impulse("max-small", BlendMode.MAX, 2.0, 0.5, 2L));
        service.start(viewerId, impulse("max-large", BlendMode.MAX, 5.0, -3.0, 2L));

        assertEquals(new CameraDelta(6.0, -2.75), service.tick(viewerId));
    }

    @Test
    void expiredMotionsQueueAFinalUnwind() {
        StandardCameraMotionService service = new StandardCameraMotionService();
        UUID viewerId = UUID.randomUUID();

        service.start(viewerId, impulse("burst", BlendMode.ADD, 1.25, 0.0, 1L));

        assertEquals(new CameraDelta(1.25, 0.0), service.tick(viewerId));
        assertEquals(new CameraDelta(-1.25, 0.0), service.tick(viewerId));
        assertEquals(CameraDelta.none(), service.tick(viewerId));
    }

    @Test
    void closeRejectsFurtherStarts() {
        StandardCameraMotionService service = new StandardCameraMotionService();
        service.close();

        assertThrows(IllegalStateException.class, () -> service.start(UUID.randomUUID(), impulse("closed", BlendMode.ADD, 1.0, 0.0, 2L)));
    }

    private static CameraMotion impulse(String key, BlendMode blendMode, double yaw, double pitch, long durationTicks) {
        return CameraMotions.motion(
                Key.key("test", key),
                blendMode,
                CameraMotions.axis(yaw, 1L, 0L, Waveform.IMPULSE),
                CameraMotions.axis(pitch, 1L, 0L, Waveform.IMPULSE),
                CameraMotions.constant(durationTicks, 1.0)
        );
    }
}
