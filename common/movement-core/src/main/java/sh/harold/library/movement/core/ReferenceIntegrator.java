package sh.harold.library.movement.core;

import sh.harold.library.movement.Aabb;
import sh.harold.library.movement.BodyOverride;
import sh.harold.library.movement.BodyState;
import sh.harold.library.movement.InputFrame;
import sh.harold.library.movement.MoveOutput;
import sh.harold.library.movement.MovementWorld;
import sh.harold.library.spatial.Vec3;

import java.util.List;

/**
 * A host-free copy of vanilla's player physics on land (26.3 {@code LivingEntity.aiStep}, {@code travelInAir} and
 * {@code Entity.move}), with a {@link MoveOutput} applied the way the host applies it. Used by unit tests to check 02's
 * numbers against the physics, and by the client to replay predicted ticks after a correction.
 *
 * <p>Per tick: the jump cooldown counts down; tiny speeds are zeroed (horizontal below 0.003, vertical below 0.003);
 * input is scaled by 0.98 and by 0.3 when moving slowly; vanilla's own ground jump fires unless the core owns jumps;
 * then the output (jump impulse, velocity rules, kinematic target, input scale); then {@code travelInAir}: acceleration
 * ({@code speed * 0.216 / f^3} on the ground, 0.02 or 0.026 sprinting in the air), the collision move (Y first, then the
 * larger horizontal axis, 0.6 step-up), then gravity and friction ({@code f * 0.91} horizontally, 0.98 vertically).
 * Not modelled: fluids, climbing, effects, block speed factors, entity collisions, the sneak edge guard.
 */
public final class ReferenceIntegrator {

    public static final double GRAVITY = 0.08;
    public static final double WALK_SPEED = 0.1;
    public static final double SPRINT_FACTOR = 1.3;
    public static final double SNEAK_FACTOR = 0.3;
    public static final double INPUT_FACTOR = 0.98;
    public static final double AIR_ACCELERATION = 0.02;
    public static final double SPRINT_AIR_ACCELERATION = 0.025999999;
    public static final double STEP_HEIGHT = 0.6;
    public static final double JUMP_POWER = 0.42;
    public static final double SPRINT_JUMP_BOOST = 0.2;
    private static final double EPSILON = 1.0e-7;

    private int noJumpDelay;

    public ReferenceIntegrator() {
    }

    public ReferenceIntegrator copy() {
        ReferenceIntegrator copy = new ReferenceIntegrator();
        copy.noJumpDelay = noJumpDelay;
        return copy;
    }

    public int noJumpDelay() {
        return noJumpDelay;
    }

    public void setNoJumpDelay(int value) {
        this.noJumpDelay = value;
    }

    /**
     * The height a fall from rest needs before its fall speed reaches {@code speed} under vanilla's vertical
     * integration ({@code y += vy; vy = (vy - 0.08) * 0.98}): about 0.08 at 0.12 b/t, 0.23 at 0.2 and 0.46 at 0.3.
     */
    public static double equivalentFallHeight(double speed) {
        double target = Math.abs(speed);
        double y = 0.0;
        double vy = 0.0;
        int guard = 0;
        while (-vy < target && guard++ < 10_000) {
            y += vy;
            vy = (vy - GRAVITY) * 0.98;
        }
        return -y;
    }

    /**
     * One tick.
     *
     * @param ownsJump whether the core owns ground jumps (the jump move is enabled), so vanilla's is suppressed
     */
    public BodyState step(BodyState body, InputFrame input, MoveOutput out, MovementWorld world, boolean ownsJump) {
        if (body.exempt()) {
            return body;
        }
        if (noJumpDelay > 0) {
            noJumpDelay--;
        }
        double vx = body.velocity().x();
        double vy = body.velocity().y();
        double vz = body.velocity().z();
        if (vx * vx + vz * vz < 9.0e-6) {
            vx = 0.0;
            vz = 0.0;
        }
        if (Math.abs(vy) < 0.003) {
            vy = 0.0;
        }
        BodyOverride override = out.body().orElse(null);
        double height = override != null ? override.height() : BodyState.PLAYER_HEIGHT;
        boolean slow = input.sneak() || (override != null && override.pose() != BodyOverride.RenderPose.STANDING);
        double forward = input.forwardImpulse() * INPUT_FACTOR;
        double strafe = input.strafeImpulse() * INPUT_FACTOR;
        if (slow) {
            forward *= SNEAK_FACTOR;
            strafe *= SNEAK_FACTOR;
        }
        boolean sprinting = (body.sprinting() || input.sprint()) && input.forwardImpulse() > 0.0 && !slow
                && !out.cancelSprint();
        boolean jumped = false;
        if (!ownsJump) {
            if (input.jump()) {
                if (body.onGround() && noJumpDelay == 0) {
                    double[] v = jump(vx, vy, vz, body.yaw(), sprinting);
                    vx = v[0];
                    vy = v[1];
                    vz = v[2];
                    noJumpDelay = 10;
                    jumped = true;
                }
            } else {
                noJumpDelay = 0;
            }
        }
        if (out.jump() && !jumped) {
            double[] v = jump(vx, vy, vz, body.yaw(), sprinting);
            vx = v[0];
            vy = v[1];
            vz = v[2];
        }
        vx = out.velocityX().apply(vx);
        vy = out.velocityY().apply(vy);
        vz = out.velocityZ().apply(vz);
        double gravity = out.gravity().orElse(GRAVITY);
        boolean discard = out.discardFriction();
        if (out.kinematicTarget().isPresent()) {
            Vec3 delta = out.kinematicTarget().get().subtract(body.position());
            vx = delta.x();
            vy = delta.y();
            vz = delta.z();
            forward = 0.0;
            strafe = 0.0;
            gravity = 0.0;
            discard = true;
        }
        forward *= out.inputScale();
        strafe *= out.inputScale();

        Vec3 position = body.position();
        Aabb box = Aabb.ofBody(position, body.width(), height);
        double friction = body.onGround() ? world.friction(box) : 1.0;
        double speed = body.onGround()
                ? WALK_SPEED * (sprinting ? SPRINT_FACTOR : 1.0) * (0.21600002 / (friction * friction * friction))
                : sprinting ? SPRINT_AIR_ACCELERATION : AIR_ACCELERATION;
        double length = strafe * strafe + forward * forward;
        if (length >= 1.0e-7) {
            double scale = speed / Math.max(1.0, Math.sqrt(length));
            double sx = strafe * scale;
            double sz = forward * scale;
            double radians = Math.toRadians(body.yaw());
            double sin = Math.sin(radians);
            double cos = Math.cos(radians);
            vx += sx * cos - sz * sin;
            vz += sz * cos + sx * sin;
        }

        Move move = move(world, box, vx, vy, vz, body.onGround());
        Vec3 moved = position.add(new Vec3(move.dx, move.dy, move.dz));
        if (move.collidedX) {
            vx = 0.0;
        }
        if (move.collidedZ) {
            vz = 0.0;
        }
        if (move.collidedY) {
            vy = 0.0;
        }
        boolean onGround = move.collidedY && vyWanted(move) < 0.0;
        double fall = body.fallDistance();
        if (onGround) {
            fall = 0.0;
        } else if (move.dy < 0.0) {
            fall -= move.dy;
        }
        vy -= gravity;
        if (!discard) {
            double horizontal = friction * 0.91;
            vx *= horizontal;
            vy *= 0.98;
            vz *= horizontal;
        }
        return new BodyState(moved, new Vec3(vx, vy, vz), onGround, move.collidedX || move.collidedZ, fall,
                body.yaw(), body.pitch(), sprinting, body.width(), height, false);
    }

    private static double vyWanted(Move move) {
        return move.wantedY;
    }

    private static double[] jump(double vx, double vy, double vz, float yaw, boolean sprinting) {
        double y = Math.max(JUMP_POWER, vy);
        if (sprinting) {
            double radians = Math.toRadians(yaw);
            vx += -Math.sin(radians) * SPRINT_JUMP_BOOST;
            vz += Math.cos(radians) * SPRINT_JUMP_BOOST;
        }
        return new double[]{vx, y, vz};
    }

    private record Move(double dx, double dy, double dz, double wantedY, boolean collidedX, boolean collidedY,
                        boolean collidedZ) {
    }

    private static Move move(MovementWorld world, Aabb box, double dx, double dy, double dz, boolean wasOnGround) {
        List<Aabb> obstacles = world.collisions(box.expandTowards(dx, dy, dz).inflate(STEP_HEIGHT + 0.01));
        double[] plain = collide(obstacles, box, dx, dy, dz);
        boolean collidedX = Math.abs(plain[0] - dx) > EPSILON;
        boolean collidedY = Math.abs(plain[1] - dy) > EPSILON;
        boolean collidedZ = Math.abs(plain[2] - dz) > EPSILON;
        boolean landing = collidedY && dy < 0.0;
        if ((wasOnGround || landing) && (collidedX || collidedZ)) {
            double[] up = collide(obstacles, box, dx, STEP_HEIGHT, dz);
            double[] stepUp = collide(obstacles, box, 0.0, STEP_HEIGHT, 0.0);
            if (stepUp[1] < STEP_HEIGHT) {
                double[] across = collide(obstacles, box.move(0, stepUp[1], 0), dx, 0.0, dz);
                if (horizontal(across) > horizontal(up)) {
                    up = new double[]{across[0], stepUp[1], across[2]};
                }
            }
            if (horizontal(up) > horizontal(plain) + EPSILON) {
                double[] down = collide(obstacles, box.move(up[0], up[1], up[2]), 0.0, -up[1] + Math.min(dy, 0.0), 0.0);
                double[] stepped = {up[0], up[1] + down[1], up[2]};
                return new Move(stepped[0], stepped[1], stepped[2], dy, Math.abs(stepped[0] - dx) > EPSILON, true,
                        Math.abs(stepped[2] - dz) > EPSILON);
            }
        }
        return new Move(plain[0], plain[1], plain[2], dy, collidedX, collidedY, collidedZ);
    }

    private static double horizontal(double[] move) {
        return move[0] * move[0] + move[2] * move[2];
    }

    private static double[] collide(List<Aabb> obstacles, Aabb box, double dx, double dy, double dz) {
        double y = clipY(obstacles, box, dy);
        Aabb current = box.move(0, y, 0);
        double x;
        double z;
        if (Math.abs(dx) < Math.abs(dz)) {
            z = clipZ(obstacles, current, dz);
            current = current.move(0, 0, z);
            x = clipX(obstacles, current, dx);
        } else {
            x = clipX(obstacles, current, dx);
            current = current.move(x, 0, 0);
            z = clipZ(obstacles, current, dz);
        }
        return new double[]{x, y, z};
    }

    private static double clipY(List<Aabb> obstacles, Aabb box, double dy) {
        for (Aabb o : obstacles) {
            if (o.maxX() <= box.minX() + EPSILON || o.minX() >= box.maxX() - EPSILON
                    || o.maxZ() <= box.minZ() + EPSILON || o.minZ() >= box.maxZ() - EPSILON) {
                continue;
            }
            if (dy > 0.0 && o.minY() >= box.maxY() - EPSILON) {
                dy = Math.min(dy, o.minY() - box.maxY());
            } else if (dy < 0.0 && o.maxY() <= box.minY() + EPSILON) {
                dy = Math.max(dy, o.maxY() - box.minY());
            }
        }
        return dy;
    }

    private static double clipX(List<Aabb> obstacles, Aabb box, double dx) {
        for (Aabb o : obstacles) {
            if (o.maxY() <= box.minY() + EPSILON || o.minY() >= box.maxY() - EPSILON
                    || o.maxZ() <= box.minZ() + EPSILON || o.minZ() >= box.maxZ() - EPSILON) {
                continue;
            }
            if (dx > 0.0 && o.minX() >= box.maxX() - EPSILON) {
                dx = Math.min(dx, o.minX() - box.maxX());
            } else if (dx < 0.0 && o.maxX() <= box.minX() + EPSILON) {
                dx = Math.max(dx, o.maxX() - box.minX());
            }
        }
        return dx;
    }

    private static double clipZ(List<Aabb> obstacles, Aabb box, double dz) {
        for (Aabb o : obstacles) {
            if (o.maxY() <= box.minY() + EPSILON || o.minY() >= box.maxY() - EPSILON
                    || o.maxX() <= box.minX() + EPSILON || o.minX() >= box.maxX() - EPSILON) {
                continue;
            }
            if (dz > 0.0 && o.minZ() >= box.maxZ() - EPSILON) {
                dz = Math.min(dz, o.minZ() - box.maxZ());
            } else if (dz < 0.0 && o.maxZ() <= box.minZ() + EPSILON) {
                dz = Math.max(dz, o.maxZ() - box.minZ());
            }
        }
        return dz;
    }
}
