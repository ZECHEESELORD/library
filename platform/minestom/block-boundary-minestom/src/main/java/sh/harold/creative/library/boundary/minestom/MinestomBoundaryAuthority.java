package sh.harold.creative.library.boundary.minestom;

import net.kyori.adventure.key.Key;
import sh.harold.creative.library.blockgrid.BlockBounds;
import sh.harold.creative.library.blockgrid.BlockPos;
import sh.harold.creative.library.boundary.BoundaryDecision;
import sh.harold.creative.library.boundary.BoundaryDecisionQuery;
import sh.harold.creative.library.boundary.BoundaryDecisionReason;
import sh.harold.creative.library.boundary.BoundaryProvider;
import sh.harold.creative.library.boundary.BoundarySnapshot;
import sh.harold.creative.library.boundary.core.IndexedBoundaryAuthority;
import sh.harold.creative.library.spatial.SpaceId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class MinestomBoundaryAuthority implements BoundaryProvider {

    private final IndexedBoundaryAuthority authority;

    public MinestomBoundaryAuthority() {
        this.authority = new IndexedBoundaryAuthority();
    }

    public void upsert(BoundarySnapshot snapshot) {
        authority.upsert(snapshot);
    }

    public void upsert(BoundarySnapshot snapshot, Function<BoundaryDecisionQuery, BoundaryDecisionReason> evaluator) {
        Objects.requireNonNull(evaluator, "evaluator");
        authority.upsert(snapshot, evaluator::apply);
    }

    public boolean remove(SpaceId spaceId, Key boundaryId) {
        return authority.remove(spaceId, boundaryId);
    }

    public void clearSpace(SpaceId spaceId) {
        authority.clearSpace(spaceId);
    }

    public void clearAll() {
        authority.clearAll();
    }

    @Override
    public Optional<BoundarySnapshot> boundaryAt(SpaceId spaceId, BlockPos block) {
        return authority.boundaryAt(spaceId, block);
    }

    @Override
    public List<BoundarySnapshot> boundariesIntersecting(SpaceId spaceId, BlockBounds bounds) {
        return authority.boundariesIntersecting(spaceId, bounds);
    }

    @Override
    public BoundaryDecision decide(BoundaryDecisionQuery query) {
        return authority.decide(query);
    }

    @Override
    public void close() {
        authority.close();
    }
}
