package sh.harold.creative.library.overlay;

import net.kyori.adventure.key.Key;

import java.util.UUID;

public interface ScreenOverlayEmitter extends AutoCloseable {

    ScreenOverlayHandle show(UUID viewerId, ScreenOverlayRequest request);

    void clear(UUID viewerId, Key key);

    void clearAll(UUID viewerId);

    @Override
    void close();
}
