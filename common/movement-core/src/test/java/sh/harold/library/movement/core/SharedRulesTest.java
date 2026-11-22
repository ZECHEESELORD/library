package sh.harold.library.movement.core;

import org.junit.jupiter.api.Test;
import sh.harold.library.movement.BodyState;
import sh.harold.library.movement.InputFrame;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.MoveOutput;
import sh.harold.library.movement.core.tuning.Moveset;
import sh.harold.library.spatial.Vec3;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 02 "Rules shared by every move": the input buffer, coyote time, and an empty registry being vanilla. */
class SharedRulesTest {

    static final Moveset JUMP = Moveset.DEFAULT.withEnabled(Set.of(MoveKeys.JUMP));

    static Driver dropping(Moveset moveset, double height) {
        BodyState body = new BodyState(new Vec3(0.5, height, 0.5), Vec3.ZERO, false, false, 0.0, 0.0f, 0.0f, false,
                0.6, 1.8, false);
        return new Driver(moveset, TestWorld.flat(), body);
    }

    static int landingTicks(double height) {
        Driver probe = dropping(JUMP, height);
        int ticks = 0;
        while (!probe.body().onGround()) {
            probe.tick(InputFrame.EMPTY);
            ticks++;
        }
        return ticks;
    }

    /** Taps jump {@code early} ticks before the body is on the ground again; returns whether it jumped on landing. */
    static boolean bufferedJump(int early) {
        int landing = landingTicks(3.0);
        Driver driver = dropping(JUMP, 3.0);
        boolean jumped = false;
        for (int i = 0; i < landing + 3; i++) {
            MoveOutput output = driver.tick(i == landing - early ? Driver.keys("J") : InputFrame.EMPTY);
            if (i >= landing && output.jump()) {
                jumped = true;
            }
        }
        return jumped;
    }

    @Test
    void pressRememberedForFourTicks() {
        assertTrue(bufferedJump(1));
        assertTrue(bufferedJump(3));
        assertFalse(bufferedJump(5), "a press 5 ticks early is forgotten");
    }

    /** Walks off a ledge and taps jump {@code late} ticks after leaving the ground. */
    static boolean coyoteJump(int late) {
        TestWorld world = new TestWorld().fill(-3, -1, -3, 3, -1, 2).fill(-3, -10, -3, 3, -10, 30);
        Driver probe = new Driver(JUMP, world, BodyState.standing(new Vec3(0.5, 0.0, 1.5), 0.0f));
        probe.run(InputFrame.EMPTY, 2);
        int off = 0;
        while (probe.body().onGround() || probe.trace.stream().noneMatch(f -> f.after().onGround())) {
            probe.tick(Driver.keys("F"));
            off++;
        }
        // off = the number of walking ticks after which the body is first airborne.
        Driver driver = new Driver(JUMP, world, BodyState.standing(new Vec3(0.5, 0.0, 1.5), 0.0f));
        driver.run(InputFrame.EMPTY, 2);
        driver.run(Driver.keys("F"), off - 1 + late);
        return driver.tick(Driver.keys("FJ")).jump();
    }

    @Test
    void jumpWithinThreeTicksOfWalkingOffStillFires() {
        assertTrue(coyoteJump(1));
        assertTrue(coyoteJump(3));
        assertFalse(coyoteJump(5));
    }

    @Test
    void emptyMovesetIsExactlyVanilla() {
        TestWorld world = TestWorld.flat().fill(-2, 0, 6, 2, 0, 6);
        Driver driver = Driver.standing(Moveset.none(), world, new Vec3(0.5, 0.0, 0.5));
        ReferenceIntegrator vanilla = new ReferenceIntegrator();
        BodyState body = driver.body();
        String[] script = {"FS", "FS", "FSJ", "FSJ", "F", "FN", "FN", "", "J", "J", "J", "FL", "FR", "B"};
        for (int i = 0; i < 120; i++) {
            InputFrame input = Driver.keys(script[i % script.length]);
            MoveOutput output = driver.tick(input);
            assertEquals(MoveOutput.NONE, output);
            body = vanilla.step(body, input, MoveOutput.NONE, world, false);
            assertEquals(body.position(), driver.body().position(), "tick " + i);
            assertEquals(body.velocity(), driver.body().velocity(), "tick " + i);
        }
    }

    @Test
    void jumpMoveTapIsVanilla() {
        Driver core = Driver.standing(JUMP, TestWorld.flat(), new Vec3(0.5, 0.0, 0.5));
        Driver vanilla = Driver.standing(Moveset.none(), TestWorld.flat(), new Vec3(0.5, 0.0, 0.5));
        String[] script = {"FS", "FSJ", "FS", "FS", "FS", "FS", "FS", "FS", "FS", "FS", "FS", "FS", "FS", "FS", "FS"};
        for (String keys : script) {
            core.tick(Driver.keys(keys));
            vanilla.tick(Driver.keys(keys));
            assertEquals(vanilla.body().position().y(), core.body().position().y(), 1.0e-12);
            assertEquals(vanilla.body().position().z(), core.body().position().z(), 1.0e-12);
        }
    }

    @Test
    void exemptBodyEndsMovesAndStartsNone() {
        MovementEngine engine = MovementEngine.of(Moveset.DEFAULT);
        MovementState state = new MovementState();
        BodyState body = BodyState.standing(new Vec3(0.5, 0.0, 0.5), 0.0f).withExempt(true);
        MoveOutput output = engine.step(state, body, Driver.keys("FJ"), TestWorld.flat(), 0);
        assertTrue(output.passive());
        assertTrue(state.active().isEmpty());
    }
}
