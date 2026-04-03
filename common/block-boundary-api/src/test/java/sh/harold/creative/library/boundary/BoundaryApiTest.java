package sh.harold.creative.library.boundary;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.blockgrid.BlockBounds;
import sh.harold.creative.library.blockgrid.BlockPos;
import sh.harold.creative.library.spatial.SpaceId;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundaryApiTest {

    @Test
    void snapshotEnforcesExactExtentCapabilityConsistency() {
        BlockBounds bounds = new BlockBounds(new BlockPos(0, 0, 0), new BlockPos(1, 1, 1));

        assertThrows(IllegalArgumentException.class, () -> new BoundarySnapshot(
                Key.key("test", "missing-cap"),
                SpaceId.of("test", "world"),
                BoundaryExtent.exactAabb(bounds),
                Set.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new BoundarySnapshot(
                Key.key("test", "missing-bounds"),
                SpaceId.of("test", "world"),
                BoundaryExtent.unavailable(),
                Set.of(BoundaryCapability.EXACT_AABB_BOUNDS)
        ));

        BoundarySnapshot snapshot = new BoundarySnapshot(
                Key.key("test", "exact"),
                SpaceId.of("test", "world"),
                BoundaryExtent.exactAabb(bounds),
                Set.of(BoundaryCapability.EXACT_AABB_BOUNDS)
        );
        assertEquals(bounds, snapshot.exactBounds().orElseThrow());
    }

    @Test
    void actorReferencesRejectBlankValues() {
        assertThrows(NullPointerException.class, () -> new BoundaryActorRef(null, "alpha"));
        assertThrows(IllegalArgumentException.class, () -> new BoundaryActorRef(" ", "alpha"));
        assertThrows(IllegalArgumentException.class, () -> new BoundaryActorRef("player", " "));
        assertEquals("player", BoundaryActorRef.player(UUID.fromString("00000000-0000-0000-0000-000000000111")).kind());
    }

    @Test
    void blockSetDeduplicatesAndComputesBounds() {
        BoundaryTarget.BlockSet target = new BoundaryTarget.BlockSet(
                SpaceId.of("test", "world"),
                java.util.List.of(
                        new BlockPos(3, 5, 7),
                        new BlockPos(3, 5, 7),
                        new BlockPos(-2, 4, 9)
                )
        );

        assertEquals(2, target.blocks().size());
        assertEquals(
                new BlockBounds(new BlockPos(-2, 4, 7), new BlockPos(3, 5, 9)),
                target.bounds()
        );
    }

    @Test
    void decisionQueryAndDecisionExposeOptionalFieldsConveniently() {
        BoundaryDecisionQuery query = new BoundaryDecisionQuery(
                BoundaryActorRef.literal("tool", "builder-wand"),
                Key.key("creative", "bulk-edit"),
                new BoundaryTarget.SingleBlock(SpaceId.of("test", "world"), new BlockPos(1, 2, 3))
        );
        BoundaryDecision decision = new BoundaryDecision(BoundaryDecisionReason.NO_BOUNDARY, null, true);

        assertEquals(SpaceId.of("test", "world"), query.spaceId());
        assertTrue(query.optionalSource().isEmpty());
        assertTrue(decision.optionalMatchedBoundary().isEmpty());
        assertFalse(decision.allowed());
    }
}
