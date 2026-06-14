package sh.harold.library.trajectory.core;

import sh.harold.library.spatial.Segment3;
import sh.harold.library.spatial.Vec3;
import sh.harold.library.trajectory.CollisionHit;
import sh.harold.library.trajectory.CollisionQuery;
import sh.harold.library.trajectory.CollisionResponseMode;
import sh.harold.library.trajectory.PreviewInvalidReason;
import sh.harold.library.trajectory.TrajectoryMotion;
import sh.harold.library.trajectory.TrajectoryPreviewResult;
import sh.harold.library.trajectory.TrajectorySolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class StandardTrajectorySolver implements TrajectorySolver {

    @Override
    public TrajectoryPreviewResult solve(TrajectoryMotion motion, CollisionQuery collisionQuery) {
        TrajectoryMotion value = Objects.requireNonNull(motion, "motion");
        CollisionQuery query = Objects.requireNonNull(collisionQuery, "collisionQuery");
        if (value.maxSimulationTicks() == 0L) {
            return new TrajectoryPreviewResult(
                    List.of(value.initialPosition()),
                    0.0,
                    0L,
                    Optional.empty(),
                    value.initialPosition(),
                    false,
                    PreviewInvalidReason.NO_STEPS
            );
        }

        List<Vec3> points = new ArrayList<>();
        points.add(value.initialPosition());
        Vec3 position = value.initialPosition();
        Vec3 velocity = value.initialVelocity();
        double totalDistance = 0.0;
        Optional<CollisionHit> firstHit = Optional.empty();

        for (long tick = 0L; tick < value.maxSimulationTicks(); tick++) {
            Vec3 nextPosition = position.add(velocity);
            Segment3 sweep = new Segment3(position, nextPosition);
            Optional<CollisionHit> hit = query.sweep(sweep, value.collisionRadius());
            if (hit.isPresent() && firstHit.isEmpty()) {
                firstHit = hit;
            }

            if (hit.isPresent() && value.collisionResponse() != CollisionResponseMode.PASS_THROUGH
                    && value.collisionResponse() != CollisionResponseMode.REPORT_ONLY) {
                CollisionHit collision = hit.get();
                totalDistance += position.distance(collision.position());
                points.add(collision.position());
                return new TrajectoryPreviewResult(
                        points,
                        totalDistance,
                        tick + 1L,
                        firstHit,
                        collision.position(),
                        true,
                        PreviewInvalidReason.NONE
                );
            }

            totalDistance += position.distance(nextPosition);
            position = nextPosition;
            points.add(position);
            velocity = applyDrag(velocity.add(value.acceleration()), value.drag());
        }

        return new TrajectoryPreviewResult(
                points,
                totalDistance,
                value.maxSimulationTicks(),
                firstHit,
                position,
                true,
                PreviewInvalidReason.NONE
        );
    }

    private static Vec3 applyDrag(Vec3 velocity, Vec3 drag) {
        return new Vec3(velocity.x() * drag.x(), velocity.y() * drag.y(), velocity.z() * drag.z());
    }
}
