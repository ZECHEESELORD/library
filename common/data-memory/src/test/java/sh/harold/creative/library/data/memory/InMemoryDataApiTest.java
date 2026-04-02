package sh.harold.creative.library.data.memory;

import org.junit.jupiter.api.Test;
import sh.harold.creative.library.data.DataNamespace;
import sh.harold.creative.library.data.Document;
import sh.harold.creative.library.data.DocumentCollection;
import sh.harold.creative.library.data.DocumentPatch;
import sh.harold.creative.library.data.DocumentSnapshot;
import sh.harold.creative.library.data.WriteCondition;
import sh.harold.creative.library.data.WriteResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryDataApiTest {

    @Test
    void writeCreatesAndReplacesDocuments() throws Exception {
        try (InMemoryDataApi api = new InMemoryDataApi()) {
            Document alpha = api.namespace("plugin-a").collection("players").document("alpha");

            WriteResult created = await(alpha.write(Map.of("name", "Alice")));
            WriteResult replaced = await(alpha.write(Map.of("name", "Bob")));

            assertTrue(created.applied());
            assertTrue(replaced.applied());
            assertEquals(Optional.of("Bob"), await(alpha.read()).get("name", String.class));
            assertEquals(1L, await(api.namespace("plugin-a").collection("players").count()));
        }
    }

    @Test
    void missingDocumentsStayStableAndUseSnapshotsForReads() throws Exception {
        try (InMemoryDataApi api = new InMemoryDataApi()) {
            Document missing = api.namespace("plugin-a").collection("players").document("missing");

            DocumentSnapshot before = await(missing.read());
            WriteResult patched = await(missing.patch(new DocumentPatch().remove("legacy.name")));
            DocumentSnapshot after = await(missing.read());

            assertFalse(before.exists());
            assertTrue(patched.applied());
            assertFalse(after.exists());
            assertTrue(after.data().isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> after.data().put("x", "y"));
        }
    }

    @Test
    void snapshotsAreDeeplyImmutable() throws Exception {
        try (InMemoryDataApi api = new InMemoryDataApi()) {
            Document alpha = api.namespace("plugin-a").collection("players").document("alpha");
            await(alpha.write(Map.of(
                    "profile", Map.of("rank", "ADMIN"),
                    "tags", List.of("staff", "builder")
            )));

            DocumentSnapshot snapshot = await(alpha.read());

            assertThrows(UnsupportedOperationException.class, () -> snapshot.data().put("name", "Bob"));

            @SuppressWarnings("unchecked")
            Map<String, Object> profile = (Map<String, Object>) snapshot.data().get("profile");
            assertThrows(UnsupportedOperationException.class, () -> profile.put("rank", "MEMBER"));

            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) snapshot.data().get("tags");
            assertThrows(UnsupportedOperationException.class, () -> tags.add("vip"));
        }
    }

    @Test
    void writeAndPatchDefensivelyCopyMutableInputsAndPreserveNull() throws Exception {
        try (InMemoryDataApi api = new InMemoryDataApi()) {
            Document alpha = api.namespace("plugin-a").collection("players").document("alpha");

            Map<String, Object> mutableRoot = new LinkedHashMap<>();
            Map<String, Object> mutableProfile = new LinkedHashMap<>();
            mutableProfile.put("rank", "ADMIN");
            mutableRoot.put("profile", mutableProfile);
            List<String> mutableTags = new ArrayList<>(List.of("staff"));
            mutableRoot.put("tags", mutableTags);

            await(alpha.write(mutableRoot));

            mutableProfile.put("rank", "MEMBER");
            mutableTags.add("vip");

            assertEquals(Optional.of("ADMIN"), await(alpha.read()).get("profile.rank", String.class));

            await(alpha.patch(new DocumentPatch().set("settings.theme", null)));
            assertTrue(await(alpha.read()).contains("settings.theme"));
            assertEquals(Optional.empty(), await(alpha.read()).get("missing.path"));
        }
    }

    @Test
    void revisionsAndConditionsBehaveCorrectly() throws Exception {
        try (InMemoryDataApi api = new InMemoryDataApi()) {
            Document alpha = api.namespace("plugin-a").collection("players").document("alpha");

            DocumentSnapshot missing = await(alpha.read());
            assertEquals("0", missing.revision());

            WriteResult created = await(alpha.write(Map.of("rank", "MEMBER"), WriteCondition.notExists()));
            DocumentSnapshot afterCreate = created.snapshot().orElseThrow();
            assertEquals("1", afterCreate.revision());

            WriteResult staleConflict = await(alpha.write(Map.of("rank", "ADMIN"), WriteCondition.revision("0")));
            assertFalse(staleConflict.applied());
            assertEquals(Optional.of("MEMBER"), staleConflict.snapshot().orElseThrow().get("rank", String.class));

            WriteResult conditionalPatch = await(alpha.patch(
                    new DocumentPatch().set("rank", "ADMIN"),
                    WriteCondition.revision(afterCreate.revision()).requireExists()
            ));
            assertTrue(conditionalPatch.applied());
            assertEquals("2", conditionalPatch.snapshot().orElseThrow().revision());

            WriteResult deleted = await(alpha.delete(WriteCondition.exists()));
            assertTrue(deleted.applied());
            assertFalse(deleted.snapshot().orElseThrow().exists());
            assertEquals("3", deleted.snapshot().orElseThrow().revision());
        }
    }

    @Test
    void namespacesAreIsolatedAndCountsStayScoped() throws Exception {
        try (InMemoryDataApi api = new InMemoryDataApi()) {
            DataNamespace pluginA = api.namespace("plugin-a");
            DataNamespace pluginB = api.namespace("plugin-b");
            DocumentCollection playersA = pluginA.collection("players");
            DocumentCollection playersB = pluginB.collection("players");

            await(playersA.document("alpha").write(Map.of("name", "Alice")));
            await(playersB.document("alpha").write(Map.of("name", "Bob")));

            assertEquals(1L, await(playersA.count()));
            assertEquals(1L, await(playersB.count()));
            assertEquals(Optional.of("Alice"), await(playersA.document("alpha").read()).get("name", String.class));
            assertEquals(Optional.of("Bob"), await(playersB.document("alpha").read()).get("name", String.class));
        }
    }

    private static <T> T await(CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
}
