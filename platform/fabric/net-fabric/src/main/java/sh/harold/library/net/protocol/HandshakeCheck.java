package sh.harold.library.net.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The configuration-phase rule (architecture B6): every feature the server declares must exist on the client
 * with the same protocol version, and both sides must run the same library version. Extra client features are
 * fine (the client may have more mods). A rejection names each feature at fault.
 */
public final class HandshakeCheck {

    private HandshakeCheck() {
    }

    public static Verdict compare(ProtocolManifest server, ProtocolManifest client) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(client, "client");
        List<String> features = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Integer> feature : server.sortedFeatures().entrySet()) {
            Integer clientVersion = client.features().get(feature.getKey());
            if (clientVersion == null) {
                features.add(feature.getKey());
                problems.add("feature '" + feature.getKey() + "' is missing on this client");
            } else if (!clientVersion.equals(feature.getValue())) {
                features.add(feature.getKey());
                problems.add("feature '" + feature.getKey() + "' is protocol " + feature.getValue()
                        + " on the server but " + clientVersion + " on this client");
            }
        }
        if (!server.libraryVersion().equals(client.libraryVersion())) {
            problems.add("library " + server.libraryVersion() + " on the server but "
                    + client.libraryVersion() + " on this client");
        }
        return new Verdict(problems.isEmpty(), List.copyOf(features), List.copyOf(problems));
    }

    /** A message for a client that has none of the library's network features at all. */
    public static String missingClientMessage(ProtocolManifest server) {
        return "This server needs the creative library " + server.libraryVersion()
                + " on the client for: " + String.join(", ", server.sortedFeatures().keySet()) + ".";
    }

    /**
     * @param accepted   true when the client may continue
     * @param features   the feature ids at fault, sorted
     * @param problems   one readable line per problem
     */
    public record Verdict(boolean accepted, List<String> features, List<String> problems) {

        /** The disconnect text: names each feature and asks for matching builds. */
        public String message() {
            if (accepted) {
                return "";
            }
            return "Creative library mismatch: " + String.join("; ", problems)
                    + ". Use the same library build on client and server.";
        }
    }
}
