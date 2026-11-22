package sh.harold.library.camera.rig.core;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import sh.harold.library.camera.rig.CameraChannel;
import sh.harold.library.camera.rig.ViewInput;
import sh.harold.library.camera.rig.cue.CameraCue;
import sh.harold.library.camera.rig.cue.CueCurve;
import sh.harold.library.telemetry.FrameTime;
import sh.harold.library.telemetry.core.HeadlessSampler;
import sh.harold.library.telemetry.core.MemoryTelemetrySink;
import sh.harold.library.telemetry.core.StandardTelemetryRegistry;
import sh.harold.library.tween.Easing;
import sh.harold.library.tween.track.Track;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadlessCameraSamplingTest {

    @Test
    void cuesSampleThroughTelemetryWithNoClient() {
        StandardCameraRig rig = new StandardCameraRig();
        HeadlessCameraFixture fixture = new HeadlessCameraFixture(rig, ViewInput.fixture());
        StandardTelemetryRegistry<HeadlessCameraFixture> registry = new StandardTelemetryRegistry<>();
        CameraColumns.register(registry, HeadlessCameraFixture::stateAt);
        Track<Double> ramp = Track.scalar().eased(0, 0.0, Easing.LINEAR).hermite(6, 12.0).build();
        rig.start(CameraCue.builder(Key.key("test", "ramp"))
                .channel(CameraChannel.FOV, CueCurve.track(ramp))
                .lookScale(0.5, 6)
                .build(), 0.0);

        MemoryTelemetrySink sink = new MemoryTelemetrySink();
        long rows = HeadlessSampler.sampleFrames(registry, fixture, fixture::step, 0, 12, 60, sink);

        assertEquals(37, rows);
        assertEquals(CameraColumns.NAMES, sink.columns().subList(sink.columns().size() - CameraColumns.NAMES.size(),
                sink.columns().size()));
        int fovAdd = sink.columns().indexOf("camera.fov_add");
        int fov = sink.columns().indexOf("camera.fov");
        int look = sink.columns().indexOf("camera.look_scale");
        List<MemoryTelemetrySink.Row> written = sink.rows();
        for (MemoryTelemetrySink.Row row : written) {
            FrameTime time = row.time();
            double expected = time.time() <= 6.0 ? 2.0 * time.time() : 0.0;
            assertEquals(expected, ((Number) row.value(fovAdd)).doubleValue(), 1e-9, "at " + time);
            assertEquals(70.0 + expected, ((Number) row.value(fov)).doubleValue(), 1e-9);
        }
        assertEquals(0.5, ((Number) written.get(4).value(look)).doubleValue(), 1e-9);
        assertEquals(1.0, ((Number) written.getLast().value(look)).doubleValue(), 1e-9);
        assertTrue(rig.ticks() == 12);
    }
}
