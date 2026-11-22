package sh.harold.library.movement.core;

import sh.harold.library.movement.Aabb;
import sh.harold.library.movement.MovementWorld;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A block world for tests: a set of full unit blocks. */
final class TestWorld implements MovementWorld {

    private final Set<Long> blocks = new HashSet<>();

    static TestWorld flat() {
        TestWorld world = new TestWorld();
        world.fill(-40, -1, -40, 40, -1, 120);
        return world;
    }

    TestWorld set(int x, int y, int z) {
        blocks.add(pack(x, y, z));
        return this;
    }

    TestWorld fill(int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    set(x, y, z);
                }
            }
        }
        return this;
    }

    @Override
    public List<Aabb> collisions(Aabb region) {
        List<Aabb> result = new ArrayList<>();
        int x0 = (int) Math.floor(region.minX()) - 1;
        int x1 = (int) Math.floor(region.maxX()) + 1;
        int y0 = (int) Math.floor(region.minY()) - 1;
        int y1 = (int) Math.floor(region.maxY()) + 1;
        int z0 = (int) Math.floor(region.minZ()) - 1;
        int z1 = (int) Math.floor(region.maxZ()) + 1;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    if (blocks.contains(pack(x, y, z))) {
                        Aabb block = Aabb.block(x, y, z);
                        if (block.intersects(region)) {
                            result.add(block);
                        }
                    }
                }
            }
        }
        return result;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }
}
