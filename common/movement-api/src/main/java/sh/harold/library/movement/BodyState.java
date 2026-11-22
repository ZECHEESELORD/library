package sh.harold.library.movement;

import sh.harold.library.spatial.Vec3;

import java.util.Objects;

/**
 * The body as the host sees it at the start of a tick's movement, before any move runs: what vanilla's
 * {@code aiStep} has left in place for {@code travel}.
 *
 * @param position            feet position
 * @param velocity            delta movement, blocks per tick
 * @param onGround            whether the last move ended on the ground
 * @param horizontalCollision whether the last move was blocked horizontally
 * @param fallDistance        the host's fall distance (informational; the core keeps its own)
 * @param yaw                 view yaw in degrees (Minecraft's: 0 faces +Z, 90 faces -X)
 * @param pitch               view pitch in degrees, positive looks down
 * @param sprinting           the host's sprint flag
 * @param width               current hitbox width
 * @param height              current hitbox height
 * @param exempt              the body is in a state no move handles (flying, swimming, in a fluid, riding, gliding,
 *                            climbing, spectating, dead): active moves end and none start
 */
public record BodyState(
        Vec3 position,
        Vec3 velocity,
        boolean onGround,
        boolean horizontalCollision,
        double fallDistance,
        float yaw,
        float pitch,
        boolean sprinting,
        double width,
        double height,
        boolean exempt
) {

    public static final double PLAYER_WIDTH = 0.6;
    public static final double PLAYER_HEIGHT = 1.8;
    public static final double PLAYER_EYE_HEIGHT = 1.62;

    public BodyState {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(velocity, "velocity");
    }

    /** A standing player body at rest. */
    public static BodyState standing(Vec3 feet, float yaw) {
        return new BodyState(feet, Vec3.ZERO, true, false, 0.0, yaw, 0.0f, false, PLAYER_WIDTH, PLAYER_HEIGHT, false);
    }

    public Aabb box() {
        return Aabb.ofBody(position, width, height);
    }

    public double horizontalSpeed() {
        return Math.hypot(velocity.x(), velocity.z());
    }

    /** The horizontal unit vector the view faces. */
    public Vec3 facing() {
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
    }

    /** The horizontal unit vector to the view's right. */
    public Vec3 right() {
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.cos(radians), 0.0, -Math.sin(radians));
    }

    public BodyState withPosition(Vec3 value) {
        return new BodyState(value, velocity, onGround, horizontalCollision, fallDistance, yaw, pitch, sprinting,
                width, height, exempt);
    }

    public BodyState withVelocity(Vec3 value) {
        return new BodyState(position, value, onGround, horizontalCollision, fallDistance, yaw, pitch, sprinting,
                width, height, exempt);
    }

    public BodyState withYaw(float value) {
        return new BodyState(position, velocity, onGround, horizontalCollision, fallDistance, value, pitch, sprinting,
                width, height, exempt);
    }

    public BodyState withSprinting(boolean value) {
        return new BodyState(position, velocity, onGround, horizontalCollision, fallDistance, yaw, pitch, value,
                width, height, exempt);
    }

    public BodyState withExempt(boolean value) {
        return new BodyState(position, velocity, onGround, horizontalCollision, fallDistance, yaw, pitch, sprinting,
                width, height, value);
    }
}
