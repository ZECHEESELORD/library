package sh.harold.creative.library.data.minestom.owner;

import sh.harold.creative.library.data.DataApi;
import sh.harold.creative.library.data.DocumentStore;
import sh.harold.creative.library.data.SharedDataProvider;
import sh.harold.creative.library.data.core.CaffeineDocumentStore;
import sh.harold.creative.library.data.core.ConfiguredSharedDataProvider;
import sh.harold.creative.library.data.core.ReflectiveSharedDataBridgeEndpoint;
import sh.harold.creative.library.data.core.StoreBackedDataApi;
import sh.harold.creative.library.data.memory.InMemoryDocumentStore;
import sh.harold.creative.library.data.minestom.MinestomSharedDataBridge;
import sh.harold.creative.library.data.mongodb.MongoDocumentStore;
import sh.harold.creative.library.data.yaml.YamlDocumentStore;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

public final class MinestomDataOwnerBootstrap {

    private MinestomDataOwnerBootstrap() {
    }

    public static void main(String[] args) {
        String ownerId = property("data.owner.id", "minestom-data-owner");
        DataApi dataApi = createDataApi();
        SharedDataProvider provider = new ConfiguredSharedDataProvider(dataApi, callerId -> callerId, callerId -> List.of());
        ReflectiveSharedDataBridgeEndpoint endpoint = new ReflectiveSharedDataBridgeEndpoint(provider);
        MinestomSharedDataBridge.register(ownerId, endpoint);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            MinestomSharedDataBridge.unregister(ownerId, endpoint);
            dataApi.close();
        }));
        System.out.println("[minestom-data-owner] Registered shared data owner " + ownerId + " using backend " + backendType() + ".");
    }

    private static DataApi createDataApi() {
        DocumentStore store = switch (backendType()) {
            case "memory" -> new InMemoryDocumentStore();
            case "yaml" -> new YamlDocumentStore(Path.of(property("data.owner.yaml.root", "minestom-data-owner")));
            case "mongodb" -> new MongoDocumentStore(
                    property("data.owner.mongo.connectionString", "mongodb://localhost:27017"),
                    property("data.owner.mongo.database", "library_data_owner")
            );
            default -> throw new IllegalArgumentException("unsupported data.owner.backend: " + backendType());
        };
        if (Boolean.parseBoolean(property("data.owner.cache.enabled", "true"))) {
            store = new CaffeineDocumentStore(
                    store,
                    Long.parseLong(property("data.owner.cache.maximumSize", "10000")),
                    Duration.ofSeconds(Long.parseLong(property("data.owner.cache.ttlSeconds", "5")))
            );
        }
        return new StoreBackedDataApi(store);
    }

    private static String backendType() {
        return property("data.owner.backend", "memory").toLowerCase(Locale.ROOT);
    }

    private static String property(String key, String fallback) {
        String value = System.getProperty(key, fallback);
        return value == null || value.isBlank() ? fallback : value;
    }
}
