package sh.harold.creative.library.camera;

import net.kyori.adventure.key.Key;

import java.util.Objects;
import java.util.UUID;

public interface CameraMotionService extends AutoCloseable {

    CameraMotionPlayback start(UUID viewerId, CameraMotion motion);

    boolean stop(UUID viewerId, Key key);

    void stopAll(UUID viewerId);

    @Override
    void close();

    default boolean stop(UUID viewerId, String key) {
        return stop(viewerId, Key.key(Objects.requireNonNull(key, "key")));
    }
}
