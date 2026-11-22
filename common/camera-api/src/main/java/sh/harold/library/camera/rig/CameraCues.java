package sh.harold.library.camera.rig;

import net.kyori.adventure.key.Key;
import sh.harold.library.camera.rig.cue.CameraCue;
import sh.harold.library.camera.rig.cue.CameraCueHandle;

/**
 * The cue side of the camera: what movement, fights and other features call. Time is rig time in ticks; a cue started
 * on tick {@code n} has local time 0 at rig time {@code n}, the frame that shows tick {@code n}'s state.
 */
public interface CameraCues {

    /** The current tick: the start time {@link #start(CameraCue)} uses. */
    double now();

    /** Starts {@code cue} on the current tick. */
    CameraCueHandle start(CameraCue cue);

    /**
     * Starts {@code cue} at an explicit rig time, for a cue replicated from a spec with a start tick (principle 4) or a
     * fixture. A start time in the past starts the cue part-way through.
     */
    CameraCueHandle start(CameraCue cue, double startTime);

    /** Adds trauma to the shake bus on the current tick. Trauma is capped at 1 and decays linearly. */
    void addTrauma(double amount);

    /** Cancels every live cue {@code owner} started. */
    void cancelAll(Key owner);
}
