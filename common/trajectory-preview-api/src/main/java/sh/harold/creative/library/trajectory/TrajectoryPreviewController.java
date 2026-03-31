package sh.harold.creative.library.trajectory;

import net.kyori.adventure.key.Key;
import sh.harold.creative.library.tick.KeyedHandle;

import java.util.List;

public interface TrajectoryPreviewController extends AutoCloseable {

    KeyedHandle start(TrajectoryPreviewSpec spec);

    boolean refresh(Key key);

    boolean stop(Key key);

    void stopAll();

    List<TrajectoryPreviewSnapshot> tick();

    boolean hasActivePreviews();

    @Override
    void close();
}
