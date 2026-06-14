package sh.harold.library.boundary.minestom;

import org.junit.jupiter.api.Test;
import sh.harold.library.boundary.BoundaryDecision;
import sh.harold.library.boundary.BoundaryDecisionQuery;
import sh.harold.library.boundary.BoundaryDecisionReason;
import sh.harold.library.boundary.BoundaryService;
import sh.harold.library.boundary.BoundarySnapshot;
import sh.harold.library.blockgrid.BlockBounds;
import sh.harold.library.blockgrid.BlockPos;
import sh.harold.library.spatial.SpaceId;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinestomBoundaryBridgeTest {

    @Test
    void registerConnectAndUnregisterControlTheVisibleService() {
        BoundaryService service = new TestBoundaryService();

        MinestomBoundaryBridge.register("owner", service);
        assertSame(service, MinestomBoundaryBridge.connect("owner"));

        MinestomBoundaryBridge.unregister("owner", service);
        assertThrows(IllegalStateException.class, () -> MinestomBoundaryBridge.connect("owner"));
    }

    private static final class TestBoundaryService implements BoundaryService {

        @Override
        public Optional<BoundarySnapshot> boundaryAt(SpaceId spaceId, BlockPos block) {
            return Optional.empty();
        }

        @Override
        public List<BoundarySnapshot> boundariesIntersecting(SpaceId spaceId, BlockBounds bounds) {
            return List.of();
        }

        @Override
        public BoundaryDecision decide(BoundaryDecisionQuery query) {
            return new BoundaryDecision(BoundaryDecisionReason.NO_BOUNDARY, null, true);
        }
    }
}
