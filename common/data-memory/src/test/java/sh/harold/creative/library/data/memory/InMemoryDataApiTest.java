package sh.harold.creative.library.data.memory;

import org.junit.jupiter.api.Test;
import sh.harold.creative.library.data.Document;
import sh.harold.creative.library.data.DocumentCollection;
import sh.harold.creative.library.data.DocumentPatch;
import sh.harold.creative.library.data.DocumentSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryDataApiTest {

    @Test
    void putCreatesAndReplacesDocuments() throws Exception {
        try (InMemoryDataApi api = new InMemoryDataApi()) {
            DocumentCollection players = api.collection("players");

            await(players.put("alpha", Map.of("name", "Alice")));

            assertTrue(await(players.exists("alpha")));
            assertEquals(1L, await(players.count()));
            assertEquals(Optional.of("Alice"), await(players.load("alpha")).get("name", String.class));

            await(players.put("alpha", Map.of("name", "Bob")));

            assertEquals(1L, await(players.count()));
            assertEquals(Optional.of("Bob"), await(players.load("alpha")).get("name", String.class));
        }
    }

    @Test
    void missingDocumentsStayStable() throws Exception {
        try (InMemoryDataApi api = new InMemoryDataApi()) {
            DocumentCollection players = api.collection("players");
            Document document = await(players.load("missing"));

            assertFalse(document.exists());
            assertFalse(await(players.exists("missing")));

            await(document.remove("profile.rank"));
            await(document.patch(new DocumentPatch().remove("legacy.name")));

            DocumentSnapshot snapshot = document.snapshot();
            assertFalse(snapshot.exists());
            assertTrue(snapshot.data().isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> snapshot.data().put("x", "y"));
        }
    }

    @Test
    void snapshotsAreDeeplyImmutable() throws Exception {
        try (InMemoryDataApi api = new InMemoryDataApi()) {
            DocumentCollection players = api.collection("players");
            await(players.put("alpha", Map.of(
                    "profile", Map.of("rank", "ADMIN"),
                    "tags", List.of("staff", "builder")
            )));

            DocumentSnapshot snapshot = await(players.load("alpha")).snapshot();

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
    void putAndSetDefensivelyCopyMutableInputs() throws Exception {
        try (InMemoryDataApi api = new InMemoryDataApi()) {
            DocumentCollection players = api.collection("players");

            Map<String, Object> mutableRoot = new LinkedHashMap<>();
            Map<String, Object> mutableProfile = new LinkedHashMap<>();
            mutableProfile.put("rank", "ADMIN");
            mutableRoot.put("profile", mutableProfile);
            List<String> mutableTags = new ArrayList<>(List.of("staff"));
            mutableRoot.put("tags", mutableTags);

            await(players.put("alpha", mutableRoot));

            mutableProfile.put("rank", "MEMBER");
            mutableTags.add("vip");

            Document document = await(players.load("alpha"));
            assertEquals(Optional.of("ADMIN"), document.get("profile.rank", String.class));

            Map<String, Object> newSettings = new LinkedHashMap<>();
            newSettings.put("theme", "dark");
            await(document.set("settings", newSettings));
            newSettings.put("theme", "light");

            assertEquals(Optional.of("dark"), document.get("settings.theme", String.class));
        }
    }

    @Test
    void updateUsesIsolatedEditableView() throws Exception {
        try (InMemoryDataApi api = new InMemoryDataApi()) {
            DocumentCollection players = api.collection("players");
            await(players.put("alpha", Map.of(
                    "profile", Map.of("rank", "ADMIN"),
                    "stats", Map.of("kills", 1)
            )));

            Document document = await(players.load("alpha"));
            @SuppressWarnings("unchecked")
            Map<String, Object>[] leaked = new Map[1];

            await(document.update(map -> {
                leaked[0] = map;

                @SuppressWarnings("unchecked")
                Map<String, Object> stats = (Map<String, Object>) map.computeIfAbsent("stats", ignored -> new LinkedHashMap<>());
                int kills = ((Number) stats.getOrDefault("kills", 0)).intValue();
                stats.put("kills", kills + 1);
                map.put("displayName", "Alpha");
                return map;
            }));

            leaked[0].put("displayName", "Corrupted");

            assertEquals(Optional.of("Alpha"), document.get("displayName", String.class));
            assertEquals(Optional.of(2), document.get("stats.kills", Integer.class));
        }
    }

    @Test
    void patchDeleteAllAndCountBehaveCorrectly() throws Exception {
        try (InMemoryDataApi api = new InMemoryDataApi()) {
            DocumentCollection players = api.collection("players");
            await(players.put("alpha", Map.of("profile", Map.of("rank", "MEMBER"), "legacy", Map.of("name", "Old"))));
            await(players.put("bravo", Map.of("profile", Map.of("rank", "ADMIN"))));

            Document alpha = await(players.load("alpha"));
            await(alpha.patch(new DocumentPatch()
                    .set("profile.rank", "ADMIN")
                    .remove("legacy.name")));

            assertEquals(Optional.of("ADMIN"), alpha.get("profile.rank", String.class));
            assertFalse(alpha.get("legacy.name", String.class).isPresent());

            assertEquals(2L, await(players.count()));
            assertEquals(2, await(players.all()).size());

            assertTrue(await(players.delete("bravo")));
            assertFalse(await(players.delete("bravo")));
            assertEquals(1L, await(players.count()));
        }
    }

    private static <T> T await(CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
}
