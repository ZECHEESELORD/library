package sh.harold.library.telemetry.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import sh.harold.library.telemetry.FrameTime;
import sh.harold.library.telemetry.TelemetryHandle;
import sh.harold.library.telemetry.TelemetryRecorder;
import sh.harold.library.telemetry.TelemetryRegistry;
import sh.harold.library.telemetry.TelemetrySink;
import sh.harold.library.telemetry.core.CsvTelemetrySink;
import sh.harold.library.telemetry.core.StandardTelemetryRegistry;
import sh.harold.library.telemetry.core.TelemetryRecording;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server-side per-tick telemetry for one player. Each recording writes one row per server tick, after the tick,
 * stamped with {@code server.getTickCount()}. Recordings stop when their handle closes, when the player
 * disconnects, or when the server stops.
 *
 * <p>Base columns: {@code pos_x/y/z}, {@code vel_x/y/z} (the server's delta movement), {@code yaw},
 * {@code pitch}, {@code eye_height}, {@code on_ground} and {@code input_*} (the last client input packet).
 * Features add columns through {@link #registry()}; a recording snapshots the columns when it starts.
 *
 * <p>The Fabric events are registered once, on first use, and do nothing while no recording is open.
 */
public final class FabricServerTelemetry {

    private static final StandardTelemetryRegistry<ServerPlayer> REGISTRY = new StandardTelemetryRegistry<>();
    private static final List<Session> SESSIONS = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean EVENTS = new AtomicBoolean();

    static {
        REGISTRY.registerDouble("pos_x", player -> player.position().x);
        REGISTRY.registerDouble("pos_y", player -> player.position().y);
        REGISTRY.registerDouble("pos_z", player -> player.position().z);
        REGISTRY.registerDouble("vel_x", player -> player.getDeltaMovement().x);
        REGISTRY.registerDouble("vel_y", player -> player.getDeltaMovement().y);
        REGISTRY.registerDouble("vel_z", player -> player.getDeltaMovement().z);
        REGISTRY.registerDouble("yaw", ServerPlayer::getYRot);
        REGISTRY.registerDouble("pitch", ServerPlayer::getXRot);
        REGISTRY.registerDouble("eye_height", ServerPlayer::getEyeHeight);
        REGISTRY.registerBoolean("on_ground", ServerPlayer::onGround);
        InputColumns.register(REGISTRY, ServerPlayer::getLastClientInput);
    }

    private FabricServerTelemetry() {
    }

    /** The server column registry. Columns registered here appear in recordings started afterwards. */
    public static TelemetryRegistry<ServerPlayer> registry() {
        return REGISTRY;
    }

    /** Starts recording {@code player} to a CSV file. Call on the server thread. */
    public static TelemetryHandle start(ServerPlayer player, Path csv) {
        return start(player, CsvTelemetrySink.open(csv));
    }

    /** Starts recording {@code player} to {@code sink}; the recording owns and closes the sink. */
    public static TelemetryHandle start(ServerPlayer player, TelemetrySink sink) {
        Objects.requireNonNull(player, "player");
        installEvents();
        Session session = new Session(player.getUUID(), TelemetryRecording.start(REGISTRY, sink));
        SESSIONS.add(session);
        return session;
    }

    private static void installEvents() {
        if (!EVENTS.compareAndSet(false, true)) {
            return;
        }
        ServerTickEvents.END_SERVER_TICK.register(FabricServerTelemetry::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> closeFor(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> SESSIONS.forEach(Session::close));
    }

    private static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        FrameTime time = FrameTime.ofTick(server.getTickCount());
        for (Session session : SESSIONS) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.player);
            if (player != null) {
                session.recorder.record(player, time);
            }
        }
    }

    private static void closeFor(UUID player) {
        for (Session session : SESSIONS) {
            if (session.player.equals(player)) {
                session.close();
            }
        }
    }

    private static final class Session implements TelemetryHandle {

        private final UUID player;
        private final TelemetryRecorder<ServerPlayer> recorder;

        private Session(UUID player, TelemetryRecorder<ServerPlayer> recorder) {
            this.player = player;
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
