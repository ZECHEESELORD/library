package sh.harold.library.movement.core;

import sh.harold.library.movement.BodyState;
import sh.harold.library.movement.InputFrame;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.MoveOutput;
import sh.harold.library.movement.MovementWorld;

import java.util.Objects;

/**
 * The engine and the reference integrator together: one whole predicted tick, host-free. The client replays these
 * after a correction; tests drive whole moves with them.
 */
public final class MovementSimulation {

    /** One body's full simulated state at the end of a tick. */
    public record Snapshot(BodyState body, MovementState movement, int noJumpDelay, MoveOutput output) {

        public Snapshot {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(movement, "movement");
            Objects.requireNonNull(output, "output");
        }

        public static Snapshot start(BodyState body) {
            return new Snapshot(body, new MovementState(), 0, MoveOutput.NONE);
        }

        public Snapshot copy() {
            return new Snapshot(body, movement.copy(), noJumpDelay, output);
        }
    }

    private final MovementEngine engine;

    public MovementSimulation(MovementEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    public MovementEngine engine() {
        return engine;
    }

    /** Steps a copy of {@code from}; {@code from} is left untouched. */
    public Snapshot step(Snapshot from, InputFrame input, MovementWorld world, long tick) {
        MovementState state = from.movement().copy();
        MoveOutput output = engine.step(state, from.body(), input, world, tick);
        ReferenceIntegrator integrator = new ReferenceIntegrator();
        integrator.setNoJumpDelay(from.noJumpDelay());
        BodyState body = integrator.step(from.body(), input, output, world, engine.has(MoveKeys.JUMP));
        for (var event : output.events()) {
            if (event instanceof sh.harold.library.movement.MoveEvent.FallDistanceReset) {
                body = new BodyState(body.position(), body.velocity(), body.onGround(), body.horizontalCollision(),
                        0.0, body.yaw(), body.pitch(), body.sprinting(), body.width(), body.height(), body.exempt());
            }
        }
        return new Snapshot(body, state, integrator.noJumpDelay(), output);
    }
}
