package sh.harold.creative.library.data.yaml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.harold.creative.library.data.Document;
import sh.harold.creative.library.data.DocumentPatch;
import sh.harold.creative.library.data.DocumentSnapshot;
import sh.harold.creative.library.data.WriteCondition;
import sh.harold.creative.library.data.WriteResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlDataApiTest {

    @TempDir
    Path tempDir;

    @Test
    void yamlPersistsAcrossRestart() throws Exception {
        Path root = tempDir.resolve("yaml-store");
        try (YamlDataApi api = new YamlDataApi(root)) {
            Document document = api.namespace("plugin-a").collection("players").document("alpha");
            await(document.write(Map.of("name", "Alice", "profile", Map.of("rank", "ADMIN"))));
        }

        try (YamlDataApi api = new YamlDataApi(root)) {
            DocumentSnapshot snapshot = await(api.namespace("plugin-a").collection("players").document("alpha").read());
            assertTrue(snapshot.exists());
            assertEquals(Optional.of("Alice"), snapshot.get("name", String.class));
            assertEquals(Optional.of("ADMIN"), snapshot.get("profile.rank", String.class));
            assertEquals(1L, await(api.namespace("plugin-a").collection("players").count()));
        }
    }

    @Test
    void yamlConditionsAndDeleteBehaveCorrectly() throws Exception {
        try (YamlDataApi api = new YamlDataApi(tempDir.resolve("yaml-store"))) {
            Document document = api.namespace("plugin-a").collection("players").document("alpha");

            WriteResult created = await(document.write(Map.of("name", "Alice"), WriteCondition.notExists()));
            WriteResult patched = await(document.patch(
                    new DocumentPatch().set("profile.rank", "ADMIN"),
                    WriteCondition.revision(created.snapshot().orElseThrow().revision())
            ));
            WriteResult deleted = await(document.delete(WriteCondition.exists()));

            assertTrue(created.applied());
            assertTrue(patched.applied());
            assertTrue(deleted.applied());
            assertFalse(deleted.snapshot().orElseThrow().exists());
            assertEquals(0L, await(api.namespace("plugin-a").collection("players").count()));
        }
    }

    @Test
    void yamlValidatesRootDirectoryEagerly() throws Exception {
        Path file = tempDir.resolve("not-a-directory");
        Files.writeString(file, "x");

        assertThrows(IllegalArgumentException.class, () -> new YamlDataApi(file));
    }

    private static <T> T await(CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
}
