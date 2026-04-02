package sh.harold.creative.library.data.paper.owner;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
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

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class PaperDataOwnerPlugin extends JavaPlugin {

    private DataApi dataApi;
    private ReflectiveSharedDataBridgeEndpoint bridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.dataApi = createDataApi();
        SharedDataProvider provider = new ConfiguredSharedDataProvider(
                dataApi,
                callerId -> callerId,
                this::extraNamespaces
        );
        this.bridge = new ReflectiveSharedDataBridgeEndpoint(provider);
        getLogger().info("Paper data owner ready with backend " + backendType() + ".");
    }

    @Override
    public void onDisable() {
        if (dataApi != null) {
            dataApi.close();
        }
    }

    public Object sharedDataBridge() {
        return Objects.requireNonNull(bridge, "bridge");
    }

    private DataApi createDataApi() {
        DocumentStore store = switch (backendType()) {
            case "memory" -> new InMemoryDocumentStore();
            case "yaml" -> new YamlDocumentStore(yamlRoot());
            case "mongodb" -> new MongoDocumentStore(
                    getConfig().getString("backend.mongodb.connectionString", "mongodb://localhost:27017"),
                    getConfig().getString("backend.mongodb.database", "library_data_owner")
            );
            default -> throw new IllegalArgumentException("unsupported backend.type: " + backendType());
        };
        if (getConfig().getBoolean("cache.enabled", true)) {
            store = new CaffeineDocumentStore(
                    store,
                    getConfig().getLong("cache.maximumSize", 10_000L),
                    Duration.ofSeconds(getConfig().getLong("cache.ttlSeconds", 5L))
            );
        }
        return new StoreBackedDataApi(store);
    }

    private String backendType() {
        return getConfig().getString("backend.type", "memory").toLowerCase(Locale.ROOT);
    }

    private Path yamlRoot() {
        return getDataFolder().toPath().resolve(getConfig().getString("backend.yaml.root", "yaml-data"));
    }

    private Collection<String> extraNamespaces(String callerId) {
        ConfigurationSection section = getConfig().getConfigurationSection("sharing.extraNamespaces");
        if (section == null) {
            return List.of();
        }
        Object raw = section.get(callerId);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(String::valueOf)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableList());
    }
}
