package sh.harold.library.ambient;

import net.kyori.adventure.key.Key;
import sh.harold.library.spatial.AnchorResolver;
import sh.harold.library.tick.KeyedHandle;

import java.util.List;

public interface AmbientZoneController extends AutoCloseable {

    KeyedHandle start(ZoneSpec spec);

    boolean stop(Key key);

    void stopAll();

    List<AmbientSnapshot> tick(List<ViewerAmbientState> viewers, AnchorResolver anchorResolver);

    boolean hasActiveZones();

    @Override
    void close();
}
