package sh.harold.creative.library.blockgrid.paper;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.blockgrid.BlockPos;
import sh.harold.creative.library.spatial.SpaceId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperBlockGridTest {

    @Test
    void worldMapsToSpaceIdUsingTheWorldKey() {
        World world = mock(World.class);
        when(world.getKey()).thenReturn(new NamespacedKey("creative", "plots"));

        assertEquals(SpaceId.of("creative", "plots"), PaperBlockGrid.spaceId(world));
    }

    @Test
    void locationUsesBlockCoordinatesWithFloorSemantics() {
        Location location = new Location(null, 12.75, 64.99, -3.2);

        assertEquals(new BlockPos(12, 64, -4), PaperBlockGrid.blockPos(location));
    }

    @Test
    void blocksMapUsingExactIntegerCoordinates() {
        Block block = mock(Block.class);
        when(block.getX()).thenReturn(8);
        when(block.getY()).thenReturn(72);
        when(block.getZ()).thenReturn(-5);

        assertEquals(new BlockPos(8, 72, -5), PaperBlockGrid.blockPos(block));
    }
}
