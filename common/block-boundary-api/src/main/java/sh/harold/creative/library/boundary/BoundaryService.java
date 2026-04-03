package sh.harold.creative.library.boundary;

import sh.harold.creative.library.blockgrid.BlockBounds;
import sh.harold.creative.library.blockgrid.BlockPos;
import sh.harold.creative.library.spatial.SpaceId;

import java.util.List;
import java.util.Optional;

public interface BoundaryService extends AutoCloseable {

    Optional<BoundarySnapshot> boundaryAt(SpaceId spaceId, BlockPos block);

    List<BoundarySnapshot> boundariesIntersecting(SpaceId spaceId, BlockBounds bounds);

    BoundaryDecision decide(BoundaryDecisionQuery query);

    @Override
    default void close() {
    }
}
