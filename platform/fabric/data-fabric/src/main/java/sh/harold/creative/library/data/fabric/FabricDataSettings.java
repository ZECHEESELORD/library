package sh.harold.creative.library.data.fabric;

import java.util.Objects;

public record FabricDataSettings(String yamlRoot) {

    public FabricDataSettings {
        Objects.requireNonNull(yamlRoot, "yamlRoot");
        if (yamlRoot.isBlank()) {
            throw new IllegalArgumentException("yamlRoot cannot be blank");
        }
    }

    public static FabricDataSettings defaults() {
        return new FabricDataSettings("data");
    }
}
