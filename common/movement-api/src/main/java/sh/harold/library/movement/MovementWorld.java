package sh.harold.library.movement;

import java.util.List;

/**
 * The host's collision query: the solid boxes the body would collide with. Moves probe ledges, walls, headroom and
 * floors through it, and the reference integrator collides against it. Implementations need only block collision
 * (entities are not obstacles for moves).
 */
@FunctionalInterface
public interface MovementWorld {

    /** An empty world: nothing to stand on. */
    MovementWorld EMPTY = region -> List.of();

    /** Every collision box that intersects {@code region}. */
    List<Aabb> collisions(Aabb region);

    /** Whether {@code box} overlaps no collision box. */
    default boolean free(Aabb box) {
        for (Aabb solid : collisions(box)) {
            if (solid.intersects(box)) {
                return false;
            }
        }
        return true;
    }

    /** The block friction under the feet ({@code 0.6} for ordinary blocks, {@code 0.98} for ice). */
    default double friction(Aabb body) {
        return 0.6;
    }
}
