package sh.harold.creative.library.overlay;

import net.kyori.adventure.key.Key;

public interface ScreenOverlayHandle extends AutoCloseable {

    Key key();

    boolean active();

    @Override
    void close();
}
