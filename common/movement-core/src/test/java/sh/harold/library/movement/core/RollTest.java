package sh.harold.library.movement.core;

import org.junit.jupiter.api.Test;
import sh.harold.library.movement.BodyState;
import sh.harold.library.movement.InputFrame;
import sh.harold.library.movement.MoveEvent;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.core.tuning.Moveset;
import sh.harold.library.spatial.Vec3;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 02 section 3 under the integrator. */
class RollTest {

    static final Moveset ROLL = Moveset.DEFAULT.withEnabled(Set.of(MoveKeys.ROLL, MoveKeys.JUMP));

    static Driver falling(double height, double speed) {
        BodyState body = new BodyState(new Vec3(0.5, height, 0.5), new Vec3(0, 0, speed), false, false, 0.0, 0.0f,
                0.0f, false, 0.6, 1.8, false);
        return new Driver(ROLL, TestWorld.flat(), body);
    }

    /** Falls, pressing sneak once {@code pressBefore} ticks before the predicted touchdown (negative: after). */
    static Driver rollFrom(double height, int pressBefore) {
        Driver probe = falling(height, 0.2);
        int landing = 0;
        while (!probe.body().onGround()) {
            probe.tick(InputFrame.EMPTY);
            landing++;
        }
        Driver driver = falling(height, 0.2);
        int pressTick = landing - pressBefore;
        for (int i = 0; i < landing + 30; i++) {
            driver.tick(i == pressTick ? Driver.keys("N") : InputFrame.EMPTY);
        }
        return driver;
    }

    @Test
    void rollStartsAtTouchdownWithReducedFallDamage() {
        Driver driver = rollFrom(8.0, 3);
        var start = driver.firstStart(MoveKeys.ROLL).orElseThrow();
        assertTrue(start.before().onGround(), "T0 is the touchdown tick");
        var claim = start.output().events().stream().filter(e -> e instanceof MoveEvent.Started)
                .map(e -> ((MoveEvent.Started) e).claim()).findFirst().orElseThrow();
        assertEquals(8.0, claim.fallDistance(), 0.05);
        assertTrue(start.output().events().stream().anyMatch(e -> e instanceof MoveEvent.FallDamageModifier m
                && m.blocks() == 10.0));
    }

    @Test
    void rollHoldsLowHitboxAndBoostedSpeedForTenTicks() {
        Driver driver = rollFrom(8.0, 2);
        long t0 = driver.firstStart(MoveKeys.ROLL).orElseThrow().tick();
        double expected = Math.max(0.2 * 0.91, 0.2806) * 1.15;
        for (var frame : driver.trace) {
            long k = frame.tick() - t0;
            if (k >= 0 && k < 10) {
                assertEquals(0.9, frame.after().height(), 1.0e-9, "T" + k + " hitbox");
                Vec3 delta = frame.after().position().subtract(frame.before().position());
                assertEquals(expected, Math.hypot(delta.x(), delta.z()), 0.02, "T" + k + " speed");
                assertEquals(0.2, frame.output().lookScale(), 1.0e-9);
            }
            if (k == 10) {
                assertEquals(1.8, frame.after().height(), 1.0e-9, "T10 standing");
            }
        }
    }

    @Test
    void latePressWithinTwoTicksStillRolls() {
        Driver driver = rollFrom(8.0, -2);
        var start = driver.firstStart(MoveKeys.ROLL).orElseThrow();
        var claim = ((MoveEvent.Started) start.output().events().stream()
                .filter(e -> e instanceof MoveEvent.Started).findFirst().orElseThrow()).claim();
        assertEquals(2, claim.pressTickOffset(), "pressed two ticks after the touchdown");
    }

    @Test
    void pressOutsideTheWindowGivesANormalLanding() {
        assertTrue(rollFrom(8.0, 9).firstStart(MoveKeys.ROLL).isEmpty(), "too early");
        assertTrue(rollFrom(8.0, -4).firstStart(MoveKeys.ROLL).isEmpty(), "too late");
        assertTrue(rollFrom(2.0, 2).firstStart(MoveKeys.ROLL).isEmpty(), "fall under 3 blocks");
    }

    @Test
    void holdingSneakThroughTheFallDoesNothing() {
        Driver driver = falling(8.0, 0.2);
        driver.tick(InputFrame.EMPTY);
        driver.run(Driver.keys("N"), 40);
        assertTrue(driver.firstStart(MoveKeys.ROLL).isEmpty());
    }

    @Test
    void thirteenBlockFallDoesNoDamageAfterTheRoll() {
        Driver driver = rollFrom(13.0, 2);
        var start = driver.firstStart(MoveKeys.ROLL).orElseThrow();
        var claim = ((MoveEvent.Started) start.output().events().getFirst()).claim();
        double reduced = claim.fallDistance() - 10.0;
        int damage = (int) Math.floor(reduced + 1.0e-6 - 3.0);
        assertEquals(0, Math.max(0, damage), "13 blocks: " + claim.fallDistance());
    }

    @Test
    void jumpBufferedLateInTheRollFiresAtT10() {
        Driver probe = falling(8.0, 0.2);
        int landing = 0;
        while (!probe.body().onGround()) {
            probe.tick(InputFrame.EMPTY);
            landing++;
        }
        Driver driver = falling(8.0, 0.2);
        for (int i = 0; i < landing + 30; i++) {
            InputFrame input = InputFrame.EMPTY;
            if (i == landing - 2) {
                input = Driver.keys("N");
            }
            if (i == landing + 8) {
                input = Driver.keys("J");
            }
            driver.tick(input);
        }
        long t0 = driver.firstStart(MoveKeys.ROLL).orElseThrow().tick();
        var t10 = driver.trace.stream().filter(frame -> frame.tick() == t0 + 10).findFirst().orElseThrow();
        assertTrue(t10.output().jump(), "roll-jump at T10");
        double speed = Math.hypot(t10.output().velocityX().value(), t10.output().velocityZ().value());
        assertEquals(0.2806 * 1.15, speed, 0.01, "keeps the boosted speed");
    }
}
