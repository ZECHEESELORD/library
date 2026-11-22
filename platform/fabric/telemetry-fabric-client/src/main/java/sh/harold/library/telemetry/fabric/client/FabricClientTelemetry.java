package sh.harold.library.telemetry.fabric.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Vector3fc;
import sh.harold.library.telemetry.FrameTime;
import sh.harold.library.telemetry.TelemetryHandle;
import sh.harold.library.telemetry.TelemetryRecorder;
import sh.harold.library.telemetry.TelemetryRegistry;
import sh.harold.library.telemetry.TelemetrySink;
import sh.harold.library.telemetry.core.CsvTelemetrySink;
import sh.harold.library.telemetry.core.StandardTelemetryRegistry;
import sh.harold.library.telemetry.core.TelemetryRecording;
import sh.harold.library.telemetry.fabric.InputColumns;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client-side telemetry for the local player, per client tick or per rendered frame.
 *
 * <p>Time is the client tick count since the recording started. A {@link RecordMode#TICK} row is written after
 * client tick {@code n} at time {@code (n, 0)}. A {@link RecordMode#FRAME} row is written after each frame's
 * level extraction at time {@code (n - 1, partial)}, because a frame drawn after tick {@code n} interpolates
 * between ticks {@code n - 1} and {@code n}. Both modes therefore share one timeline.
 *
 * <p>Base columns: {@code pos_x/y/z} (interpolated), {@code vel_x/y/z}, {@code yaw}, {@code pitch} (view
 * angles, interpolated), {@code roll} (degrees, from the camera, positive when the view's left side is raised),
 * {@code fov} (degrees, the camera's), {@code eye_height}, {@code camera_eye_height} (camera height above the
 * interpolated feet), {@code on_ground} and {@code input_*} (keys held this tick).
 *
 * <p>Recordings stop when their handle closes, on disconnect and when the client stops. The Fabric events are
 * registered once, on first use, and do nothing while no recording is open. Use from the client thread.
 */
public final class FabricClientTelemetry {

    /** When a recording writes rows. */
    public enum RecordMode {
        TICK,
        FRAME
    }

    private static final StandardTelemetryRegistry<ClientFrame> REGISTRY = new StandardTelemetryRegistry<>();
    private static final List<Session> SESSIONS = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean EVENTS = new AtomicBoolean();

    static {
        REGISTRY.registerDouble("pos_x", frame -> frame.player().getPosition(frame.partial()).x);
        REGISTRY.registerDouble("pos_y", frame -> frame.player().getPosition(frame.partial()).y);
        REGISTRY.registerDouble("pos_z", frame -> frame.player().getPosition(frame.partial()).z);
        REGISTRY.registerDouble("vel_x", frame -> frame.player().getDeltaMovement().x);
        REGISTRY.registerDouble("vel_y", frame -> frame.player().getDeltaMovement().y);
        REGISTRY.registerDouble("vel_z", frame -> frame.player().getDeltaMovement().z);
        REGISTRY.registerDouble("yaw", frame -> frame.player().getViewYRot(frame.partial()));
        REGISTRY.registerDouble("pitch", frame -> frame.player().getViewXRot(frame.partial()));
        REGISTRY.registerDouble("roll", frame -> roll(frame.camera()));
        REGISTRY.registerDouble("fov", frame -> frame.camera().getFov());
        REGISTRY.registerDouble("eye_height", frame -> frame.player().getEyeHeight());
        REGISTRY.registerDouble("camera_eye_height",
                frame -> frame.camera().position().y - frame.player().getPosition(frame.partial()).y);
        REGISTRY.registerBoolean("on_ground", frame -> frame.player().onGround());
        InputColumns.register(REGISTRY, frame -> frame.player().input.keyPresses);
    }

    private FabricClientTelemetry() {
    }

    /** The client column registry. Columns registered here appear in recordings started afterwards. */
    public static TelemetryRegistry<ClientFrame> registry() {
        return REGISTRY;
    }

    public static TelemetryHandle start(Path csv, RecordMode mode) {
        return start(CsvTelemetrySink.open(csv), mode);
    }

    /** Starts a recording; it owns and closes the sink. Rows are written only while a player and level exist. */
    public static TelemetryHandle start(TelemetrySink sink, RecordMode mode) {
        Objects.requireNonNull(mode, "mode");
        installEvents();
        Session session = new Session(mode, TelemetryRecording.start(REGISTRY, sink));
        SESSIONS.add(session);
        return session;
    }

    /** Camera roll in degrees: the angle of the camera's left vector out of the horizontal plane. */
    static double roll(Camera camera) {
        Vector3fc left = camera.leftVector();
        Vector3fc up = camera.upVector();
        return Math.toDegrees(Math.atan2(left.y(), up.y()));
    }

    private static void installEvents() {
        if (!EVENTS.compareAndSet(false, true)) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(FabricClientTelemetry::afterTick);
        LevelExtractionEvents.END_EXTRACTION.register(context -> afterFrame(
                context.deltaTracker().getGameTimeDeltaPartialTick(true)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(FabricClientTelemetry::closeAll));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> closeAll());
    }

    private static void afterTick(Minecraft client) {
        if (SESSIONS.isEmpty() || client.player == null || client.level == null) {
            return;
        }
        ClientFrame frame = new ClientFrame(client, client.player, client.gameRenderer.mainCamera(), 1.0f);
        for (Session session : SESSIONS) {
            session.tick++;
            if (session.mode == RecordMode.TICK) {
                session.recorder.record(frame, FrameTime.ofTick(session.tick));
            }
        }
    }

    private static void afterFrame(float partial) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return;
        }
        ClientFrame frame = new ClientFrame(client, player, client.gameRenderer.mainCamera(), partial);
        for (Session session : SESSIONS) {
            if (session.mode != RecordMode.FRAME || session.tick == 0) {
                continue;
            }
            FrameTime time = partial >= 1.0f
                    ? FrameTime.ofTick(session.tick)
                    : new FrameTime(session.tick - 1, Math.max(0.0, partial));
            session.recorder.record(frame, time);
        }
    }

    private static void closeAll() {
        SESSIONS.forEach(Session::close);
    }

    private static final class Session implements TelemetryHandle {

        private final RecordMode mode;
        private final TelemetryRecorder<ClientFrame> recorder;
        private long tick;

        private Session(RecordMode mode, TelemetryRecorder<ClientFrame> recorder) {
            this.mode = mode;
            this.recorder = recorder;
        }

        @Override
        public boolean active() {
            return recorder.active();
        }

        @Override
        public long rows() {
            return recorder.rows();
        }

        @Override
        public void close() {
            SESSIONS.remove(this);
            recorder.close();
        }
    }
}
