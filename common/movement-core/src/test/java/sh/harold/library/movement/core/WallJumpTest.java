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

/** 02 section 5 under the integrator. */
class WallJumpTest {

    static final Moveset WALLS = Moveset.DEFAULT.withEnabled(Set.of(MoveKeys.WALL_JUMP, MoveKeys.JUMP));

    /** A shaft {@code width} blocks wide along x (walls at x=-1 and x=width), the body against the west wall. */
    static Driver inShaft(int width) {
        TestWorld world = TestWorld.flat().fill(-1, 0, -3, -1, 40, 3).fill(width, 0, -3, width, 40, 3);
        BodyState body = BodyState.standing(new Vec3(0.35, 0.0, 0.5), 0.0f);
        Driver driver = new Driver(WALLS, world, body);
        driver.run(InputFrame.EMPTY, 3);
        driver.trace.clear();
        return driver;
    }

    static long kicks(Driver driver) {
        return driver.trace.stream().filter(frame -> frame.output().events().stream()
                .anyMatch(e -> e instanceof MoveEvent.Started s && s.move().equals(MoveKeys.WALL_JUMP))).count();
    }

    @Test
    void kickLeavesTheWallWithTheStatedSpeeds() {
        Driver driver = inShaft(4);
        driver.tick(Driver.keys("J"));
        driver.run(InputFrame.EMPTY, 3);
        var kick = driver.tick(Driver.keys("J"));
        assertEquals(1, kicks(driver));
        assertEquals(0.48, kick.velocityY().value(), 1.0e-9);
        assertEquals(0.32, kick.velocityX().value(), 1.0e-9, "straight away from the west wall");
        assertEquals(0.0, kick.velocityZ().value(), 1.0e-9);
        assertTrue(kick.events().stream().anyMatch(e -> e instanceof MoveEvent.FallDistanceReset));
    }

    @Test
    void kickApexIsAboutOnePointSixAboveTheKick() {
        Driver driver = inShaft(4);
        driver.tick(Driver.keys("J"));
        driver.run(InputFrame.EMPTY, 3);
        double kickY = driver.body().position().y();
        driver.tick(Driver.keys("J"));
        double apex = kickY;
        for (int i = 0; i < 12; i++) {
            driver.tick(InputFrame.EMPTY);
            apex = Math.max(apex, driver.body().position().y());
        }
        assertEquals(1.59, apex - kickY, 0.02, "02: apex 1.59 above the kick point");
    }

    @Test
    void tooSoonAfterTakeoffNoKick() {
        Driver driver = inShaft(4);
        driver.tick(Driver.keys("J"));
        driver.tick(InputFrame.EMPTY);
        driver.tick(Driver.keys("J"));
        assertEquals(0, kicks(driver), "airborne under 3 ticks");
    }

    @Test
    void facingWallsClimbWithoutLimit() {
        Driver driver = inShaft(2);
        driver.tick(Driver.keys("J"));
        driver.run(InputFrame.EMPTY, 3);
        double start = driver.body().position().y();
        for (int i = 0; i < 160 && kicks(driver) < 8; i++) {
            boolean touching = sh.harold.library.movement.core.moves.Probe.wall(driver.world, driver.body(), 0.1, 1.5)
                    .isPresent();
            driver.tick(touching ? Driver.keys("J") : InputFrame.EMPTY);
            driver.tick(InputFrame.EMPTY);
        }
        assertEquals(8, kicks(driver), "alternating faces of a 2-wide shaft");
        assertTrue(driver.body().position().y() > start + 4.0, "gains height: " + driver.body().position().y());
    }

    @Test
    void singleWallGivesOneKick() {
        TestWorld world = TestWorld.flat().fill(-1, 0, -3, -1, 40, 3);
        Driver driver = new Driver(WALLS, world, BodyState.standing(new Vec3(0.35, 0.0, 0.5), 90.0f));
        driver.run(InputFrame.EMPTY, 3);
        driver.trace.clear();
        driver.tick(Driver.keys("J"));
        driver.run(InputFrame.EMPTY, 3);
        driver.tick(Driver.keys("J"));
        assertEquals(1, kicks(driver));
        for (int i = 0; i < 20 && !driver.body().onGround(); i++) {
            driver.tick(Driver.keys("F"));
            driver.tick(Driver.keys("FJ"));
        }
        assertEquals(1, kicks(driver), "same face again before landing");
    }

    @Test
    void wallSlideCapsFallSpeedForTwentyFourTicks() {
        TestWorld world = TestWorld.flat().fill(-1, 0, -3, -1, 60, 3);
        BodyState body = new BodyState(new Vec3(0.31, 40.0, 0.5), Vec3.ZERO, false, false, 0.0, 90.0f, 0.0f, false,
                0.6, 1.8, false);
        Driver driver = new Driver(WALLS, world, body);
        driver.run(Driver.keys("F"), 40);
        var start = driver.firstStart(MoveKeys.WALL_SLIDE).orElseThrow();
        long capped = driver.trace.stream().filter(frame -> frame.tick() >= start.tick())
                .filter(frame -> frame.after().position().y() - frame.before().position().y() >= -0.12 - 1.0e-9)
                .filter(frame -> frame.output().velocityY().mode() == sh.harold.library.movement.VelocityRule.Mode.MIN)
                .count();
        assertEquals(24, capped);
        assertTrue(driver.firstEnd(MoveKeys.WALL_SLIDE).isPresent());
    }
}
