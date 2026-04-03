package sh.harold.creative.library.blockgrid;

import java.util.Objects;

public record BlockPos(int x, int y, int z) {

    public BlockPos add(int dx, int dy, int dz) {
        return new BlockPos(
                Math.addExact(x, dx),
                Math.addExact(y, dy),
                Math.addExact(z, dz)
        );
    }

    public BlockPos subtract(int dx, int dy, int dz) {
        return new BlockPos(
                Math.subtractExact(x, dx),
                Math.subtractExact(y, dy),
                Math.subtractExact(z, dz)
        );
    }

    public BlockPos min(BlockPos other) {
        Objects.requireNonNull(other, "other");
        return new BlockPos(
                Math.min(x, other.x()),
                Math.min(y, other.y()),
                Math.min(z, other.z())
        );
    }

    public BlockPos max(BlockPos other) {
        Objects.requireNonNull(other, "other");
        return new BlockPos(
                Math.max(x, other.x()),
                Math.max(y, other.y()),
                Math.max(z, other.z())
        );
    }
}
