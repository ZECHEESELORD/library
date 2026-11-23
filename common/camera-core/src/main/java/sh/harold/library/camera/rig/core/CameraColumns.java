package sh.harold.library.camera.rig.core;

import sh.harold.library.camera.rig.CameraState;
import sh.harold.library.telemetry.ColumnHandle;
import sh.harold.library.telemetry.FrameTime;
import sh.harold.library.telemetry.TelemetryRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.ToDoubleFunction;

/**
 * The camera's telemetry columns, shared by the Fabric client and headless sampling so both write the same CSV:
 *
 * <ul>
 *     <li>{@code camera.pitch_add}: pitch added by the layers, unwound (shake excluded)</li>
 *     <li>{@code camera.roll}: final roll, shake included</li>
 *     <li>{@code camera.fov}: final FOV</li>
 *     <li>{@code camera.fov_add}: FOV added by the layers</li>
 *     <li>{@code camera.eye_height}: eye height added by the layers</li>
 *     <li>{@code camera.look_scale}: look-input scale</li>
 *     <li>{@code camera.shake}: rotational shake amplitude in degrees</li>
 *     <li>{@code camera.yaw}, {@code camera.pitch}: final view angles, pitch unwound and shake included</li>
 *     <li>{@code camera.eye_target}: the absolute eye height a live absolute curve holds, {@code NaN} when none</li>
 *     <li>{@code camera.arm_main_x} to {@code camera.arm_main_rot_z} and {@code camera.arm_off_x} to
 *     {@code camera.arm_off_rot_z}: the first-person arm pose of each hand (blocks, then degrees; see
 *     {@link sh.harold.library.camera.rig.ArmPose})</li>
 * </ul>
 *
 * <p>A row with no state yet writes {@code NaN}.
 */
public final class CameraColumns {

    public static final List<String> NAMES = List.of("camera.pitch_add", "camera.roll", "camera.fov",
            "camera.fov_add", "camera.eye_height", "camera.look_scale", "camera.shake", "camera.yaw", "camera.pitch",
            "camera.eye_target", "camera.arm_main_x", "camera.arm_main_y", "camera.arm_main_z",
            "camera.arm_main_rot_x", "camera.arm_main_rot_y", "camera.arm_main_rot_z", "camera.arm_off_x",
            "camera.arm_off_y", "camera.arm_off_z", "camera.arm_off_rot_x", "camera.arm_off_rot_y",
            "camera.arm_off_rot_z");

    private CameraColumns() {
    }

    /** Registers the columns, reading the state for each row from {@code state}. Returns the handles in order. */
    public static <C> List<ColumnHandle> register(TelemetryRegistry<C> registry,
                                                  BiFunction<? super C, FrameTime, CameraState> state) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(state, "state");
        List<ColumnHandle> handles = new ArrayList<>();
        handles.add(column(registry, state, "camera.pitch_add", s -> s.layers().pitchAdd()));
        handles.add(column(registry, state, "camera.roll", s -> s.angles().roll()));
        handles.add(column(registry, state, "camera.fov", CameraState::fovDegrees));
        handles.add(column(registry, state, "camera.fov_add", s -> s.layers().fovAdd()));
        handles.add(column(registry, state, "camera.eye_height", s -> s.layers().eyeHeight()));
        handles.add(column(registry, state, "camera.look_scale", CameraState::lookInputScale));
        handles.add(column(registry, state, "camera.shake", s -> s.layers().shakeAmplitude()));
        handles.add(column(registry, state, "camera.yaw", s -> s.angles().yaw()));
        handles.add(column(registry, state, "camera.pitch", s -> s.angles().pitch()));
        handles.add(column(registry, state, "camera.eye_target", s -> s.layers().eyeTarget()));
        for (int i = 0; i < 12; i++) {
            int axis = i % 6;
            boolean main = i < 6;
            handles.add(column(registry, state, NAMES.get(10 + i),
                    s -> (main ? s.layers().mainArm() : s.layers().offArm()).get(axis)));
        }
        return List.copyOf(handles);
    }

    private static <C> ColumnHandle column(TelemetryRegistry<C> registry,
                                           BiFunction<? super C, FrameTime, CameraState> state, String name,
                                           ToDoubleFunction<CameraState> reader) {
        return registry.registerTimedDouble(name, (context, time) -> {
            CameraState current = state.apply(context, time);
            return current == null ? Double.NaN : reader.applyAsDouble(current);
        });
    }
}
