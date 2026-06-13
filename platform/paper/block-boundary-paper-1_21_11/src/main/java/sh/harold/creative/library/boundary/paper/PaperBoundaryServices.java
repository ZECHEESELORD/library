package sh.harold.creative.library.boundary.paper;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import sh.harold.creative.library.boundary.BoundaryProvider;
import sh.harold.creative.library.boundary.BoundaryService;

import java.util.Objects;
import java.util.Optional;

public final class PaperBoundaryServices {

    private PaperBoundaryServices() {
    }

    public static void register(Plugin plugin, BoundaryService service) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(service, "service");
        plugin.getServer().getServicesManager().register(BoundaryService.class, service, plugin, ServicePriority.Normal);
    }

    public static void register(Plugin plugin, BoundaryProvider provider) {
        register(plugin, (BoundaryService) provider);
    }

    public static Optional<BoundaryService> resolve(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return Optional.ofNullable(plugin.getServer().getServicesManager().load(BoundaryService.class));
    }

    public static BoundaryService required(Plugin plugin) {
        return resolve(plugin).orElseThrow(() -> new IllegalStateException("boundary service is not registered"));
    }
}
