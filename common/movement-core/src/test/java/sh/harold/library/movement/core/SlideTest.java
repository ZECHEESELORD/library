package sh.harold.library.movement.core;

import org.junit.jupiter.api.Test;
import sh.harold.library.movement.MoveEnd;
import sh.harold.library.movement.MoveEvent;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.SlideVariant;
import sh.harold.library.movement.core.tuning.Moveset;
import sh.harold.library.spatial.Vec3;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 02 section 4 under the integrator. */
class SlideTest {

    static Driver sprinting(Moveset moveset, TestWorld world) {
        Driver driver = Driver.standing(moveset, world, new Vec3(0.5, 0.0, 0.5));
        driver.run(Driver.keys("FS"), 40);
        driver.trace.clear();
        return driver;
    }

    static Moveset slideOnly(SlideVariant variant) {
        return Moveset.DEFAULT.withEnabled(Set.of(MoveKeys.SLIDE, MoveKeys.JUMP)).withSlideVariant(variant);
    }

    /** Per-tick horizontal displacement from the slide's T0 until it ends. */
    static List<Double> slideSteps(Driver driver) {
        var start = driver.firstStart(MoveKeys.SLIDE).orElseThrow();
        var end = driver.firstEnd(MoveKeys.SLIDE).orElseThrow();
        return driver.trace.stream()
                .filter(frame -> frame.tick() >= start.tick() && frame.tick() < end.tick())
                .map(frame -> frame.after().position().subtract(frame.before().position()))
                .map(delta -> Math.hypot(delta.x(), delta.z()))
                .toList();
    }

    /** S1: a flat slide from a sprint ends on the 0.19 speed floor after 16 ticks and 4.21 blocks. */
    @Test
    void flatSlideEndsOnTheSpeedFloor() {
        Driver driver = sprinting(slideOnly(SlideVariant.S1), TestWorld.flat());
        driver.run(Driver.keys("FSN"), 40);
        List<Double> steps = slideSteps(driver);
        assertEquals(0.35075, steps.getFirst(), 0.002, "T0 speed from a plain sprint");
        assertEquals(16, steps.size(), "16 ticks");
        double total = steps.stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(4.21, total, 0.01, "4.21 blocks");
        assertEquals(0.19014, steps.getLast(), 0.001, "the last slide tick still moves at the floor");
        assertEquals(0.1825, steps.getLast() * 0.96, 0.001, "the speed that ends it");
        var end = driver.firstEnd(MoveKeys.SLIDE).orElseThrow();
        assertTrue(end.output().events().stream().anyMatch(e -> e instanceof MoveEvent.Ended ended
                && ended.reason() == MoveEnd.EndReason.CONDITION));
    }

    @Test
    void slideDropsTheHitboxAndLocksDirection() {
        Driver driver = sprinting(slideOnly(SlideVariant.S1), TestWorld.flat());
        driver.run(Driver.keys("FSN"), 5);
        var start = driver.firstStart(MoveKeys.SLIDE).orElseThrow();
        assertEquals(0.6, start.after().height(), 1.0e-9);
        assertEquals(0.6, start.output().body().orElseThrow().height(), 1.0e-9);
        assertTrue(start.output().camera().stream().anyMatch(c -> c instanceof sh.harold.library.movement.CameraCommand.Start));
    }

    @Test
    void releasingSneakAfterT6EndsTheSlide() {
        Driver driver = sprinting(slideOnly(SlideVariant.S1), TestWorld.flat());
        driver.run(Driver.keys("FSN"), 8);
        driver.run(Driver.keys("FS"), 2);
        var end = driver.firstEnd(MoveKeys.SLIDE).orElseThrow();
        long t0 = driver.firstStart(MoveKeys.SLIDE).orElseThrow().tick();
        assertEquals(8, end.tick() - t0);
        assertTrue(end.output().events().stream().anyMatch(e -> e instanceof MoveEvent.Ended ended
                && ended.reason() == MoveEnd.EndReason.CANCELLED));
    }

    @Test
    void slideNeedsGroundSpeed() {
        Driver driver = Driver.standing(slideOnly(SlideVariant.S1), TestWorld.flat(), new Vec3(0.5, 0.0, 0.5));
        driver.run(Driver.keys("F"), 20);
        driver.run(Driver.keys("FN"), 5);
        assertTrue(driver.firstStart(MoveKeys.SLIDE).isEmpty(), "walking speed is below 0.25");
    }

    @Test
    void slidesUnderAOneBlockGapAndCrawlsOut() {
        TestWorld world = TestWorld.flat().fill(-2, 1, 7, 2, 1, 12);
        Driver driver = Driver.standing(slideOnly(SlideVariant.S1), world, new Vec3(0.5, 0.0, 0.5));
        driver.run(Driver.keys("FS"), 15);
        driver.trace.clear();
        double startZ = driver.body().position().z();
        assertTrue(startZ < 6.0, "starts before the duct: z=" + startZ);
        driver.run(Driver.keys("FSN"), 100);
        assertTrue(driver.body().position().z() > 13.3, "came out the far side: z=" + driver.body().position().z());
        assertTrue(driver.trace.stream().anyMatch(frame -> "crawl".equals(frame.output().status().phase())),
                "crawls when the slide ends under the gap");
        assertTrue(driver.trace.stream().noneMatch(frame -> frame.after().height() > 0.6
                && frame.after().position().z() > 6.75 && frame.after().position().z() < 13.25));
    }

    @Test
    void slideJumpFromT4KeepsSpeedPlusBonus() {
        Driver driver = sprinting(slideOnly(SlideVariant.S1), TestWorld.flat());
        driver.run(Driver.keys("FSN"), 5);
        var output = driver.tick(Driver.keys("FSNJ"));
        assertTrue(output.jump());
        assertTrue(output.discardFriction(), "S4: the launch tick skips ground friction");
        assertEquals(0.0, output.inputScale(), 1.0e-12);
        double speed = Math.hypot(output.velocityX().value(), output.velocityZ().value());
        assertTrue(speed > 0.35 * Math.pow(0.96, 5) + 0.09, "slide speed plus 0.1: " + speed);
        assertTrue(driver.firstEnd(MoveKeys.SLIDE).isPresent());
    }

    /** S3: an immediate second slide is never refused; its boost has only refilled for the ticks since the first. */
    @Test
    void secondSlideStartsAtOnceWithAPartlyRefilledBoost() {
        Driver driver = sprinting(slideOnly(SlideVariant.S1), TestWorld.flat());
        driver.run(Driver.keys("FSN"), 8);
        driver.run(Driver.keys("FS"), 1);
        long ended = driver.firstEnd(MoveKeys.SLIDE).orElseThrow().tick();
        driver.run(Driver.keys("FS"), 2);
        driver.run(Driver.keys("FSN"), 2);
        var starts = driver.trace.stream().filter(frame -> frame.output().events().stream()
                .anyMatch(e -> e instanceof MoveEvent.Started s && s.move().equals(MoveKeys.SLIDE))).toList();
        assertEquals(2, starts.size(), "no lockout");
        var second = starts.get(1);
        long since = second.tick() - ended;
        var before = driver.trace.stream().filter(f -> f.tick() == second.tick() - 1).findFirst().orElseThrow();
        double entry = Math.hypot(before.after().position().x() - before.before().position().x(),
                before.after().position().z() - before.before().position().z());
        double refill = Math.min(1.0, since / 20.0);
        double expected = Math.max(entry, 0.2806) * (1.0 + 0.25 * refill);
        double t0 = Math.hypot(second.output().velocityX().value(), second.output().velocityZ().value());
        assertEquals(expected, t0, 1.0e-9, "boost x" + (1.0 + 0.25 * refill) + " after " + since + " ticks");
        assertEquals((4.0 + 4.0 * refill) / 5.0, second.output().cameraSignals().fov(), 1.0e-9,
                "T0 FOV is a fifth of the kick 4 + 4r");
    }

    @Test
    void s2ChordCancelsTheJumpIntoASlide() {
        Driver driver = sprinting(slideOnly(SlideVariant.S2), TestWorld.flat());
        var jump = driver.tick(Driver.keys("FSJ"));
        assertTrue(jump.jump(), "S2: the jump fires at once");
        driver.tick(Driver.keys("FSJN"));
        assertTrue(driver.firstStart(MoveKeys.SLIDE).isPresent(), "sneak within 2 ticks turns it into a slide");
        driver.run(Driver.keys("FSN"), 3);
        assertTrue(driver.body().onGround(), "pulled back to the ground");
    }

    @Test
    void s2NeedsTheChordWindow() {
        Driver driver = sprinting(slideOnly(SlideVariant.S2), TestWorld.flat());
        driver.tick(Driver.keys("FSJ"));
        driver.run(Driver.keys("FS"), 3);
        driver.tick(Driver.keys("FSN"));
        assertFalse(driver.firstStart(MoveKeys.SLIDE).isPresent());
        Driver s1 = sprinting(slideOnly(SlideVariant.S2), TestWorld.flat());
        s1.run(Driver.keys("FSN"), 3);
        assertFalse(s1.firstStart(MoveKeys.SLIDE).isPresent(), "S2 ignores a lone sneak press");
    }
}
