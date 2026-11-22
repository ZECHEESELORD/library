package sh.harold.library.camera.rig;

import net.kyori.adventure.key.Key;
import sh.harold.library.tick.KeyedHandle;
import sh.harold.library.tween.signal.SignalChannelSpec;

import java.util.function.DoubleSupplier;

/**
 * The camera rig as a disposable service (architecture section 8). Closing it closes every cue and layer it issued and
 * hands the camera back to vanilla. Use from the client (render) thread.
 */
public interface CameraService extends CameraCues, AutoCloseable {

    IntensityProfile intensity();

    /** Switches between Full and Reduced. Takes effect on the next frame. */
    void setIntensity(IntensityProfile profile);

    /**
     * Adds a signal-driven channel (catalog 1.2): each tick {@code signal} is read, mapped through the spec's map and
     * fed to its spring as the target; the spring's value adds to {@code channel} every frame. Examples: speed to FOV,
     * strafe input to roll. Starting a layer whose key is live replaces it.
     */
    KeyedHandle addSignalLayer(Key key, CameraChannel channel, SignalChannelSpec spec, DoubleSupplier signal);

    /** The state of the last evaluated frame, or {@code null} before the first frame. */
    CameraState state();

    /** Whether nothing is live: no cue, no trauma and no signal layer away from rest. The camera is then vanilla. */
    boolean idle();

    boolean active();

    @Override
    void close();
}
