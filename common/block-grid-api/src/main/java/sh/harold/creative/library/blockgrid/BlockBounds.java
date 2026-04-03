package sh.harold.creative.library.blockgrid;

import sh.harold.creative.library.spatial.Bounds3;
import sh.harold.creative.library.spatial.Vec3;

import java.util.Objects;

public record BlockBounds(BlockPos min, BlockPos max) {

    public BlockBounds {
        min = Objects.requireNonNull(min, "min");
        max = Objects.requireNonNull(max, "max");
        if (max.x() < min.x() || max.y() < min.y() || max.z() < min.z()) {
            throw new IllegalArgumentException("max must be greater than or equal to min on every axis");
        }
    }

    public static BlockBounds point(BlockPos point) {
        return new BlockBounds(point, point);
    }

    public static BlockBounds between(BlockPos first, BlockPos second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return new BlockBounds(first.min(second), first.max(second));
    }

    public boolean contains(BlockPos block) {
        Objects.requireNonNull(block, "block");
        return block.x() >= min.x() && block.x() <= max.x()
                && block.y() >= min.y() && block.y() <= max.y()
                && block.z() >= min.z() && block.z() <= max.z();
    }

    public boolean contains(BlockBounds other) {
        Objects.requireNonNull(other, "other");
        return other.min.x() >= min.x()
                && other.max.x() <= max.x()
                && other.min.y() >= min.y()
                && other.max.y() <= max.y()
                && other.min.z() >= min.z()
                && other.max.z() <= max.z();
    }

    public boolean intersects(BlockBounds other) {
        Objects.requireNonNull(other, "other");
        return min.x() <= other.max.x() && max.x() >= other.min.x()
                && min.y() <= other.max.y() && max.y() >= other.min.y()
                && min.z() <= other.max.z() && max.z() >= other.min.z();
    }

    public BlockPos clamp(BlockPos block) {
        Objects.requireNonNull(block, "block");
        return new BlockPos(
                clampAxis(block.x(), min.x(), max.x()),
                clampAxis(block.y(), min.y(), max.y()),
                clampAxis(block.z(), min.z(), max.z())
        );
    }

    public BlockBounds translate(BlockPos delta) {
        Objects.requireNonNull(delta, "delta");
        return translate(delta.x(), delta.y(), delta.z());
    }

    public BlockBounds translate(int dx, int dy, int dz) {
        return new BlockBounds(min.add(dx, dy, dz), max.add(dx, dy, dz));
    }

    public BlockBounds expand(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        if (amount == 0) {
            return this;
        }
        return new BlockBounds(
                min.subtract(amount, amount, amount),
                max.add(amount, amount, amount)
        );
    }

    public long sizeX() {
        return ((long) max.x() - (long) min.x()) + 1L;
    }

    public long sizeY() {
        return ((long) max.y() - (long) min.y()) + 1L;
    }

    public long sizeZ() {
        return ((long) max.z() - (long) min.z()) + 1L;
    }

    public long blockCountExact() {
        return Math.multiplyExact(Math.multiplyExact(sizeX(), sizeY()), sizeZ());
    }

    public BlockBounds union(BlockBounds other) {
        Objects.requireNonNull(other, "other");
        return new BlockBounds(min.min(other.min()), max.max(other.max()));
    }

    public Bounds3 toBounds3() {
        return new Bounds3(
                new Vec3(min.x(), min.y(), min.z()),
                new Vec3(max.x() + 1.0, max.y() + 1.0, max.z() + 1.0)
        );
    }

    private static int clampAxis(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
