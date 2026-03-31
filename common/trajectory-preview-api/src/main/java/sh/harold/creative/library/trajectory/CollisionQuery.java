package sh.harold.creative.library.trajectory;

import sh.harold.creative.library.spatial.Segment3;

import java.util.Optional;

@FunctionalInterface
public interface CollisionQuery {

    Optional<CollisionHit> sweep(Segment3 segment, double radius);
}
