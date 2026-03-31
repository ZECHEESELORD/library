package sh.harold.creative.library.trajectory;

import sh.harold.creative.library.spatial.Vec3;

import java.util.Objects;

public record TrajectoryMotion(
        Vec3 initialPosition,
        Vec3 initialVelocity,
        Vec3 acceleration,
        Vec3 drag,
        double collisionRadius,
        long maxSimulationTicks,
        CollisionResponseMode collisionResponse
) {

    public TrajectoryMotion {
        initialPosition = Objects.requireNonNull(initialPosition, "initialPosition");
        initialVelocity = Objects.requireNonNull(initialVelocity, "initialVelocity");
        acceleration = Objects.requireNonNull(acceleration, "acceleration");
        drag = Objects.requireNonNull(drag, "drag");
        if (!Double.isFinite(collisionRadius) || collisionRadius < 0.0) {
            throw new IllegalArgumentException("collisionRadius must be finite and non-negative");
        }
        if (maxSimulationTicks < 0L) {
            throw new IllegalArgumentException("maxSimulationTicks cannot be negative");
        }
        collisionResponse = Objects.requireNonNull(collisionResponse, "collisionResponse");
    }
}
