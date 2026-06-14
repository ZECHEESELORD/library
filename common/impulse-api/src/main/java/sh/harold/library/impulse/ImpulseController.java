package sh.harold.library.impulse;

import net.kyori.adventure.key.Key;
import sh.harold.library.tick.KeyedHandle;

public interface ImpulseController extends AutoCloseable {

    KeyedHandle start(ImpulseSpec spec);

    boolean stop(Key key);

    void stopAll();

    ComposedImpulse tick(ImpulseActorState actorState);

    boolean hasActiveImpulses();

    @Override
    void close();
}
