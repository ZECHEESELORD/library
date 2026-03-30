package sh.harold.creative.library.overlay;

import net.kyori.adventure.key.Key;

import java.util.Objects;

public record ScreenOverlayRequest(Key key, ScreenOverlay overlay) {

    public ScreenOverlayRequest {
        key = Objects.requireNonNull(key, "key");
        overlay = Objects.requireNonNull(overlay, "overlay");
    }
}
