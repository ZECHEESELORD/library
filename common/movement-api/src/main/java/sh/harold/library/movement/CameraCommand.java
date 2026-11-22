package sh.harold.library.movement;

import net.kyori.adventure.key.Key;
import sh.harold.library.camera.rig.cue.CameraCue;

import java.util.Objects;

/**
 * A camera instruction from a move. Cue time follows one convention: a cue's local time {@code k + 1} is the end of
 * move tick {@code Tk}, so local time 0 is the end of the tick before {@code T0} and the camera responds in the frames
 * drawn right after {@code T0}. A host whose rig evaluates frames drawn after tick {@code n} at {@code n - 1 + partial}
 * (camera-core's convention) therefore starts a cue at rig time {@code now() - 1} during the move's tick.
 */
public sealed interface CameraCommand {

    /** Starts (or replaces, by key) a cue. */
    record Start(CameraCue cue) implements CameraCommand {
        public Start {
            Objects.requireNonNull(cue, "cue");
        }
    }

    /** Fades a live cue out from its current values. */
    record Release(Key key) implements CameraCommand {
        public Release {
            Objects.requireNonNull(key, "key");
        }
    }

    /** Removes a live cue at once. */
    record Cancel(Key key) implements CameraCommand {
        public Cancel {
            Objects.requireNonNull(key, "key");
        }
    }
}
