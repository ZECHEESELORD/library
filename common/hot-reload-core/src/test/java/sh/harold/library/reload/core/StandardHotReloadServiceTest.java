package sh.harold.library.reload.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.harold.library.reload.DataDecodeException;
import sh.harold.library.reload.DataDecoder;
import sh.harold.library.reload.ReloadError;
import sh.harold.library.reload.ReloadableData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardHotReloadServiceTest {

    private record Tuning(double fov, int ticks) {
    }

    private static final DataDecoder<Tuning> TUNING = root -> {
        var object = root.asObject();
        return new Tuning(object.get("fov").asDouble(), object.get("ticks").asInt());
    };

    @TempDir
    Path dir;

    private Path write(String name, String text) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, text);
        return file;
    }

    @Test
    void editsAreStagedOnPollAndSwappedInAtTheTickBoundary() throws IOException {
        Path file = write("roll.json", "{ \"fov\": 6, \"ticks\": 10 }");
        List<Tuning> applied = new ArrayList<>();
        try (StandardHotReloadService service = new StandardHotReloadService()) {
            ReloadableData<Tuning> data = service.watch(file, TUNING, applied::add);
            assertEquals(new Tuning(6, 10), data.current());
            assertEquals(1, data.version());
            assertEquals(List.of(new Tuning(6, 10)), applied);

            assertEquals(0, service.poll(), "unchanged file stages nothing");
            Files.writeString(file, "{ \"fov\": 8, \"ticks\": 12 }");
            assertEquals(1, service.poll());
            // Staged, not applied: game code reading mid-tick still sees the old value.
            assertEquals(new Tuning(6, 10), data.current());
            assertEquals(1, service.applyPending());
            assertEquals(new Tuning(8, 12), data.current());
            assertEquals(2, data.version());
            assertEquals(2, applied.size());
            assertEquals(0, service.applyPending());
        }
    }

    @Test
    void aBadEditKeepsTheLastGoodValueAndReportsFileKeyAndField() throws IOException {
        Path file = write("roll.json", "{ \"fov\": 6, \"ticks\": 10 }");
        List<ReloadError> errors = new ArrayList<>();
        try (StandardHotReloadService service = new StandardHotReloadService(errors::add)) {
            ReloadableData<Tuning> data = service.watch(file, TUNING, tuning -> { });
            Files.writeString(file, "{\n  \"fov\": \"six\",\n  \"ticks\": 10\n}");
            assertEquals(1, service.poll());
            assertEquals(1, errors.size());
            ReloadError error = errors.getFirst();
            assertEquals(file, error.file());
            assertEquals("$.fov", error.path());
            assertEquals(2, error.line());
            assertTrue(error.describe().startsWith("roll.json: $.fov (line 2): expected a number"), error.describe());

            assertEquals(0, service.applyPending());
            assertEquals(new Tuning(6, 10), data.current());
            assertEquals(error, data.lastError().orElseThrow());

            Files.writeString(file, "{ \"fov\": 7, \"ticks\": 10 }");
            service.poll();
            service.applyPending();
            assertEquals(new Tuning(7, 10), data.current());
            assertTrue(data.lastError().isEmpty());
        }
    }

    @Test
    void syntaxErrorsAndMissingFilesAreReportedNotThrown() throws IOException {
        Path file = write("broken.json", "{ \"fov\": 6 ");
        List<ReloadError> errors = new ArrayList<>();
        try (StandardHotReloadService service = new StandardHotReloadService(errors::add)) {
            ReloadableData<Tuning> data = service.watch(file, TUNING, new Tuning(1, 1), tuning -> { });
            assertEquals(new Tuning(1, 1), data.current());
            assertEquals(1, errors.size());
            assertTrue(data.lastError().isPresent());

            assertThrows(DataDecodeException.class,
                    () -> service.watch(dir.resolve("absent.json"), TUNING, tuning -> { }));
        }
    }

    @Test
    void closingStopsWatching() throws IOException {
        Path file = write("roll.json", "{ \"fov\": 6, \"ticks\": 10 }");
        StandardHotReloadService service = new StandardHotReloadService();
        ReloadableData<Tuning> data = service.watch(file, TUNING, tuning -> { });
        data.close();
        assertFalse(data.active());
        Files.writeString(file, "{ \"fov\": 9, \"ticks\": 10 }");
        assertEquals(0, service.poll());
        service.close();
        assertThrows(IllegalStateException.class, () -> service.watch(file, TUNING, tuning -> { }));
    }
}
