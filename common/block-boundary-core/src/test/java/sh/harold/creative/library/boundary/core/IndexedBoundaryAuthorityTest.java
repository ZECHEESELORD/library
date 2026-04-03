package sh.harold.creative.library.boundary.core;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.blockgrid.BlockBounds;
import sh.harold.creative.library.blockgrid.BlockPos;
import sh.harold.creative.library.boundary.BoundaryActorRef;
import sh.harold.creative.library.boundary.BoundaryCapability;
import sh.harold.creative.library.boundary.BoundaryDecision;
import sh.harold.creative.library.boundary.BoundaryDecisionQuery;
import sh.harold.creative.library.boundary.BoundaryDecisionReason;
import sh.harold.creative.library.boundary.BoundaryExtent;
import sh.harold.creative.library.boundary.BoundarySnapshot;
import sh.harold.creative.library.boundary.BoundaryTarget;
import sh.harold.creative.library.spatial.SpaceId;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IndexedBoundaryAuthorityTest {

    private static final SpaceId SPACE = SpaceId.of("test", "world");
    private static final Key BULK_EDIT = Key.key("creative", "bulk-edit");

    @Test
    void boundaryAtReturnsTheContainingSnapshot() {
        IndexedBoundaryAuthority authority = new IndexedBoundaryAuthority();
        BoundarySnapshot boundary = snapshot("alpha", 0, 9, 0, 9, 0, 9);
        authority.upsert(boundary);

        assertEquals(boundary, authority.boundaryAt(SPACE, new BlockPos(5, 5, 5)).orElseThrow());
    }

    @Test
    void boundariesIntersectingUsesExactAabbLookup() {
        IndexedBoundaryAuthority authority = new IndexedBoundaryAuthority();
        BoundarySnapshot first = snapshot("alpha", 0, 9, 0, 9, 0, 9);
        BoundarySnapshot second = snapshot("beta", 20, 29, 0, 9, 0, 9);
        authority.upsert(first);
        authority.upsert(second);

        List<BoundarySnapshot> intersections = authority.boundariesIntersecting(
                SPACE,
                new BlockBounds(new BlockPos(5, 0, 0), new BlockPos(25, 9, 9))
        );

        assertEquals(List.of(first, second), intersections);
    }

    @Test
    void upsertRejectsOverlappingExactBoundaries() {
        IndexedBoundaryAuthority authority = new IndexedBoundaryAuthority();
        authority.upsert(snapshot("alpha", 0, 9, 0, 9, 0, 9));

        assertThrows(IllegalArgumentException.class, () -> authority.upsert(snapshot("beta", 9, 12, 0, 9, 0, 9)));
    }

    @Test
    void decideDistinguishesNoBoundaryAndNoAccess() {
        IndexedBoundaryAuthority authority = new IndexedBoundaryAuthority();
        BoundarySnapshot boundary = snapshot("alpha", 0, 9, 0, 9, 0, 9);
        authority.upsert(boundary, BoundaryRule.fixed(BoundaryDecisionReason.NO_ACCESS));

        BoundaryDecision inside = authority.decide(new BoundaryDecisionQuery(
                BoundaryActorRef.player(UUID.randomUUID()),
                BULK_EDIT,
                new BoundaryTarget.SingleBlock(SPACE, new BlockPos(2, 2, 2))
        ));
        BoundaryDecision outside = authority.decide(new BoundaryDecisionQuery(
                BoundaryActorRef.player(UUID.randomUUID()),
                BULK_EDIT,
                new BoundaryTarget.SingleBlock(SPACE, new BlockPos(40, 2, 2))
        ));

        assertEquals(BoundaryDecisionReason.NO_ACCESS, inside.reason());
        assertSame(boundary, inside.matchedBoundary());
        assertEquals(BoundaryDecisionReason.NO_BOUNDARY, outside.reason());
    }

    @Test
    void boundsQueriesRejectRegionsThatEscapeTheMatchedBoundary() {
        IndexedBoundaryAuthority authority = new IndexedBoundaryAuthority();
        BoundarySnapshot boundary = snapshot("alpha", 0, 9, 0, 9, 0, 9);
        authority.upsert(boundary);

        BoundaryDecision decision = authority.decide(new BoundaryDecisionQuery(
                BoundaryActorRef.player(UUID.randomUUID()),
                BULK_EDIT,
                new BoundaryTarget.Bounds(SPACE, new BlockBounds(new BlockPos(8, 1, 1), new BlockPos(10, 2, 2)))
        ));

        assertEquals(BoundaryDecisionReason.OUTSIDE_BOUNDARY, decision.reason());
        assertSame(boundary, decision.matchedBoundary());
    }

    @Test
    void blockSetQueriesDenyWhenAnyBlockEscapesTheBoundary() {
        IndexedBoundaryAuthority authority = new IndexedBoundaryAuthority();
        BoundarySnapshot boundary = snapshot("alpha", 0, 9, 0, 9, 0, 9);
        authority.upsert(boundary);

        BoundaryDecision decision = authority.decide(new BoundaryDecisionQuery(
                BoundaryActorRef.player(UUID.randomUUID()),
                BULK_EDIT,
                new BoundaryTarget.BlockSet(SPACE, List.of(new BlockPos(1, 1, 1), new BlockPos(20, 1, 1)))
        ));

        assertEquals(BoundaryDecisionReason.OUTSIDE_BOUNDARY, decision.reason());
        assertSame(boundary, decision.matchedBoundary());
    }

    @Test
    void equivalentBoundsAndBlockSetQueriesProduceTheSameDecision() {
        IndexedBoundaryAuthority authority = new IndexedBoundaryAuthority();
        BoundarySnapshot boundary = snapshot("alpha", 0, 9, 0, 9, 0, 9);
        authority.upsert(boundary);

        BoundaryDecision boundsDecision = authority.decide(new BoundaryDecisionQuery(
                BoundaryActorRef.player(UUID.randomUUID()),
                BULK_EDIT,
                new BoundaryTarget.Bounds(SPACE, new BlockBounds(new BlockPos(1, 1, 1), new BlockPos(2, 2, 2)))
        ));
        BoundaryDecision blockSetDecision = authority.decide(new BoundaryDecisionQuery(
                BoundaryActorRef.player(UUID.randomUUID()),
                BULK_EDIT,
                new BoundaryTarget.BlockSet(SPACE, List.of(
                        new BlockPos(1, 1, 1),
                        new BlockPos(2, 1, 1),
                        new BlockPos(1, 2, 1),
                        new BlockPos(2, 2, 1),
                        new BlockPos(1, 1, 2),
                        new BlockPos(2, 1, 2),
                        new BlockPos(1, 2, 2),
                        new BlockPos(2, 2, 2)
                ))
        ));

        assertEquals(BoundaryDecisionReason.ALLOWED, boundsDecision.reason());
        assertEquals(boundsDecision.reason(), blockSetDecision.reason());
        assertSame(boundary, boundsDecision.matchedBoundary());
        assertSame(boundary, blockSetDecision.matchedBoundary());
    }

    private static BoundarySnapshot snapshot(String id, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        return new BoundarySnapshot(
                Key.key("test", id),
                SPACE,
                BoundaryExtent.exactAabb(new BlockBounds(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ))),
                Set.of(
                        BoundaryCapability.EXACT_AABB_BOUNDS,
                        BoundaryCapability.REGION_INTERSECTION_QUERY,
                        BoundaryCapability.SINGLE_BLOCK_DECISION,
                        BoundaryCapability.BATCH_DECISION
                )
        );
    }
}
