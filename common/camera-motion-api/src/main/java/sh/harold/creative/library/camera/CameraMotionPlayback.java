package sh.harold.creative.library.camera;

import net.kyori.adventure.key.Key;

import java.util.UUID;

public interface CameraMotionPlayback extends AutoCloseable {

    UUID viewerId();

    Key key();

    void stop();

    @Override
    default void close() {
        stop();
    }
}
