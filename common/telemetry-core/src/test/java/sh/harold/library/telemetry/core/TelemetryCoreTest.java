package sh.harold.library.telemetry.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.harold.library.telemetry.ColumnHandle;
import sh.harold.library.telemetry.FrameTime;
import sh.harold.library.telemetry.TelemetryRegistry;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryCoreTest {

    /** A tiny host-free fixture: a body falling under vanilla gravity with drag, sampled per frame. */
    static final class Body {
        double y = 10.0;
        double vy = 0.0;
        long ticks;

        void step() {
            vy = (vy - 0.08) * 0.98;
            y += vy;
            ticks++;
        }

        double yAt(double partial) {
            double nextVy = (vy - 0.08) * 0.98;
            return y + (nextVy * partial);
        }
    }

    @Test
    void registryRejectsDuplicateAndReservedNamesAndClosesColumns() {
        TelemetryRegistry<Body> registry = new StandardTelemetryRegistry<>();
        ColumnHandle y = registry.registerDouble("pos_y", body -> body.y);
        assertThrows(IllegalStateException.class, () -> registry.registerDouble("pos_y", body -> 0.0));
        assertThrows(IllegalArgumentException.class, () -> registry.registerDouble("tick", body -> 0.0));
        assertThrows(IllegalArgumentException.class, () -> registry.registerDouble("Pos Y", body -> 0.0));
        assertTrue(y.active());
        y.close();
        y.close();
        assertFalse(y.active());
        assertTrue(registry.columns().isEmpty());
    }

    @Test
    void recordingSnapshotsColumnsAndWritesCsv() {
        TelemetryRegistry<Body> registry = new StandardTelemetryRegistry<>();
        registry.registerDouble("pos_y", body -> body.y);
        registry.registerLong("move_tick", body -> body.ticks);
        registry.registerBoolean("falling", body -> body.vy < 0.0);
        registry.registerText("move", body -> "roll, then \"slide\"");
        StringWriter out = new StringWriter();
        Body body = new Body();
        TelemetryRecording<Body> recording = TelemetryRecording.start(registry, new CsvTelemetrySink(out));
        registry.registerDouble("late", b -> 1.0);
        recording.record(body, FrameTime.ofTick(0));
        body.step();
        recording.record(body, FrameTime.ofTick(1));
        recording.close();
        recording.record(body, FrameTime.ofTick(2));

        assertEquals(List.of("pos_y", "move_tick", "falling", "move"), recording.columnNames());
        assertEquals(2, recording.rows());
        String[] lines = out.toString().split("\n");
        assertEquals("tick,partial,pos_y,move_tick,falling,move", lines[0]);
        assertEquals("0,0,10,0,false,\"roll, then \"\"slide\"\"\"", lines[1]);
        assertEquals("1,0,9.9216,1,true,\"roll, then \"\"slide\"\"\"", lines[2]);
        assertEquals(3, lines.length);
    }

    @Test
    void badReadersWriteEmptyCells() {
        TelemetryRegistry<Body> registry = new StandardTelemetryRegistry<>();
        registry.registerDouble("boom", body -> {
            throw new IllegalStateException("not ready");
        });
        registry.register(new sh.harold.library.telemetry.TelemetryColumn<>("wrong",
                sh.harold.library.telemetry.ColumnType.LONG, (b, t) -> "text"));
        MemoryTelemetrySink sink = new MemoryTelemetrySink();
        TelemetryRecording<Body> recording = TelemetryRecording.start(registry, sink);
        recording.record(new Body(), FrameTime.ofTick(3));
        assertEquals(null, sink.rows().getFirst().value(0));
        assertEquals(null, sink.rows().getFirst().value(1));
    }

    @Test
    void headlessFrameSamplerSteppsOncePerTickAndSamplesPartials() {
        TelemetryRegistry<Body> registry = new StandardTelemetryRegistry<>();
        registry.registerTimedDouble("pos_y", (body, time) -> body.yAt(time.partial()));
        registry.registerLong("ticks_stepped", body -> body.ticks);
        Body body = new Body();
        MemoryTelemetrySink sink = new MemoryTelemetrySink();

        long rows = HeadlessSampler.sampleFrames(registry, body, tick -> body.step(), 0L, 2L, 60, sink);

        assertEquals(7, rows);
        List<MemoryTelemetrySink.Row> written = sink.rows();
        assertEquals(new FrameTime(0, 0.0), written.get(0).time());
        assertEquals(new FrameTime(0, 1.0 / 3.0), written.get(1).time());
        assertEquals(new FrameTime(1, 0.0), written.get(3).time());
        assertEquals(new FrameTime(2, 0.0), written.get(6).time());
        assertEquals(0L, written.get(2).value(1));
        assertEquals(1L, written.get(3).value(1));
        assertEquals(2L, written.get(6).value(1));
    }

    @Test
    void headlessTickRowsMatchFrameRowsOnWholeTicks() {
        TelemetryRegistry<Body> registry = new StandardTelemetryRegistry<>();
        registry.registerTimedDouble("pos_y", (body, time) -> body.yAt(time.partial()));
        MemoryTelemetrySink frames = new MemoryTelemetrySink();
        MemoryTelemetrySink ticks = new MemoryTelemetrySink();
        Body a = new Body();
        Body b = new Body();
        HeadlessSampler.sampleFrames(registry, a, tick -> a.step(), 0L, 20L, 60, frames);
        HeadlessSampler.sampleTicks(registry, b, tick -> b.step(), 0L, 20L, ticks);

        List<MemoryTelemetrySink.Row> tickRows = ticks.rows();
        assertEquals(21, tickRows.size());
        int matched = 0;
        for (MemoryTelemetrySink.Row frame : frames.rows()) {
            if (frame.time().partial() == 0.0) {
                MemoryTelemetrySink.Row tick = tickRows.get((int) frame.time().tick());
                assertEquals(tick.value(0), frame.value(0));
                matched++;
            }
        }
        assertEquals(21, matched);
    }

    @Test
    void csvFileSinkAndRingBufferWriteTheSameFormat(@TempDir Path dir) throws Exception {
        TelemetryRegistry<Body> registry = new StandardTelemetryRegistry<>();
        registry.registerDouble("pos_y", body -> body.y);
        Body body = new Body();
        Path file = dir.resolve("runs/fall.csv");
        MemoryTelemetrySink ring = new MemoryTelemetrySink(3);
        HeadlessSampler.sampleTicks(registry, body, tick -> body.step(), 0L, 5L, CsvTelemetrySink.open(file));
        Body again = new Body();
        HeadlessSampler.sampleTicks(registry, again, tick -> again.step(), 0L, 5L, ring);

        List<String> fileLines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(7, fileLines.size());
        StringWriter ringCsv = new StringWriter();
        ring.writeCsv(ringCsv);
        String[] ringLines = ringCsv.toString().split("\n");
        assertEquals(4, ringLines.length);
        assertEquals(fileLines.get(0), ringLines[0]);
        assertEquals(fileLines.get(6), ringLines[3]);
        assertEquals("", CsvTelemetrySink.formatDouble(Double.NaN));
        assertEquals("-0.5", CsvTelemetrySink.formatDouble(-0.5));
        assertEquals("0", CsvTelemetrySink.formatDouble(-0.0000001));
    }
}
