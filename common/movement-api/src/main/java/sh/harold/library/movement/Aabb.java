package sh.harold.library.movement;

import sh.harold.library.spatial.Vec3;

/**
 * An axis-aligned box in world blocks: the body's hitbox, a collision box from the world, or a probe region.
 */
public record Aabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    public Aabb {
        if (!(minX <= maxX && minY <= maxY && minZ <= maxZ)) {
            throw new IllegalArgumentException("min must not exceed max: " + minX + "," + minY + "," + minZ
                    + " / " + maxX + "," + maxY + "," + maxZ);
        }
    }

    /** A body box of {@code width} by {@code height} standing with its feet centred on {@code feet}. */
    public static Aabb ofBody(Vec3 feet, double width, double height) {
        double half = width / 2.0;
        return new Aabb(feet.x() - half, feet.y(), feet.z() - half, feet.x() + half, feet.y() + height, feet.z() + half);
    }

    /** The unit cube of the block at the given integer position. */
    public static Aabb block(int x, int y, int z) {
        return new Aabb(x, y, z, x + 1.0, y + 1.0, z + 1.0);
    }

    public Aabb move(double dx, double dy, double dz) {
        return new Aabb(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    public Aabb move(Vec3 delta) {
        return move(delta.x(), delta.y(), delta.z());
    }

    /** Grows the box towards the direction of {@code delta} only (a swept region). */
    public Aabb expandTowards(double dx, double dy, double dz) {
        return new Aabb(
                dx < 0 ? minX + dx : minX, dy < 0 ? minY + dy : minY, dz < 0 ? minZ + dz : minZ,
                dx > 0 ? maxX + dx : maxX, dy > 0 ? maxY + dy : maxY, dz > 0 ? maxZ + dz : maxZ);
    }

    public Aabb inflate(double amount) {
        return new Aabb(minX - amount, minY - amount, minZ - amount, maxX + amount, maxY + amount, maxZ + amount);
    }

    /** Strict overlap: boxes that only touch on a face do not intersect. */
    public boolean intersects(Aabb other) {
        return minX < other.maxX && maxX > other.minX
                && minY < other.maxY && maxY > other.minY
                && minZ < other.maxZ && maxZ > other.minZ;
    }

    public double width() {
        return maxX - minX;
    }

    public double height() {
        return maxY - minY;
    }

    public Vec3 bottomCenter() {
        return new Vec3((minX + maxX) / 2.0, minY, (minZ + maxZ) / 2.0);
    }
}
