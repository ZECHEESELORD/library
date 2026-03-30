package sh.harold.creative.library.camera;

import net.kyori.adventure.key.Key;

import java.util.Objects;

public record CameraMotion(Key key, BlendMode blendMode, CameraAxis yaw, CameraAxis pitch, Envelope envelope) {

    public CameraMotion {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(blendMode, "blendMode");
        Objects.requireNonNull(yaw, "yaw");
        Objects.requireNonNull(pitch, "pitch");
        Objects.requireNonNull(envelope, "envelope");
    }
}
