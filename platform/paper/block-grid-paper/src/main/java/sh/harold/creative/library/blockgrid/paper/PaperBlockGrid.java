package sh.harold.creative.library.blockgrid.paper;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import sh.harold.creative.library.blockgrid.BlockPos;
import sh.harold.creative.library.spatial.SpaceId;

import java.util.Objects;

public final class PaperBlockGrid {

    private PaperBlockGrid() {
    }

    public static SpaceId spaceId(World world) {
        Objects.requireNonNull(world, "world");
        return SpaceId.of(world.getKey().getNamespace(), world.getKey().getKey());
    }

    public static BlockPos blockPos(Block block) {
        Objects.requireNonNull(block, "block");
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }

    public static BlockPos blockPos(Location location) {
        Objects.requireNonNull(location, "location");
        return new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
