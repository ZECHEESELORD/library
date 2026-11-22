package sh.harold.library.movement.core;

import net.kyori.adventure.key.Key;
import sh.harold.library.movement.BodyState;
import sh.harold.library.movement.InputFrame;
import sh.harold.library.movement.MoveEvent;
import sh.harold.library.movement.MoveOutput;
import sh.harold.library.movement.core.tuning.Moveset;
import sh.harold.library.spatial.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Drives a body tick by tick through the engine and the reference integrator, keeping a trace. */
final class Driver {

    record Frame(long tick, InputFrame input, BodyState before, MoveOutput output, BodyState after) {
    }

    final TestWorld world;
    final MovementSimulation simulation;
    MovementSimulation.Snapshot snapshot;
    long tick;
    final List<Frame> trace = new ArrayList<>();

    Driver(Moveset moveset, TestWorld world, BodyState body) {
        this.world = world;
        this.simulation = new MovementSimulation(MovementEngine.of(moveset));
        this.snapshot = MovementSimulation.Snapshot.start(body);
    }

    static Driver standing(Moveset moveset, TestWorld world, Vec3 feet) {
        Driver driver = new Driver(moveset, world, BodyState.standing(feet, 0.0f));
        driver.run(InputFrame.EMPTY, 3);
        driver.trace.clear();
        return driver;
    }

    BodyState body() {
        return snapshot.body();
    }

    MoveOutput tick(InputFrame input) {
        BodyState before = snapshot.body();
        snapshot = simulation.step(snapshot, input, world, tick);
        trace.add(new Frame(tick, input, before, snapshot.output(), snapshot.body()));
        tick++;
        return snapshot.output();
    }

    void run(InputFrame input, int ticks) {
        for (int i = 0; i < ticks; i++) {
            tick(input);
        }
    }

    /** Runs until the body is on the ground again, up to {@code max} ticks. */
    void runUntilGrounded(InputFrame input, int max) {
        for (int i = 0; i < max; i++) {
            tick(input);
            if (body().onGround()) {
                return;
            }
        }
    }

    Optional<Frame> firstStart(Key move) {
        return trace.stream().filter(frame -> frame.output().events().stream()
                .anyMatch(event -> event instanceof MoveEvent.Started started && started.move().equals(move))).findFirst();
    }

    Optional<Frame> firstEnd(Key move) {
        return trace.stream().filter(frame -> frame.output().events().stream()
                .anyMatch(event -> event instanceof MoveEvent.Ended ended && ended.move().equals(move))).findFirst();
    }

    static InputFrame keys(String compact) {
        return new InputFrame(compact.contains("F"), compact.contains("B"), compact.contains("L"),
                compact.contains("R"), compact.contains("J"), compact.contains("N"), compact.contains("S"));
    }
}
