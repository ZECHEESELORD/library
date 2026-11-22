package sh.harold.library.camera.rig.core;

import net.kyori.adventure.key.Key;
import sh.harold.library.camera.rig.CameraChannel;
import sh.harold.library.camera.rig.CameraRigSettings;
import sh.harold.library.camera.rig.CameraService;
import sh.harold.library.camera.rig.CameraState;
import sh.harold.library.camera.rig.IntensityProfile;
import sh.harold.library.camera.rig.ViewInput;
import sh.harold.library.camera.rig.cue.CameraCue;
import sh.harold.library.camera.rig.cue.CameraCueHandle;
import sh.harold.library.camera.rig.stage.AdditiveStage;
import sh.harold.library.camera.rig.stage.LayerSample;
import sh.harold.library.camera.rig.stage.RigFrame;
import sh.harold.library.tick.KeyedHandle;
import sh.harold.library.tween.signal.SignalChannelSpec;

import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * The host-free camera rig: wave 1's stack (eye anchor, additive layers, intensity constraints) with its own tick
 * clock. A host calls {@link #tick()} once per game tick and {@link #evaluate(ViewInput, double)} once per rendered
 * frame; a headless sampler does the same with fixture input.
 *
 * <p><b>Time.</b> Rig time is in ticks. {@link #now()} is the number of ticks so far, and a cue started on tick
 * {@code n} has local time 0 at rig time {@code n}. A host that calls {@code tick()} at the start of game tick
 * {@code n} and evaluates frames drawn after that tick at {@code n - 1 + partial} puts cues on the same timeline as
 * the body's interpolated position and per-frame telemetry. Cues and trauma are pure functions of time (R2), signal
 * layers step on the tick and are continued in closed form between ticks (R3).
 *
 * <p>Use from one thread at a time (the client thread); methods are synchronized so a handle closed from another
 * thread is still safe.
 */
public final class StandardCameraRig implements CameraService, AdditiveStage {

    private final Object lock = new Object();
    private final CameraRigSettings settings;
    private final CameraPipeline pipeline;
    private final CueStack cues;
    private final TraumaBus trauma;
    private final SignalLayers signals;
    private volatile IntensityProfile profile = IntensityProfile.FULL;
    private volatile CameraState lastState;
    private volatile double lastDrawnEye = Double.NaN;
    private volatile boolean closed;
    private long tick;

    public StandardCameraRig() {
        this(CameraRigSettings.DEFAULT);
    }

    public StandardCameraRig(CameraRigSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.cues = new CueStack(lock, this::now, () -> lastDrawnEye);
        this.trauma = new TraumaBus(settings.traumaDecayPerTick());
        this.signals = new SignalLayers(lock);
        this.pipeline = CameraPipeline.firstPerson(this, new IntensityConstraints(settings));
    }

    public CameraRigSettings settings() {
        return settings;
    }

    /** Advances the rig clock by one tick: steps signal layers and forgets cues no later frame can show. */
    public void tick() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            tick++;
            signals.tick();
            // Frames after this tick show times from tick - 1 on.
            cues.prune(tick - 1.0);
            trauma.prune(tick - 1.0);
        }
    }

    /** The current tick count. */
    public long ticks() {
        synchronized (lock) {
            return tick;
        }
    }

    /** Evaluates the stack for one frame at rig time {@code time} and remembers it as {@link #state()}. */
    public CameraState evaluate(ViewInput input, double time) {
        CameraState state = sample(input, time);
        lastState = state;
        lastDrawnEye = input.eyeHeight() + state.layers().eyeHeight();
        return state;
    }

    /**
     * The eye height above the feet the last evaluated frame drew (the host's eye plus the rig's offset), or
     * {@code NaN} when the host gave no eye height, no frame has been evaluated or the last frame was skipped. Curves
     * that start from the current eye start here.
     */
    public double drawnEyeHeight() {
        return lastDrawnEye;
    }

    /** Marks a frame the host drew without the rig: the last drawn eye is no longer known. */
    public void skipFrame() {
        lastDrawnEye = Double.NaN;
    }

    /** Evaluates the stack at {@code time} without remembering it (for seeks and samplers). */
    public CameraState sample(ViewInput input, double time) {
        return pipeline.evaluate(new RigFrame(time, input, profile));
    }

    @Override
    public LayerSample sample(RigFrame frame) {
        LayerAccumulator into = new LayerAccumulator();
        synchronized (lock) {
            cues.sample(frame.time(), frame.reduced(), into);
            signals.sample(frame.time(), tick, into);
            double level = trauma.trauma(frame.time());
            if (level > 0.0) {
                into.add(CameraChannel.SHAKE, settings.traumaMaxDegrees() * level * level, false);
            }
        }
        return into.toSample();
    }

    @Override
    public double now() {
        synchronized (lock) {
            return tick;
        }
    }

    @Override
    public CameraCueHandle start(CameraCue cue) {
        synchronized (lock) {
            return start(cue, tick);
        }
    }

    @Override
    public CameraCueHandle start(CameraCue cue, double startTime) {
        requireOpen();
        return cues.start(cue, startTime);
    }

    @Override
    public void addTrauma(double amount) {
        synchronized (lock) {
            requireOpen();
            trauma.add(tick, amount);
        }
    }

    /** The trauma level at rig time {@code time}. */
    public double trauma(double time) {
        synchronized (lock) {
            return trauma.trauma(time);
        }
    }

    @Override
    public void cancelAll(Key owner) {
        cues.cancelAll(Objects.requireNonNull(owner, "owner"));
    }

    /**
     * Ends every cue at once (no release) and clears trauma, keeping signal layers and the clock. Hosts call it when
     * the world goes away, for example on disconnect.
     */
    public void clearCues() {
        synchronized (lock) {
            cues.closeAll();
            trauma.clear();
        }
    }

    @Override
    public IntensityProfile intensity() {
        return profile;
    }

    @Override
    public void setIntensity(IntensityProfile newProfile) {
        this.profile = Objects.requireNonNull(newProfile, "profile");
    }

    @Override
    public KeyedHandle addSignalLayer(Key key, CameraChannel channel, SignalChannelSpec spec, DoubleSupplier signal) {
        requireOpen();
        return signals.add(key, channel, spec, signal);
    }

    @Override
    public CameraState state() {
        return lastState;
    }

    @Override
    public boolean idle() {
        synchronized (lock) {
            return closed || (cues.isEmpty() && trauma.isEmpty() && signals.atRest());
        }
    }

    @Override
    public boolean active() {
        return !closed;
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            cues.closeAll();
            signals.closeAll();
            trauma.clear();
            lastState = null;
            lastDrawnEye = Double.NaN;
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("camera rig is closed");
        }
    }
}
