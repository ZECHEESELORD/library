package sh.harold.library.entity.paper;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.util.PEVersion;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

final class PaperPacketEventsRequirement {
    private static final int REQUIRED_MAJOR = 2;
    private static final int REQUIRED_MINOR = 13;
    private static final int REQUIRED_PATCH = 0;
    private static final ServerVersion SUPPORTED_PROTOCOL = ServerVersion.V_26_1_2;

    private PaperPacketEventsRequirement() {
    }

    static PacketEventsAPI<?> verifyRuntime(Plugin owner) {
        Objects.requireNonNull(owner, "owner");
        Plugin packetEventsPlugin = owner.getServer().getPluginManager().getPlugin("packetevents");
        if (packetEventsPlugin == null) {
            packetEventsPlugin = owner.getServer().getPluginManager().getPlugin("PacketEvents");
        }

        PacketEventsAPI<?> api = null;
        Throwable linkageFailure = null;
        try {
            api = PacketEvents.getAPI();
        } catch (Throwable failure) {
            linkageFailure = failure;
        }

        PEVersion version = api == null ? null : api.getVersion();
        ServerVersion protocol = api == null || api.getServerManager() == null
                ? null
                : api.getServerManager().getVersion();
        RuntimeState state = new RuntimeState(
                packetEventsPlugin != null,
                packetEventsPlugin != null && packetEventsPlugin.isEnabled(),
                api != null && api.isLoaded(),
                api != null && api.isInitialized(),
                version == null ? -1 : version.major(),
                version == null ? -1 : version.minor(),
                version == null ? -1 : version.patch(),
                version != null && version.snapshot(),
                protocol == null ? "unknown" : protocol.name()
        );
        try {
            verify(state);
        } catch (IllegalStateException failure) {
            if (linkageFailure != null) {
                failure.initCause(linkageFailure);
            }
            throw failure;
        }
        return api;
    }

    static void verify(RuntimeState state) {
        Objects.requireNonNull(state, "state");
        if (!state.pluginPresent()) {
            throw new IllegalStateException("PaperEntityPlatform requires the PacketEvents 2.13.0 server plugin");
        }
        if (!state.pluginEnabled()) {
            throw new IllegalStateException("PacketEvents is installed but disabled; PaperEntityPlatform cannot start");
        }
        if (!state.loaded()) {
            throw new IllegalStateException("PacketEvents is enabled but has not completed its load phase");
        }
        if (!state.initialized()) {
            throw new IllegalStateException("PacketEvents is loaded but not initialized; construct PaperEntityPlatform after PacketEvents enables");
        }
        if (state.major() != REQUIRED_MAJOR
                || state.minor() != REQUIRED_MINOR
                || state.patch() != REQUIRED_PATCH
                || state.snapshot()) {
            throw new IllegalStateException("PaperEntityPlatform requires PacketEvents 2.13.0 exactly; found "
                    + state.versionLabel());
        }
        if (!SUPPORTED_PROTOCOL.name().equals(state.protocol())) {
            throw new IllegalStateException("Unsupported PacketEvents server protocol " + state.protocol()
                    + "; this v9 codec supports only " + SUPPORTED_PROTOCOL.name());
        }
    }

    record RuntimeState(
            boolean pluginPresent,
            boolean pluginEnabled,
            boolean loaded,
            boolean initialized,
            int major,
            int minor,
            int patch,
            boolean snapshot,
            String protocol
    ) {
        RuntimeState {
            Objects.requireNonNull(protocol, "protocol");
        }

        String versionLabel() {
            if (major < 0) {
                return "unknown";
            }
            return major + "." + minor + "." + patch + (snapshot ? "-SNAPSHOT" : "");
        }
    }
}
