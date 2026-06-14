package sh.harold.library.tween;

import sh.harold.library.tick.KeyedHandle;

public interface TweenHandle extends KeyedHandle {

    void pause();

    void resume();

    void cancel();

    @Override
    default void close() {
        cancel();
    }
}
