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

    @Test
    void flatSlideMatchesTheFirstSixteenTicks() {
        Driver driver = sprinting(slideOnly(SlideVariant.S1), TestWorld.flat());
        driver.run(Driver.keys("FSN"), 40);
        List<Double> steps = slideSteps(driver);
        assertEquals(0.35, steps.getFirst(), 0.002, "T0 speed from a plain sprint");
        double sixteen = steps.subList(0, 16).stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(4.2, sixteen, 0.01, "02: a flat slide covers 4.2 blocks in 16 ticks");
        assertEquals(0.18, steps.get(16), 0.005, "02: ends at 0.18 b/t");
    }

    /**
     * Mismatch with 02: "covers 4.2 blocks in 16 ticks and ends at 0.18 b/t" holds for the first 16 ticks, but the
     * end rules in the same table (speed below 0.16, or 20 ticks on the flat) let a flat slide run 20 ticks, cover
     * about 4.88 blocks and stop from about 0.16 b/t. Reported, not tuned away.
     */
    @Test
    void reportsFlatSlideEndRuleMismatch() {
        Driver driver = sprinting(slideOnly(SlideVariant.S1), TestWorld.flat());
        driver.run(Driver.keys("FSN"), 40);
        List<Double> steps = slideSteps(driver);
        double total = steps.stream().mapToDouble(Double::doubleValue).sum();
        System.out.printf("MISMATCH 02 slide length: 02 says 16 ticks / 4.2 blocks / 0.18 at the end; end rules give "
                + "%d ticks / %.3f blocks / last speed %.3f%n", steps.size(), total, steps.getLast());
        assertEquals(20, steps.size());
        assertEquals(4.88, total, 0.02);
        assertTrue(Math.abs(total - 4.2) > 0.5);
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
        double speed = Math.hypot(output.velocityX().value(), output.velocityZ().value());
        assertTrue(speed > 0.35 * Math.pow(0.96, 5) + 0.09, "slide speed plus 0.1: " + speed);
        assertTrue(driver.firstEnd(MoveKeys.SLIDE).isPresent());
    }

    @Test
    void cooldownBlocksAnImmediateSecondSlide() {
        Driver driver = sprinting(slideOnly(SlideVariant.S1), TestWorld.flat());
        driver.run(Driver.keys("FSN"), 8);
        driver.run(Driver.keys("FS"), 1);
        long ended = driver.firstEnd(MoveKeys.SLIDE).orElseThrow().tick();
        driver.run(Driver.keys("FS"), 2);
        driver.run(Driver.keys("FSN"), 1);
        long starts = driver.trace.stream().filter(frame -> frame.output().events().stream()
                .anyMatch(e -> e instanceof MoveEvent.Started s && s.move().equals(MoveKeys.SLIDE))).count();
        assertEquals(1, starts, "cooldown after the slide ended at " + ended);
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
