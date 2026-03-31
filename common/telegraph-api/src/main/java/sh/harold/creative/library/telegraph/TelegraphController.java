package sh.harold.creative.library.telegraph;

import net.kyori.adventure.key.Key;
import sh.harold.creative.library.spatial.AnchorResolver;
import sh.harold.creative.library.tick.KeyedHandle;

import java.util.List;

public interface TelegraphController extends AutoCloseable {

    KeyedHandle start(TelegraphSpec spec);

    boolean stop(Key key);

    void stopAll();

    List<TelegraphFrame> tick(AnchorResolver anchorResolver);

    boolean hasActiveTelegraphs();

    @Override
    void close();
}
