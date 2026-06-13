package sh.harold.creative.library.boundary.paper;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.boundary.BoundaryDecision;
import sh.harold.creative.library.boundary.BoundaryDecisionQuery;
import sh.harold.creative.library.boundary.BoundaryDecisionReason;
import sh.harold.creative.library.boundary.BoundaryService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperBoundaryServicesTest {

    @Test
    void registerPublishesTheBoundaryServiceInServicesManager() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        ServicesManager servicesManager = mock(ServicesManager.class);
        BoundaryService service = new TestBoundaryService();
        when(plugin.getServer()).thenReturn(server);
        when(server.getServicesManager()).thenReturn(servicesManager);

        PaperBoundaryServices.register(plugin, service);

        verify(servicesManager).register(
                BoundaryService.class,
                service,
                plugin,
                org.bukkit.plugin.ServicePriority.Normal
        );
    }

    @Test
    void resolveAndRequiredLoadThePublishedService() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        ServicesManager servicesManager = mock(ServicesManager.class);
        BoundaryService service = new TestBoundaryService();
        when(plugin.getServer()).thenReturn(server);
        when(server.getServicesManager()).thenReturn(servicesManager);
        when(servicesManager.load(BoundaryService.class)).thenReturn(service);

        assertSame(service, PaperBoundaryServices.resolve(plugin).orElseThrow());
        assertSame(service, PaperBoundaryServices.required(plugin));
    }

    @Test
    void requiredThrowsWhenTheServiceIsMissing() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        ServicesManager servicesManager = mock(ServicesManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getServicesManager()).thenReturn(servicesManager);
        when(servicesManager.load(BoundaryService.class)).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> PaperBoundaryServices.required(plugin));
    }

    private static final class TestBoundaryService implements BoundaryService {

        @Override
        public Optional<sh.harold.creative.library.boundary.BoundarySnapshot> boundaryAt(
                sh.harold.creative.library.spatial.SpaceId spaceId,
                sh.harold.creative.library.blockgrid.BlockPos block
        ) {
            return Optional.empty();
        }

        @Override
        public List<sh.harold.creative.library.boundary.BoundarySnapshot> boundariesIntersecting(
                sh.harold.creative.library.spatial.SpaceId spaceId,
                sh.harold.creative.library.blockgrid.BlockBounds bounds
        ) {
            return List.of();
        }

        @Override
        public BoundaryDecision decide(BoundaryDecisionQuery query) {
            return new BoundaryDecision(BoundaryDecisionReason.NO_BOUNDARY, null, true);
        }
    }
}
