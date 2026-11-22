package sh.harold.library.camera.fabric.client;

import net.kyori.adventure.key.Key;
import sh.harold.library.camera.rig.CameraChannel;
import sh.harold.library.camera.rig.CameraRigSettings;
import sh.harold.library.camera.rig.CameraService;
import sh.harold.library.camera.rig.CameraState;
import sh.harold.library.camera.rig.IntensityProfile;
import sh.harold.library.camera.rig.ViewInput;
import sh.harold.library.camera.rig.core.StandardCameraRig;
import sh.harold.library.camera.rig.cue.CameraCue;
import sh.harold.library.camera.rig.cue.CameraCueHandle;
import sh.harold.library.tick.KeyedHandle;
import sh.harold.library.tween.signal.SignalChannelSpec;

import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

/** The open camera service: a {@link StandardCameraRig} plus the state of the last frame the client drew. */
final class FabricCameraService implements CameraService {

    private final StandardCameraRig rig;
    private final Consumer<FabricCameraService> onClose;
    private volatile CameraState frameState;

    FabricCameraService(CameraRigSettings settings, Consumer<FabricCameraService> onClose) {
        this.rig = new StandardCameraRig(settings);
        this.onClose = onClose;
    }

    StandardCameraRig rig() {
        return rig;
    }

    /** Evaluates the frame drawn after the latest tick at {@code partial}, and remembers it for telemetry. */
    CameraState frame(ViewInput input, float partial) {
        CameraState state = rig.evaluate(input, rig.ticks() - 1.0 + partial);
        frameState = state;
        return state;
    }

    /** Marks a frame the rig does not drive (third person, panoramic, no player). */
    void skipFrame() {
        frameState = null;
        rig.skipFrame();
    }

    /** The last driven frame, or {@code null} when the last frame was vanilla or skipped. */
    CameraState frameState() {
        return frameState;
    }

    @Override
    public double now() {
        return rig.now();
    }

    @Override
    public CameraCueHandle start(CameraCue cue) {
        return rig.start(cue);
    }

    @Override
    public CameraCueHandle start(CameraCue cue, double startTime) {
        return rig.start(cue, startTime);
    }

    @Override
    public void addTrauma(double amount) {
        rig.addTrauma(amount);
    }

    @Override
    public void cancelAll(Key owner) {
        rig.cancelAll(owner);
    }

    @Override
    public IntensityProfile intensity() {
        return rig.intensity();
    }

    @Override
    public void setIntensity(IntensityProfile profile) {
        rig.setIntensity(profile);
    }

    @Override
    public KeyedHandle addSignalLayer(Key key, CameraChannel channel, SignalChannelSpec spec, DoubleSupplier signal) {
        return rig.addSignalLayer(key, channel, spec, signal);
    }

    @Override
    public CameraState state() {
        return rig.state();
    }

    @Override
    public boolean idle() {
        return rig.idle();
    }

    @Override
    public boolean active() {
        return rig.active();
    }

    @Override
    public void close() {
        if (!rig.active()) {
            return;
        }
        rig.close();
        frameState = null;
        onClose.accept(this);
    }
}
