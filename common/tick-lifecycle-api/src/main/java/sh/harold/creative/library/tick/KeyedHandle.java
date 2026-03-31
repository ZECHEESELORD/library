package sh.harold.creative.library.tick;

import net.kyori.adventure.key.Key;

public interface KeyedHandle extends AutoCloseable {

    Key key();

    boolean active();

    @Override
    void close();
}
