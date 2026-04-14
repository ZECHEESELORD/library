package sh.harold.creative.library.data.fabric;

import sh.harold.creative.library.data.DataApi;
import sh.harold.creative.library.data.yaml.YamlDataApi;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

public final class FabricDataApiFactory {

    private final Function<Path, DataApi> yamlFactory;

    public FabricDataApiFactory() {
        this(YamlDataApi::new);
    }

    FabricDataApiFactory(Function<Path, DataApi> yamlFactory) {
        this.yamlFactory = Objects.requireNonNull(yamlFactory, "yamlFactory");
    }

    public DataApi create(Path modDataDirectory, FabricDataSettings settings) {
        Objects.requireNonNull(modDataDirectory, "modDataDirectory");
        Objects.requireNonNull(settings, "settings");
        return yamlFactory.apply(modDataDirectory.resolve(settings.yamlRoot()).toAbsolutePath().normalize());
    }
}
