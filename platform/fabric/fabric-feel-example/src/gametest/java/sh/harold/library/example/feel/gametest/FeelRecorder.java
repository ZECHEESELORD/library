package sh.harold.library.example.feel.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import sh.harold.library.movement.MoveEvent;
import sh.harold.library.movement.fabric.client.ClientMovement;
import sh.harold.library.telemetry.FrameTime;
import sh.harold.library.telemetry.TelemetryHandle;
import sh.harold.library.telemetry.core.CsvTelemetrySink;
import sh.harold.library.telemetry.core.MemoryTelemetrySink;
import sh.harold.library.telemetry.fabric.client.FabricClientTelemetry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Records one scenario with the library's client telemetry: a per-tick session and a per-frame session started on
 * the same client tick, merged into one row per tick.
 *
 * <p><b>Why two sessions.</b> A tick row is written at the end of client tick {@code n}, before that tick's frame is
 * drawn, so its camera columns still hold the frame drawn after tick {@code n - 1}. With the capture clock at 20 fps
 * every frame runs exactly one tick and draws at partial 0, and the frame drawn after tick {@code n + 1} is stamped
 * {@code (n, 0)}: it shows the rig and the interpolated body at the end of tick {@code n}. The merged row for tick
 * {@code n} therefore takes the body, input and {@code move.*} columns from tick row {@code n} and the camera
 * columns ({@link #FRAME_COLUMNS}) from frame row {@code n}, so every column describes the end of tick {@code n}.
 * The raw tick and frame CSVs are written beside the merged one for audit.
 */
final class FeelRecorder {

    /** Columns taken from the frame row: whatever the drawn camera shows. */
    static final Set<String> FRAME_COLUMNS = Set.of("roll", "fov", "camera_eye_height", "camera.pitch_add",
            "camera.roll", "camera.fov", "camera.fov_add", "camera.eye_height", "camera.look_scale", "camera.shake",
            "camera.yaw", "camera.pitch", "camera.eye_target", "camera.arm_main_x", "camera.arm_main_y",
            "camera.arm_main_z", "camera.arm_main_rot_x", "camera.arm_main_rot_y", "camera.arm_main_rot_z",
            "camera.arm_off_x", "camera.arm_off_y", "camera.arm_off_z", "camera.arm_off_rot_x", "camera.arm_off_rot_y",
            "camera.arm_off_rot_z");

    private static boolean columnsRegistered;

    private final MemoryTelemetrySink tickSink = new MemoryTelemetrySink();
    private final MemoryTelemetrySink frameSink = new MemoryTelemetrySink();
    private TelemetryHandle tickHandle;
    private TelemetryHandle frameHandle;

    private FeelRecorder() {
    }

    /**
     * Adds the test's own columns: the moves the engine started and ended on each tick, read from the newest
     * {@code MoveOutput}. A wall kick starts and ends on its own tick, so {@code move.id} never shows it.
     */
    static void registerColumns(ClientGameTestContext context) {
        context.runOnClient(client -> {
            if (columnsRegistered) {
                return;
            }
            columnsRegistered = true;
            var registry = FabricClientTelemetry.registry();
            registry.registerText("test.started", frame -> ClientMovement.current()
                    .map(movement -> movement.lastOutput().events().stream()
                            .filter(MoveEvent.Started.class::isInstance)
                            .map(event -> ((MoveEvent.Started) event).move().value())
                            .collect(Collectors.joining(" ")))
                    .orElse(""));
            registry.registerText("test.ended", frame -> ClientMovement.current()
                    .map(movement -> movement.lastOutput().events().stream()
                            .filter(MoveEvent.Ended.class::isInstance)
                            .map(event -> ((MoveEvent.Ended) event).move().value() + ":"
                                    + ((MoveEvent.Ended) event).reason().name().toLowerCase())
                            .collect(Collectors.joining(" ")))
                    .orElse(""));
            registry.registerBoolean("test.sprinting", frame -> frame.player().isSprinting());
        });
    }

    static FeelRecorder start(ClientGameTestContext context) {
        FeelRecorder recorder = new FeelRecorder();
        context.runOnClient(client -> {
            recorder.tickHandle = FabricClientTelemetry.start(recorder.tickSink, FabricClientTelemetry.RecordMode.TICK);
            recorder.frameHandle = FabricClientTelemetry.start(recorder.frameSink,
                    FabricClientTelemetry.RecordMode.FRAME);
        });
        return recorder;
    }

    /** Stops both sessions (after one more tick, so the last tick's frame row exists) and writes the CSVs. */
    Table stop(ClientGameTestContext context, Path dir, String name) {
        context.waitTick();
        context.runOnClient(client -> {
            tickHandle.close();
            frameHandle.close();
        });
        Table merged = merge();
        try {
            Files.createDirectories(dir);
            try (Writer writer = Files.newBufferedWriter(dir.resolve(name + ".tick.csv"))) {
                tickSink.writeCsv(writer);
            }
            try (Writer writer = Files.newBufferedWriter(dir.resolve(name + ".frame.csv"))) {
                frameSink.writeCsv(writer);
            }
            try (Writer writer = Files.newBufferedWriter(dir.resolve(name + ".csv"))) {
                merged.writeCsv(writer);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return merged.named(name);
    }

    private Table merge() {
        List<String> columns = tickSink.columns();
        List<String> frameColumns = frameSink.columns();
        Map<Long, Object[]> frames = new HashMap<>();
        for (MemoryTelemetrySink.Row row : frameSink.rows()) {
            if (row.time().partial() == 0.0) {
                // Last wins: a screenshot re-render for the same tick comes before the regular frame.
                frames.put(row.time().tick(), row.values());
            }
        }
        int[] fromFrame = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            fromFrame[i] = FRAME_COLUMNS.contains(columns.get(i)) ? frameColumns.indexOf(columns.get(i)) : -1;
        }
        List<Long> ticks = new ArrayList<>();
        List<Object[]> rows = new ArrayList<>();
        for (MemoryTelemetrySink.Row row : tickSink.rows()) {
            long tick = row.time().tick();
            Object[] frame = frames.get(tick);
            Object[] values = row.values().clone();
            for (int i = 0; i < values.length; i++) {
                if (fromFrame[i] >= 0) {
                    values[i] = frame == null ? Double.NaN : frame[fromFrame[i]];
                }
            }
            ticks.add(tick);
            rows.add(values);
        }
        return new Table("", columns, ticks, rows);
    }

    /** One merged row per tick. Row index {@code i} is the {@code i}-th recorded tick. */
    record Table(String name, List<String> columns, List<Long> ticks, List<Object[]> rows) {

        Table named(String value) {
            return new Table(value, columns, ticks, rows);
        }

        int size() {
            return rows.size();
        }

        long tick(int row) {
            return ticks.get(row);
        }

        Object value(int row, String column) {
            int index = columns.indexOf(column);
            if (index < 0) {
                throw new IllegalArgumentException("no column " + column + " in " + columns);
            }
            return rows.get(row)[index];
        }

        double d(int row, String column) {
            Object value = value(row, column);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value instanceof Boolean bool) {
                return bool ? 1.0 : 0.0;
            }
            return Double.NaN;
        }

        String s(int row, String column) {
            Object value = value(row, column);
            return value == null ? "" : value.toString();
        }

        boolean b(int row, String column) {
            return Boolean.TRUE.equals(value(row, column));
        }

        /** The first row at or after {@code from} whose {@code test.started} names {@code move}, or -1. */
        int started(String move, int from) {
            for (int i = Math.max(0, from); i < size(); i++) {
                if (List.of(s(i, "test.started").split(" ")).contains(move)) {
                    return i;
                }
            }
            return -1;
        }

        List<Integer> allStarted(String move) {
            List<Integer> found = new ArrayList<>();
            for (int i = 0; i < size(); i++) {
                if (List.of(s(i, "test.started").split(" ")).contains(move)) {
                    found.add(i);
                }
            }
            return found;
        }

        /** A reference to one row for the report: {@code name.csv tick=N}. */
        String ref(int row) {
            if (row < 0 || row >= size()) {
                return name + ".csv (no such row)";
            }
            return name + ".csv tick=" + tick(row);
        }

        void writeCsv(Writer writer) {
            CsvTelemetrySink csv = new CsvTelemetrySink(writer);
            csv.header(columns);
            for (int i = 0; i < rows.size(); i++) {
                csv.row(FrameTime.ofTick(ticks.get(i)), rows.get(i));
            }
            csv.flush();
        }
    }
}
