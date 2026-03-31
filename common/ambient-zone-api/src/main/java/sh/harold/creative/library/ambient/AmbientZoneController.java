package sh.harold.creative.library.ambient;

import net.kyori.adventure.key.Key;
import sh.harold.creative.library.spatial.AnchorResolver;
import sh.harold.creative.library.tick.KeyedHandle;

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
