package sh.harold.creative.library.tween;

import net.kyori.adventure.key.Key;

import java.util.List;

public interface TweenController<T> extends AutoCloseable {

    TweenHandle start(Tween<T> tween);

    boolean stop(Key key);

    void stopAll();

    boolean pause(Key key);

    boolean resume(Key key);

    List<TweenSample<T>> tick();

    boolean hasActiveTweens();

    @Override
    void close();
}
