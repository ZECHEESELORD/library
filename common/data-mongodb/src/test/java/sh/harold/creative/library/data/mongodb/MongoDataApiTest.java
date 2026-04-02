package sh.harold.creative.library.data.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import sh.harold.creative.library.data.Document;
import sh.harold.creative.library.data.DocumentPatch;
import sh.harold.creative.library.data.DocumentSnapshot;
import sh.harold.creative.library.data.WriteCondition;
import sh.harold.creative.library.data.WriteResult;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class MongoDataApiTest {

    @Container
    private static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    private MongoClient client;

    @AfterEach
    void closeClient() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void mongoPersistsAcrossApiInstancesAndNamespaces() throws Exception {
        String database = "test_" + UUID.randomUUID().toString().replace("-", "");
        client = MongoClients.create(MONGO.getConnectionString());

        try (MongoDataApi writer = new MongoDataApi(client, database);
             MongoDataApi reader = new MongoDataApi(client, database)) {
            await(writer.namespace("plugin-a").collection("players").document("alpha").write(Map.of("name", "Alice")));
            await(writer.namespace("plugin-b").collection("players").document("alpha").write(Map.of("name", "Bob")));

            assertEquals(Optional.of("Alice"),
                    await(reader.namespace("plugin-a").collection("players").document("alpha").read()).get("name", String.class));
            assertEquals(Optional.of("Bob"),
                    await(reader.namespace("plugin-b").collection("players").document("alpha").read()).get("name", String.class));
        }
    }

    @Test
    void mongoConditionsAndDeleteUseSharedRevisionState() throws Exception {
        String database = "test_" + UUID.randomUUID().toString().replace("-", "");
        client = MongoClients.create(MONGO.getConnectionString());

        try (MongoDataApi api = new MongoDataApi(client, database)) {
            Document document = api.namespace("plugin-a").collection("players").document("alpha");

            WriteResult created = await(document.write(Map.of("name", "Alice"), WriteCondition.notExists()));
            WriteResult conflict = await(document.write(Map.of("name", "Bob"), WriteCondition.revision("0")));
            WriteResult deleted = await(document.delete(WriteCondition.revision(created.snapshot().orElseThrow().revision()).requireExists()));
            DocumentSnapshot afterDelete = await(document.read());

            assertTrue(created.applied());
            assertFalse(conflict.applied());
            assertTrue(deleted.applied());
            assertFalse(afterDelete.exists());
            assertEquals(deleted.snapshot().orElseThrow().revision(), afterDelete.revision());
        }
    }

    @Test
    void mongoPatchAndCountBehaveCorrectly() throws Exception {
        String database = "test_" + UUID.randomUUID().toString().replace("-", "");
        client = MongoClients.create(MONGO.getConnectionString());

        try (MongoDataApi api = new MongoDataApi(client, database)) {
            Document first = api.namespace("plugin-a").collection("players").document("alpha");
            Document second = api.namespace("plugin-a").collection("players").document("bravo");

            await(first.write(Map.of("profile", Map.of("rank", "MEMBER"))));
            await(second.write(Map.of("profile", Map.of("rank", "ADMIN"))));
            await(first.patch(new DocumentPatch().set("profile.rank", "ADMIN").set("settings.theme", "dark")));

            assertEquals(2L, await(api.namespace("plugin-a").collection("players").count()));
            assertEquals(Optional.of("dark"), await(first.read()).get("settings.theme", String.class));
        }
    }

    private static <T> T await(CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
