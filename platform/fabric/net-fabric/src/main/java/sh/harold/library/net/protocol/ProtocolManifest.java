package sh.harold.library.net.protocol;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * What one side of a connection runs: the library version and each declared feature's protocol version.
 * Host-free so the handshake rules are unit-tested without a game.
 */
public record ProtocolManifest(String libraryVersion, Map<String, Integer> features) {

    public ProtocolManifest {
        Objects.requireNonNull(libraryVersion, "libraryVersion");
        features = Map.copyOf(new TreeMap<>(Objects.requireNonNull(features, "features")));
        for (Map.Entry<String, Integer> feature : features.entrySet()) {
            PayloadIds.requireFeature(feature.getKey());
            if (feature.getValue() < 0) {
                throw new IllegalArgumentException("feature '" + feature.getKey() + "' has a negative protocol version");
            }
        }
    }

    /** Features sorted by id, for stable messages. */
    public TreeMap<String, Integer> sortedFeatures() {
        return new TreeMap<>(features);
    }
}
