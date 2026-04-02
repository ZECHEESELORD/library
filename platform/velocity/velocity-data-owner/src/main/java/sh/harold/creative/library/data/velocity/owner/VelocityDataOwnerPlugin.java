package sh.harold.creative.library.data.velocity.owner;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import sh.harold.creative.library.data.DataApi;
import sh.harold.creative.library.data.DocumentStore;
import sh.harold.creative.library.data.SharedDataProvider;
import sh.harold.creative.library.data.core.CaffeineDocumentStore;
import sh.harold.creative.library.data.core.ConfiguredSharedDataProvider;
import sh.harold.creative.library.data.core.ReflectiveSharedDataBridgeEndpoint;
import sh.harold.creative.library.data.core.StoreBackedDataApi;
import sh.harold.creative.library.data.memory.InMemoryDocumentStore;
import sh.harold.creative.library.data.mongodb.MongoDocumentStore;
import sh.harold.creative.library.data.yaml.YamlDocumentStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

@Plugin(
        id = "velocity-data-owner",
        name = "velocity-data-owner",
        version = "0.1.0"
)
public final class VelocityDataOwnerPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private DataApi dataApi;
    private ReflectiveSharedDataBridgeEndpoint bridge;
    private Properties properties;

    @Inject
    public VelocityDataOwnerPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        this.properties = loadProperties();
        this.dataApi = createDataApi();
        SharedDataProvider provider = new ConfiguredSharedDataProvider(
                dataApi,
                callerId -> callerId,
                this::extraNamespaces
        );
        this.bridge = new ReflectiveSharedDataBridgeEndpoint(provider);
        logger.info("Velocity data owner ready with backend {}.", backendType());
    }

    public Object sharedDataBridge() {
        return Objects.requireNonNull(bridge, "bridge");
    }

    private DataApi createDataApi() {
        DocumentStore store = switch (backendType()) {
            case "memory" -> new InMemoryDocumentStore();
            case "yaml" -> new YamlDocumentStore(dataDirectory.resolve(properties.getProperty("backend.yaml.root", "yaml-data")));
            case "mongodb" -> new MongoDocumentStore(
                    properties.getProperty("backend.mongodb.connectionString", "mongodb://localhost:27017"),
                    properties.getProperty("backend.mongodb.database", "library_data_owner")
            );
            default -> throw new IllegalArgumentException("unsupported backend.type: " + backendType());
        };
        if (Boolean.parseBoolean(properties.getProperty("cache.enabled", "true"))) {
            store = new CaffeineDocumentStore(
                    store,
                    Long.parseLong(properties.getProperty("cache.maximumSize", "10000")),
                    Duration.ofSeconds(Long.parseLong(properties.getProperty("cache.ttlSeconds", "5")))
            );
        }
        return new StoreBackedDataApi(store);
    }

    private String backendType() {
        return properties.getProperty("backend.type", "memory").toLowerCase(Locale.ROOT);
    }

    private List<String> extraNamespaces(String callerId) {
        String raw = properties.getProperty("sharing.extraNamespaces." + callerId, "");
        if (raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private Properties loadProperties() {
        try {
            Files.createDirectories(dataDirectory);
            Path path = dataDirectory.resolve("data-owner.properties");
            if (!Files.exists(path)) {
                Files.writeString(path, """
                        backend.type=memory
                        backend.yaml.root=yaml-data
                        backend.mongodb.connectionString=mongodb://localhost:27017
                        backend.mongodb.database=library_data_owner
                        cache.enabled=true
                        cache.maximumSize=10000
                        cache.ttlSeconds=5
                        """);
            }
            Properties loaded = new Properties();
            try (InputStream input = Files.newInputStream(path)) {
                loaded.load(input);
            }
            return loaded;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to load velocity data owner properties", exception);
        }
    }
}
