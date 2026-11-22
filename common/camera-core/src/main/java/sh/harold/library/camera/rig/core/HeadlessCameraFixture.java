package sh.harold.library.camera.rig.core;

import sh.harold.library.camera.rig.CameraState;
import sh.harold.library.camera.rig.ViewInput;
import sh.harold.library.telemetry.FrameTime;

import java.util.Objects;

/**
 * A rig against a fixed view, for sampling cues with no client (architecture 7.5, "Headless sampler"). Use it as the
 * context of telemetry-core's {@code HeadlessSampler} with {@link CameraColumns}:
 *
 * <pre>{@code
 * HeadlessCameraFixture fixture = new HeadlessCameraFixture(rig, ViewInput.fixture());
 * StandardTelemetryRegistry<HeadlessCameraFixture> registry = new StandardTelemetryRegistry<>();
 * CameraColumns.register(registry, HeadlessCameraFixture::stateAt);
 * rig.start(rollCue, 0);
 * HeadlessSampler.sampleFrames(registry, fixture, fixture::step, 0, 12, 60, CsvTelemetrySink.open(csv));
 * }</pre>
 *
 * <p>Row time maps to rig time one to one, and {@link #step(long)} ticks the rig so signal layers advance.
 */
public final class HeadlessCameraFixture {

    private final StandardCameraRig rig;
    private ViewInput input;
    private double cachedTime = Double.NaN;
    private CameraState cachedState;

    public HeadlessCameraFixture(StandardCameraRig rig, ViewInput input) {
        this.rig = Objects.requireNonNull(rig, "rig");
        this.input = Objects.requireNonNull(input, "input");
    }

    public StandardCameraRig rig() {
        return rig;
    }

    /** Changes the fixture view, for example to test the pitch clamp while looking down. */
    public void input(ViewInput newInput) {
        this.input = Objects.requireNonNull(newInput, "input");
        cachedTime = Double.NaN;
    }

    /** A {@code TickStepper}: advances the rig to the end of {@code tick}. */
    public void step(long tick) {
        rig.tick();
        cachedTime = Double.NaN;
    }

    /** The state at a row's time; columns of one row share one evaluation. */
    public CameraState stateAt(FrameTime time) {
        return stateAt(time.time());
    }

    public CameraState stateAt(double time) {
        if (time != cachedTime || cachedState == null) {
            cachedState = rig.sample(input, time);
            cachedTime = time;
        }
        return cachedState;
    }
}
