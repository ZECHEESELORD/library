package sh.harold.creative.library.blockgrid;

import org.junit.jupiter.api.Test;
import sh.harold.creative.library.spatial.Bounds3;
import sh.harold.creative.library.spatial.Vec3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockGridApiTest {

    @Test
    void boundsUseInclusiveSemantics() {
        BlockBounds bounds = new BlockBounds(new BlockPos(-2, 3, 4), new BlockPos(2, 7, 6));

        assertTrue(bounds.contains(new BlockPos(-2, 3, 4)));
        assertTrue(bounds.contains(new BlockPos(2, 7, 6)));
        assertEquals(5L, bounds.sizeX());
        assertEquals(5L, bounds.sizeY());
        assertEquals(3L, bounds.sizeZ());
        assertEquals(75L, bounds.blockCountExact());
    }

    @Test
    void clampAndIntersectionRespectInclusiveEdges() {
        BlockBounds first = new BlockBounds(new BlockPos(0, 0, 0), new BlockPos(4, 4, 4));
        BlockBounds touching = new BlockBounds(new BlockPos(4, 4, 4), new BlockPos(6, 6, 6));
        BlockBounds separate = new BlockBounds(new BlockPos(5, 5, 5), new BlockPos(7, 7, 7));

        assertEquals(new BlockPos(0, 2, 4), first.clamp(new BlockPos(-3, 2, 9)));
        assertTrue(first.intersects(touching));
        assertFalse(first.intersects(separate));
    }

    @Test
    void betweenFactoryIsExplicitAboutNormalization() {
        BlockBounds bounds = BlockBounds.between(new BlockPos(5, 2, -1), new BlockPos(2, 7, -4));

        assertEquals(new BlockPos(2, 2, -4), bounds.min());
        assertEquals(new BlockPos(5, 7, -1), bounds.max());
    }

    @Test
    void blockCountExactReportsOverflowExplicitly() {
        BlockBounds huge = new BlockBounds(
                new BlockPos(Integer.MIN_VALUE, Integer.MIN_VALUE, 0),
                new BlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, 0)
        );

        assertThrows(ArithmeticException.class, huge::blockCountExact);
    }

    @Test
    void blockBoundsProjectToFullVoxelFloatingBounds() {
        BlockBounds bounds = new BlockBounds(new BlockPos(1, 2, 3), new BlockPos(4, 5, 6));

        Bounds3 projected = bounds.toBounds3();

        assertEquals(new Vec3(1.0, 2.0, 3.0), projected.min());
        assertEquals(new Vec3(5.0, 6.0, 7.0), projected.max());
    }
}
