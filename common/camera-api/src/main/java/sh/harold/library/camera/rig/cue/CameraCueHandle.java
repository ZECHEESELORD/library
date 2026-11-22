package sh.harold.library.camera.rig.cue;

import net.kyori.adventure.key.Key;
import sh.harold.library.tick.KeyedHandle;

/**
 * A started cue. {@link #close()} (or {@link #cancel()}) removes it at once; {@link #release()} fades it out from its
 * current values. The handle goes inactive when the cue ends, is cancelled or replaced, or its service closes. Every
 * method is idempotent and a closed handle ignores calls.
 */
public interface CameraCueHandle extends KeyedHandle {

    /** The cue's slot key. */
    @Override
    Key key();

    Key owner();

    /** The rig time the cue started at, in ticks. */
    double startTime();

    /** Starts the release on the current tick. */
    void release();

    /** Whether {@link #release()} has been called. */
    boolean released();

    /** Closes this cue and starts {@code next} now in its place, whatever {@code next}'s conflict policy says. */
    CameraCueHandle replace(CameraCue next);

    /** Removes the cue at once. The same as {@link #close()}. */
    default void cancel() {
        close();
    }
}
