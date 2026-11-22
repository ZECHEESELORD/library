package sh.harold.library.movement.core;

import org.junit.jupiter.api.Test;
import sh.harold.library.movement.InputFrame;
import sh.harold.library.movement.JumpPreset;
import sh.harold.library.movement.MoveEnd;
import sh.harold.library.movement.MoveEvent;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.core.tuning.Moveset;
import sh.harold.library.spatial.Vec3;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 02 section 2 and the reach chart's mantle rows under the integrator. */
class MantleTest {

    static final Moveset MANTLE = Moveset.DEFAULT.withEnabled(Set.of(MoveKeys.MANTLE, MoveKeys.JUMP, MoveKeys.WALL_JUMP))
            .withJumpPreset(JumpPreset.A);

    /** A wall {@code height} blocks tall starting at z=2, the body facing it 0.35 from its face. */
    static Driver atWall(int height, boolean ceiling) {
        TestWorld world = TestWorld.flat().fill(-3, 0, 2, 3, height - 1, 30);
        if (ceiling) {
            world.fill(-3, height + 1, 2, 3, height + 1, 30);
        }
        return Driver.standing(MANTLE, world, new Vec3(0.5, 0.0, 1.35));
    }

    static long duration(Driver driver) {
        return driver.firstEnd(MoveKeys.MANTLE).orElseThrow().tick()
                - driver.firstStart(MoveKeys.MANTLE).orElseThrow().tick();
    }

    @Test
    void oneBlockLedgeVaultsInFiveTicks() {
        Driver driver = atWall(1, false);
        driver.tick(Driver.keys("FJ"));
        driver.run(Driver.keys("F"), 12);
        var start = driver.firstStart(MoveKeys.MANTLE).orElseThrow();
        assertEquals("vault_swing", start.output().status().phase());
        assertEquals(5, duration(driver));
        assertEquals(1.0, driver.body().position().y(), 0.01, "standing on the ledge");
        assertTrue(driver.body().position().z() > 2.0);
        assertTrue(driver.body().onGround());
    }

    @Test
    void tapJumpReachesATwoBlockLedgeByClimbing() {
        Driver driver = atWall(2, false);
        driver.tick(Driver.keys("FJ"));
        driver.run(Driver.keys("F"), 20);
        var start = driver.firstStart(MoveKeys.MANTLE).orElseThrow();
        assertTrue(start.output().status().phase().startsWith("climb"));
        assertEquals(9, duration(driver));
        assertEquals(2.0, driver.body().position().y(), 0.01);
        assertTrue(driver.body().onGround());
    }

    @Test
    void heldJumpReachesAThreeBlockLedgeButATapDoesNot() {
        Driver held = atWall(3, false);
        held.run(Driver.keys("FJ"), 12);
        held.run(Driver.keys("F"), 20);
        assertTrue(held.firstStart(MoveKeys.MANTLE).isPresent(), "held jump + mantle reaches 3 blocks");
        assertEquals(3.0, held.body().position().y(), 0.01);

        Driver tap = atWall(3, false);
        tap.tick(Driver.keys("FJ"));
        tap.run(Driver.keys("F"), 20);
        assertTrue(tap.firstStart(MoveKeys.MANTLE).isEmpty(), "a tap jump peaks at 1.25");
    }

    @Test
    void noHeadroomNoMantle() {
        Driver driver = atWall(1, true);
        driver.tick(Driver.keys("FJ"));
        driver.run(Driver.keys("F"), 12);
        assertTrue(driver.firstStart(MoveKeys.MANTLE).isEmpty());
    }

    @Test
    void sneakDuringTheHangLetsGo() {
        Driver driver = atWall(2, false);
        driver.tick(Driver.keys("FJ"));
        while (driver.firstStart(MoveKeys.MANTLE).isEmpty()) {
            driver.tick(Driver.keys("F"));
        }
        driver.tick(Driver.keys("FN"));
        var end = driver.firstEnd(MoveKeys.MANTLE).orElseThrow();
        assertTrue(end.output().events().stream().anyMatch(e -> e instanceof MoveEvent.Ended ended
                && ended.reason() == MoveEnd.EndReason.CANCELLED));
    }

    @Test
    void jumpDuringTheHangKicksOffTheWall() {
        Driver driver = atWall(2, false);
        driver.tick(Driver.keys("FJ"));
        while (driver.firstStart(MoveKeys.MANTLE).isEmpty()) {
            driver.tick(Driver.keys("F"));
        }
        var kick = driver.tick(Driver.keys("FJ"));
        assertTrue(driver.firstStart(MoveKeys.WALL_JUMP).isPresent(), "climb cancelled into a wall kick");
        assertEquals(0.48, kick.velocityY().value(), 1.0e-9);
        assertTrue(kick.velocityZ().value() < 0.0, "away from the wall");
    }

    @Test
    void climbTimelineFollowsTheTable() {
        Driver driver = atWall(2, false);
        driver.tick(Driver.keys("FJ"));
        driver.run(Driver.keys("F"), 20);
        long t0 = driver.firstStart(MoveKeys.MANTLE).orElseThrow().tick();
        double startY = driver.trace.stream().filter(f -> f.tick() == t0).findFirst().orElseThrow().before()
                .position().y();
        for (var frame : driver.trace) {
            long k = frame.tick() - t0;
            if (k >= 0 && k <= 2) {
                assertEquals(startY, frame.after().position().y(), 1.0e-6, "T" + k + " hangs");
            }
            if (k == 6) {
                assertEquals(2.0, frame.after().position().y(), 0.01, "T6 at the top");
            }
            if (k >= 0 && k < 7) {
                assertEquals(0.0, frame.output().inputScale() * (frame.output().kinematicTarget().isPresent() ? 0 : 1),
                        1.0e-9);
            }
        }
        assertTrue(driver.trace.stream().noneMatch(f -> f.input().equals(InputFrame.EMPTY)));
    }
}
