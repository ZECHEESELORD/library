package sh.harold.library.net.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import sh.harold.library.net.protocol.PayloadIds;
import sh.harold.library.net.protocol.ProtocolManifest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The library's network registry. Features declare themselves here with a protocol version, which the
 * configuration-phase handshake checks, and declare their payloads through the returned {@link NetFeature}.
 *
 * <p>With no feature declared the handshake is skipped and vanilla clients can join. Thread-safe.
 */
public final class LibraryNet {

    /** The mod id of {@code net-fabric}, whose version is the library version both sides must share. */
    public static final String MOD_ID = "creative_library_net_fabric";
    public static final String FEATURE = "net";
    /** Ticks between {@code net/tick} broadcasts. */
    public static final int TICK_INTERVAL = 20;

    private static final Map<String, NetFeature> FEATURES = new LinkedHashMap<>();
    private static volatile String libraryVersionOverride;

    private LibraryNet() {
    }

    /**
     * Declares {@code feature} at {@code protocolVersion}. Bump the version whenever the feature's payloads or
     * their meaning change. Call during mod initialisation on both sides, since payload types must be
     * registered before any connection.
     *
     * @throws IllegalStateException if the feature is already declared and not closed
     */
    public static NetFeature declare(String feature, int protocolVersion) {
        PayloadIds.requireFeature(feature);
        if (FEATURE.equals(feature)) {
            throw new IllegalArgumentException("'net' is reserved for the library's own payloads");
        }
        if (protocolVersion < 0) {
            throw new IllegalArgumentException("protocolVersion cannot be negative");
        }
        NetServerBootstrap.install(); // in case the entrypoint has not run yet (or the jar is not loaded as a mod)
        synchronized (FEATURES) {
            NetFeature existing = FEATURES.get(feature);
            if (existing != null && existing.active()) {
                throw new IllegalStateException("network feature '" + feature + "' is already declared");
            }
            NetFeature declared = new NetFeature(feature, protocolVersion);
            FEATURES.put(feature, declared);
            return declared;
        }
    }

    /** This side's manifest: the library version and every open feature. */
    public static ProtocolManifest manifest() {
        Map<String, Integer> versions = new LinkedHashMap<>();
        synchronized (FEATURES) {
            for (NetFeature feature : FEATURES.values()) {
                versions.put(feature.id(), feature.protocolVersion());
            }
        }
        return new ProtocolManifest(libraryVersion(), versions);
    }

    /** The library version: {@code net-fabric}'s mod version, which the build sets from the root project version. */
    public static String libraryVersion() {
        String override = libraryVersionOverride;
        if (override != null) {
            return override;
        }
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    /** Test hook: pretend to be another library build (for mismatch tests in gametests). {@code null} clears it. */
    public static void overrideLibraryVersionForTesting(String version) {
        libraryVersionOverride = version;
    }

    /** The payload type {@code creative_library:<feature>/<name>}. */
    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String feature, String name) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(PayloadIds.NAMESPACE,
                PayloadIds.path(feature, name)));
    }

    static void remove(NetFeature feature) {
        Objects.requireNonNull(feature, "feature");
        synchronized (FEATURES) {
            FEATURES.remove(feature.id(), feature);
        }
    }
}
