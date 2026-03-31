package sh.harold.creative.library.tween;

import sh.harold.creative.library.tick.KeyedHandle;

public interface TweenHandle extends KeyedHandle {

    void pause();

    void resume();

    void cancel();

    @Override
    default void close() {
        cancel();
    }
}
