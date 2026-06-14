package sh.harold.library.trajectory;

import sh.harold.library.spatial.Segment3;

import java.util.Optional;

@FunctionalInterface
public interface CollisionQuery {

    Optional<CollisionHit> sweep(Segment3 segment, double radius);
}
