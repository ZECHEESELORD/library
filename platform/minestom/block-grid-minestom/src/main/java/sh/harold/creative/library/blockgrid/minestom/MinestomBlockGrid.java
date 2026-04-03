package sh.harold.creative.library.blockgrid.minestom;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Instance;
import sh.harold.creative.library.blockgrid.BlockPos;
import sh.harold.creative.library.spatial.SpaceId;

import java.util.Objects;
import java.util.function.Function;

public final class MinestomBlockGrid {

    private MinestomBlockGrid() {
    }

    public static BlockPos blockPos(Point point) {
        Objects.requireNonNull(point, "point");
        return new BlockPos(point.blockX(), point.blockY(), point.blockZ());
    }

    public static SpaceId spaceId(Instance instance, Function<Instance, SpaceId> resolver) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(resolver, "resolver");
        return Objects.requireNonNull(resolver.apply(instance), "resolver returned null");
    }
}
