package sh.harold.library.movement.core;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import sh.harold.library.camera.rig.CameraChannel;
import sh.harold.library.camera.rig.cue.CameraCue;
import sh.harold.library.movement.BodyState;
import sh.harold.library.movement.CameraCommand;
import sh.harold.library.movement.InputFrame;
import sh.harold.library.movement.JumpPreset;
import sh.harold.library.movement.MoveEvent;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.MoveOutput;
import sh.harold.library.movement.MoveSound;
import sh.harold.library.movement.SlideVariant;
import sh.harold.library.movement.core.camera.MoveCameras;
import sh.harold.library.movement.core.moves.Probe;
import sh.harold.library.movement.core.tuning.JumpTuning;
import sh.harold.library.movement.core.tuning.MantleTuning;
import sh.harold.library.movement.core.tuning.Moveset;
import sh.harold.library.movement.core.tuning.RollTuning;
import sh.harold.library.spatial.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 2026-11-23 movement rulings under the reference integrator, one test or more per ruling. */
class RulingsTest {

    static final Moveset ALL = Moveset.DEFAULT.withJumpPreset(JumpPreset.A);

    static Driver.Frame frame(Driver driver, long tick) {
        return driver.trace.stream().filter(f -> f.tick() == tick).findFirst().orElseThrow();
    }

    static double horizontal(Driver.Frame frame) {
        Vec3 d = frame.after().position().subtract(frame.before().position());
        return Math.hypot(d.x(), d.z());
    }

    static double outputSpeed(MoveOutput output) {
        return Math.hypot(output.velocityX().value(), output.velocityZ().value());
    }

    static Optional<CameraCue> started(MoveOutput output, Key key) {
        return output.camera().stream().filter(c -> c instanceof CameraCommand.Start start && start.cue().key().equals(key))
                .map(c -> ((CameraCommand.Start) c).cue()).findFirst();
    }

    /** The curve value with the largest magnitude over 0-20 ticks, signed. */
    static double peak(CameraCue cue, CameraChannel channel) {
        var track = TuningAndCameraTest.curve(cue, channel, false).track();
        double best = 0.0;
        for (double t = 0.0; t <= 20.0; t += 0.125) {
            double value = track.sample(t);
            if (Math.abs(value) > Math.abs(best)) {
                best = value;
            }
        }
        return best;
    }

    static boolean anyStarted(Driver driver, Key key) {
        return driver.trace.stream().anyMatch(f -> started(f.output(), key).isPresent());
    }

    /** A 1-block step from z=2, the body starting {@code fromZ} and running to it. */
    static TestWorld step(int height) {
        return TestWorld.flat().fill(-3, 0, 2, 3, height - 1, 30);
    }

    /** Runs {@code keys} until the hitbox front is within reach of the wall at z=2, then presses jump once. */
    static Driver runAndTap(Moveset moveset, TestWorld world, String keys) {
        Driver driver = Driver.standing(moveset, world, new Vec3(0.5, 0.0, -6.0));
        for (int i = 0; i < 60 && driver.body().position().z() < 1.25; i++) {
            driver.tick(Driver.keys(keys));
        }
        driver.tick(Driver.keys(keys + "J"));
        return driver;
    }

    // ---- Mantle ----

    /** MA5: a walking tap jump (sprint off) at a 1-block step is exactly the vanilla jump. */
    @Test
    void walkingTapAtAStepIsVanilla() {
        Driver moves = runAndTap(ALL, step(1), "F");
        moves.run(Driver.keys("F"), 15);
        assertTrue(moves.firstStart(MoveKeys.MANTLE).isEmpty(), "no mantle");
        assertEquals(1.0, moves.body().position().y(), 1.0e-6, "lands on the step");
        Driver vanilla = Driver.standing(Moveset.none(), step(1), new Vec3(0.5, 0.0, -6.0));
        for (Driver.Frame frame : moves.trace) {
            vanilla.tick(frame.input());
        }
        for (int i = 0; i < moves.trace.size(); i++) {
            assertEquals(vanilla.trace.get(i).after().position(), moves.trace.get(i).after().position(),
                    "tick " + i + " matches vanilla");
        }
    }

    /** MA6 + MA7: sprinting at a 1-block step vaults, and the exit keeps all the speed. */
    @Test
    void sprintVaultKeepsItsSpeed() {
        Driver driver = runAndTap(ALL, step(1), "FS");
        driver.run(Driver.keys("FS"), 12);
        var start = driver.firstStart(MoveKeys.MANTLE).orElseThrow();
        assertEquals("vault_swing", start.output().status().phase());
        var end = driver.firstEnd(MoveKeys.MANTLE).orElseThrow();
        assertEquals(5, end.tick() - start.tick(), "MA15: 5 ticks");
        double entry = horizontal(frame(driver, start.tick() - 1));
        double exit = outputSpeed(end.output());
        assertTrue(entry > 0.27, "entered at sprint speed: " + entry);
        assertEquals(Math.max(entry, 0.2806), exit, 1.0e-9, "exit = max(entry, 0.2806)");
        assertEquals(Math.max(entry, 0.2806), horizontal(frame(driver, end.tick())), 1.0e-9,
                "the exit tick moves at exactly the exit speed: no input push on top");
        for (long t = end.tick() + 1; t <= end.tick() + 3; t++) {
            assertTrue(horizontal(frame(driver, t)) > 0.27, "speed kept at tick " + (t - end.tick()));
        }
        assertEquals(1.0, driver.body().position().y(), 1.0e-6);
    }

    /** MA10 (vault): a jump pressed T3-T5 fires on the exit tick with the speed kept and no extra height. */
    @Test
    void vaultHopFiresOnTheExitTick() {
        Driver driver = runAndTap(ALL, step(1), "FS");
        long t0 = driver.firstStart(MoveKeys.MANTLE).orElseThrow().tick();
        while (driver.tick < t0 + 4) {
            driver.tick(Driver.keys("FS"));
        }
        driver.tick(Driver.keys("FSJ"));
        MoveOutput exit = driver.tick(Driver.keys("FS"));
        assertTrue(exit.jump(), "hop on T5");
        assertTrue(outputSpeed(exit) >= 0.2806 - 1.0e-9);
        assertEquals(outputSpeed(exit), horizontal(driver.trace.getLast()), 1.0e-9, "the hop tick moves at the exit speed");
        double apex = 0;
        for (int i = 0; i < 14; i++) {
            driver.tick(Driver.keys("FS"));
            apex = Math.max(apex, driver.body().position().y());
        }
        assertTrue(apex < 1.0 + 1.26, "a normal jump's height above the ledge: " + apex);
    }

    static Driver climbing(Moveset moveset) {
        Driver driver = Driver.standing(moveset, step(2), new Vec3(0.5, 0.0, 1.35));
        driver.tick(Driver.keys("FJ"));
        while (driver.firstStart(MoveKeys.MANTLE).isEmpty() && driver.tick < 20) {
            driver.tick(Driver.keys("F"));
        }
        return driver;
    }

    /** MA10 (climb): a jump pressed T5-T8 is a mantle hop at T7: a jump plus 0.1 forward and FOV +2. */
    @Test
    void climbHopAtT7() {
        Driver driver = climbing(ALL);
        long t0 = driver.firstStart(MoveKeys.MANTLE).orElseThrow().tick();
        while (driver.tick < t0 + 5) {
            driver.tick(Driver.keys("F"));
        }
        driver.tick(Driver.keys("FJ"));
        driver.run(Driver.keys("F"), 4);
        MoveOutput t7 = frame(driver, t0 + 7).output();
        assertTrue(t7.jump());
        assertEquals(0.2159 + 0.1, outputSpeed(t7), 1.0e-9);
        assertEquals(0.2159 + 0.1, horizontal(frame(driver, t0 + 7)), 1.0e-9, "T7 moves at the hop speed");
        CameraCue hop = started(t7, MoveCameras.MANTLE_HOP).orElseThrow();
        var fov = hop.curves().stream().filter(c -> c.channel() == CameraChannel.FOV).findFirst().orElseThrow();
        assertEquals(2.0, ((sh.harold.library.camera.rig.cue.CueCurve.Pulse) fov.curveFor(false)).amount(), 1.0e-9);
        assertFalse(frame(driver, t0 + 6).output().jump(), "nothing fires before T7");
    }

    /** Coordinator clarification 2: a jump pressed at T4 is buffered as a plain jump at T7, with no hop. */
    @Test
    void climbJumpAtT4IsAPlainJumpAtT7() {
        Driver driver = climbing(ALL);
        long t0 = driver.firstStart(MoveKeys.MANTLE).orElseThrow().tick();
        while (driver.tick < t0 + 4) {
            driver.tick(Driver.keys("F"));
        }
        driver.tick(Driver.keys("FJ"));
        driver.run(Driver.keys("F"), 4);
        MoveOutput t7 = frame(driver, t0 + 7).output();
        assertTrue(t7.jump());
        assertEquals(0.2159, outputSpeed(t7), 1.0e-9);
        assertTrue(started(t7, MoveCameras.MANTLE_HOP).isEmpty());
    }

    /** MA8 + MA9: forward and sprint held at T7 exits at sprint speed; sprint pressed from T4 counts too. */
    @Test
    void climbExitsAtSprintWithSprintHeld() {
        Driver driver = climbing(ALL);
        long t0 = driver.firstStart(MoveKeys.MANTLE).orElseThrow().tick();
        while (driver.tick < t0 + 4) {
            driver.tick(Driver.keys("F"));
        }
        driver.tick(Driver.keys("FS"));
        driver.run(Driver.keys("F"), 4);
        assertEquals(0.2806, outputSpeed(frame(driver, t0 + 7).output()), 1.0e-9, "sprint pressed at T4, released");
        assertEquals(0.2806, horizontal(frame(driver, t0 + 7)), 1.0e-9, "T7 moves at exactly the sprint exit");

        Driver walk = climbing(ALL);
        walk.run(Driver.keys("F"), 10);
        long w0 = walk.firstStart(MoveKeys.MANTLE).orElseThrow().tick();
        assertEquals(0.2159, outputSpeed(frame(walk, w0 + 7).output()), 1.0e-9, "walking speed without sprint");
        assertEquals(0.2159, horizontal(frame(walk, w0 + 7)), 1.0e-9, "T7 moves at exactly the walk exit");
    }

    /** MA9: strafe from T5 moves the body sideways 0.05 per tick. */
    @Test
    void climbAcceptsStrafeFromT5() {
        Driver driver = climbing(ALL);
        long t0 = driver.firstStart(MoveKeys.MANTLE).orElseThrow().tick();
        double x0 = frame(driver, t0).before().position().x();
        driver.run(Driver.keys("FR"), 8);
        double lateral = Math.abs(frame(driver, t0 + 6).after().position().x() - x0);
        assertEquals(0.10, lateral, 1.0e-6, "T5 and T6 strafe 0.05 each");
        assertEquals(0.0, Math.abs(frame(driver, t0 + 4).after().position().x() - x0), 1.0e-9, "none before T5");
    }

    /** MA4: input decides the approach, not look. Strafing past a wall never mantles; looking away while pushing does. */
    @Test
    void approachUsesMovementInput() {
        Driver past = Driver.standing(ALL, step(2), new Vec3(0.5, 0.0, 1.35));
        past.tick(Driver.keys("RJ"));
        past.run(Driver.keys("R"), 10);
        assertTrue(past.firstStart(MoveKeys.MANTLE).isEmpty(), "strafing along the wall");

        BodyState sideways = BodyState.standing(new Vec3(0.5, 0.0, 1.35), 90.0f);
        String toward = Probe.inputDirection(sideways, Driver.keys("R")).z() > 0 ? "R" : "L";
        Driver away = new Driver(ALL, step(2), sideways);
        away.run(InputFrame.EMPTY, 3);
        away.trace.clear();
        away.tick(Driver.keys(toward + "J"));
        away.run(Driver.keys(toward), 12);
        assertTrue(away.firstStart(MoveKeys.MANTLE).isPresent(), "looking along the wall, keys into it");
        assertEquals(2.0, away.body().position().y(), 0.01);
    }

    /** MA16: no catch once the fall is longer than 6 blocks. */
    @Test
    void noCatchAfterALongFall() {
        TestWorld world = TestWorld.flat().fill(-3, 0, 2, 3, 9, 30);
        for (double fall : new double[]{5.0, 7.0}) {
            BodyState body = new BodyState(new Vec3(0.5, 8.8, 1.35), new Vec3(0, -0.3, 0), false, false, fall, 0.0f,
                    0.0f, false, 0.6, 1.8, false);
            Driver driver = new Driver(ALL, world, body);
            driver.tick(Driver.keys("FJ"));
            assertEquals(fall < 6.0, driver.firstStart(MoveKeys.MANTLE).isPresent(), "fall " + fall);
        }
    }

    /** MA12: a blocked ledge slaps once per press: arm channel non-zero, hit sound at pitch 1, no motion change. */
    @Test
    void refusedMantleSlapsWithoutMoving() {
        TestWorld world = step(2).fill(-3, 3, 2, 3, 3, 30);
        Driver slap = Driver.standing(ALL, world, new Vec3(0.5, 0.0, 1.35));
        slap.tick(Driver.keys("FJ"));
        slap.run(Driver.keys("FJ"), 3);
        slap.run(Driver.keys("F"), 12);
        assertTrue(slap.firstStart(MoveKeys.MANTLE).isEmpty());
        var slaps = slap.trace.stream().filter(f -> started(f.output(), MoveCameras.MANTLE_REFUSAL).isPresent())
                .toList();
        assertEquals(1, slaps.size(), "once per press");
        CameraCue cue = started(slaps.getFirst().output(), MoveCameras.MANTLE_REFUSAL).orElseThrow();
        assertTrue(peak(cue, CameraChannel.ARM_MAIN_Y) > 0.0);
        assertTrue(peak(cue, CameraChannel.ARM_OFF_Y) > 0.0);
        assertTrue(slaps.getFirst().output().sounds().stream().anyMatch(s -> s.source() == MoveSound.Source.BLOCK_HIT
                && s.pitch() == 1.0f));
        assertTrue(slaps.getFirst().output().passive(), "no motion fields on the slap tick");

        // The refusal also blocks the wall slide for the rest of the airtime, so the comparison has no wall slide.
        Driver without = Driver.standing(Moveset.DEFAULT.withJumpPreset(JumpPreset.A).withEnabled(
                Set.of(MoveKeys.JUMP, MoveKeys.ROLL, MoveKeys.SLIDE)), world, new Vec3(0.5, 0.0, 1.35));
        for (Driver.Frame frame : slap.trace) {
            without.tick(frame.input());
        }
        for (int i = 0; i < slap.trace.size(); i++) {
            assertEquals(without.trace.get(i).after().position(), slap.trace.get(i).after().position(), "tick " + i);
        }
    }

    /**
     * Refusal ruling (2026-11-23 follow-up): after a refusal, no wall slide starts until the body stands on the ground
     * again, while a wall kick still can; after landing, the wall slide works again.
     */
    @Test
    void refusalBlocksWallSlideUntilLanding() {
        TestWorld world = step(2).fill(-3, 3, 2, 3, 3, 30);
        Driver slap = Driver.standing(ALL, world, new Vec3(0.5, 0.0, 1.35));
        slap.tick(Driver.keys("FJ"));
        slap.run(Driver.keys("F"), 20);
        assertEquals(1, slap.trace.stream().filter(f -> started(f.output(), MoveCameras.MANTLE_REFUSAL).isPresent())
                .count(), "refused once");
        assertTrue(slap.firstStart(MoveKeys.WALL_SLIDE).isEmpty(), "no wall slide in the refused airtime");
        assertTrue(slap.body().onGround());

        Driver control = Driver.standing(ALL, world, new Vec3(0.5, 0.0, 1.69));
        control.tick(Driver.keys("J"));
        control.run(InputFrame.EMPTY, 6);
        control.run(Driver.keys("F"), 14);
        assertTrue(control.firstStart(MoveKeys.WALL_SLIDE).isPresent(),
                "control: pushing into the wall on the way down slides when nothing was refused");

        // The wall only counts for a kick once 1.5 blocks of it face the body, so press on the way down below y 0.5.
        Driver kick = Driver.standing(ALL, world, new Vec3(0.5, 0.0, 1.35));
        kick.tick(Driver.keys("FJ"));
        while ((kick.body().velocity().y() >= 0.0 || kick.body().position().y() > 0.5) && kick.tick < 40) {
            kick.tick(Driver.keys("F"));
        }
        kick.tick(Driver.keys("FJ"));
        kick.run(Driver.keys("F"), 4);
        long firstRefusal = kick.trace.stream().filter(f -> started(f.output(), MoveCameras.MANTLE_REFUSAL).isPresent())
                .mapToLong(Driver.Frame::tick).min().orElseThrow();
        assertTrue(kick.firstStart(MoveKeys.WALL_SLIDE).isEmpty(), "no wall slide after the refusal");
        assertTrue(kick.firstStart(MoveKeys.WALL_JUMP).isPresent(), "a wall kick is still allowed after a refusal");
        assertTrue(kick.firstStart(MoveKeys.WALL_JUMP).get().tick() > firstRefusal, "the kick came after the refusal");
        assertTrue(kick.trace.stream().filter(f -> f.tick() > firstRefusal && f.tick() < kick.firstStart(MoveKeys.WALL_JUMP).get().tick())
                .noneMatch(f -> f.after().onGround()), "same airtime: no ground between refusal and kick");

        long landed = slap.tick;
        slap.tick(Driver.keys("J"));
        slap.run(InputFrame.EMPTY, 6);
        slap.run(Driver.keys("F"), 14);
        assertTrue(slap.trace.stream().filter(f -> f.tick() > landed).anyMatch(f -> f.output().events().stream()
                .anyMatch(e -> e instanceof MoveEvent.Started st && st.move().equals(MoveKeys.WALL_SLIDE))),
                "after landing, a later airtime slides again");
    }

    /** MA1, MA13, MA14, MA18, MA3: the climb cue's keys. */
    @Test
    void climbCameraLooksDownAtTheHands() {
        MantleTuning.Climb climb = Moveset.DEFAULT.mantle().climb();
        CameraCue cue = MoveCameras.climb(climb, 1.0);
        var pitch = TuningAndCameraTest.curve(cue, CameraChannel.PITCH, false).track();
        assertEquals(4.0, pitch.sample(1.0), 1.0e-9, "+4 at T0");
        assertEquals(8.0, pitch.sample(2.0), 1.0e-9, "+8 at T1");
        assertEquals(15.0, pitch.sample(4.0), 1.0e-9, "+15 peak at T3");
        assertEquals(10.0, pitch.sample(6.0), 1.0e-9, "+10 at T5");
        assertEquals(3.0, pitch.sample(8.0), 1.0e-9, "+3 at T7");
        assertEquals(0.0, pitch.sample(9.0), 1.0e-9, "0 at T8");
        assertEquals(7.5, TuningAndCameraTest.curve(cue, CameraChannel.PITCH, true).track().sample(4.0), 1.0e-9,
                "Reduced: half");
        var eye = TuningAndCameraTest.curve(cue, CameraChannel.EYE_HEIGHT, false).track();
        assertEquals(-0.08, eye.sample(1.0), 1.0e-9, "bounce 0.08 on T0");
        assertEquals(0.0, eye.sample(3.0), 1.0e-9, "recovered two ticks later");
        var roll = TuningAndCameraTest.curve(cue, CameraChannel.ROLL, false).track();
        assertEquals(2.0, roll.sample(2.0), 1.0e-9);
        assertEquals(2.0, roll.sample(4.0), 1.0e-9);
        assertEquals(-1.5, roll.sample(5.0), 1.0e-9);
        assertEquals(-1.5, roll.sample(7.0), 1.0e-9);
        assertEquals(0.0, roll.sample(9.0), 1.0e-9);
        assertTrue(cue.curves().stream().noneMatch(c -> c.channel() == CameraChannel.FOV), "MA18: no FOV");
        var armY = TuningAndCameraTest.curve(cue, CameraChannel.ARM_MAIN_Y, false).track();
        var armRot = TuningAndCameraTest.curve(cue, CameraChannel.ARM_OFF_ROT_X, false).track();
        assertEquals(0.24, armY.sample(2.0), 1.0e-9, "grip by T1");
        assertEquals(-46.0, armRot.sample(4.0), 1.0e-9, "held through T3, both hands");
        assertEquals(0.0, armY.sample(9.0), 1.0e-9, "rest by T8");
    }

    /** MA2: the vault nod is +6 at T2, 0 by T5, halved in Reduced, no roll; only the main arm plants. */
    @Test
    void vaultCameraNods() {
        CameraCue cue = MoveCameras.vault(Moveset.DEFAULT.mantle().vault());
        assertEquals(6.0, TuningAndCameraTest.curve(cue, CameraChannel.PITCH, false).track().sample(3.0), 1.0e-9);
        assertEquals(3.0, TuningAndCameraTest.curve(cue, CameraChannel.PITCH, true).track().sample(3.0), 1.0e-9);
        assertEquals(0.0, TuningAndCameraTest.curve(cue, CameraChannel.PITCH, false).track().sample(6.0), 1.0e-9);
        assertTrue(cue.curves().stream().noneMatch(c -> c.channel() == CameraChannel.ROLL
                || c.channel() == CameraChannel.FOV || c.channel().isArm() && c.channel().armIndex() >= 6));
        assertEquals(0.24, TuningAndCameraTest.curve(cue, CameraChannel.ARM_MAIN_Y, false).track().sample(1.0),
                1.0e-9, "grip at T0");
    }

    /** MA11: sneak held on the vault exit at sprint speed goes straight into slide T0. */
    @Test
    void vaultIntoSlide() {
        Driver driver = runAndTap(ALL, step(1), "FS");
        long t0 = driver.firstStart(MoveKeys.MANTLE).orElseThrow().tick();
        while (driver.tick < t0 + 5) {
            driver.tick(Driver.keys("FS"));
        }
        driver.run(Driver.keys("FSN"), 3);
        var slide = driver.firstStart(MoveKeys.SLIDE).orElseThrow();
        assertEquals(t0 + 5, slide.tick(), "slide T0 on the vault's last tick");
        assertTrue(outputSpeed(slide.output()) > 0.2806, "slide speed from the vault exit");
    }

    // ---- Jump ----

    /** J2: the boost applies only to a jump launched by a fresh press; holding the key through a landing is vanilla. */
    @Test
    void boostOnlyOnAFreshPress() {
        Moveset jump = Moveset.DEFAULT.withEnabled(Set.of(MoveKeys.JUMP)).withJumpPreset(JumpPreset.A);
        Driver held = Driver.standing(jump, TestWorld.flat(), new Vec3(0.5, 0.0, 0.5));
        held.run(Driver.keys("J"), 40);
        List<Long> launches = held.trace.stream().filter(f -> f.output().jump()).map(Driver.Frame::tick).toList();
        assertTrue(launches.size() >= 2, "auto-repeated");
        long first = launches.get(0);
        long second = launches.get(1);
        long boostedFirst = held.trace.stream().filter(f -> f.tick() >= first && f.tick() < second)
                .filter(f -> f.output().gravity().isPresent()).count();
        long boostedSecond = held.trace.stream().filter(f -> f.tick() >= second && f.tick() < second + 11)
                .filter(f -> f.output().gravity().isPresent()).count();
        assertEquals(8, boostedFirst, "fresh press: sustain over air ticks 3-10");
        assertEquals(0, boostedSecond, "held auto-repeat: vanilla");
        assertTrue(held.trace.stream().filter(f -> f.tick() >= second).noneMatch(
                f -> started(f.output(), MoveCameras.JUMP_BOOST).isPresent()), "no boost FOV on the repeat");

        Driver buffered = Driver.standing(jump, TestWorld.flat(), new Vec3(0.5, 0.0, 0.5));
        buffered.run(Driver.keys("J"), 10);
        buffered.run(InputFrame.EMPTY, 1);
        while (buffered.body().position().y() > 0.3) {
            buffered.tick(InputFrame.EMPTY);
        }
        buffered.run(Driver.keys("J"), 14);
        long repress = buffered.trace.stream().filter(f -> f.output().jump()).skip(1).findFirst().orElseThrow().tick();
        long boosted = buffered.trace.stream().filter(f -> f.tick() >= repress && f.tick() < repress + 11)
                .filter(f -> f.output().gravity().isPresent()).count();
        assertEquals(8, boosted, "a press buffered before the landing is fresh");
    }

    /** J4: no dip at or below 1.3 blocks; above, 0.03 + 0.045 per block, at most 0.25, down 2 ticks, back 5. */
    @Test
    void landingDipHasAThreshold() {
        JumpTuning.Camera camera = Moveset.DEFAULT.jump().camera();
        assertEquals(0.0, camera.landingDip(1.25), 1.0e-12, "a tap jump");
        assertEquals(0.0, camera.landingDip(1.3), 1.0e-12);
        assertEquals(0.03 + 0.045 * 0.7, camera.landingDip(2.0), 1.0e-12);
        assertEquals(0.25, camera.landingDip(9.0), 1.0e-12);
        var eye = TuningAndCameraTest.curve(MoveCameras.landing(camera, 2.0), CameraChannel.EYE_HEIGHT, false).track();
        assertEquals(-(0.03 + 0.045 * 0.7), eye.sample(2.0), 1.0e-12, "down over 2 ticks");
        assertEquals(0.0, eye.sample(7.0), 1.0e-12, "back over 5");
        assertTrue(-eye.sample(4.5) < (0.03 + 0.045 * 0.7) / 2.0, "ease-out recover is past halfway at its middle");

        Moveset jump = Moveset.DEFAULT.withEnabled(Set.of(MoveKeys.JUMP)).withJumpPreset(JumpPreset.A);
        Driver tap = Driver.standing(jump, TestWorld.flat(), new Vec3(0.5, 0.0, 0.5));
        tap.tick(Driver.keys("J"));
        tap.run(InputFrame.EMPTY, 20);
        assertFalse(anyStarted(tap, MoveCameras.LANDING), "tap jump: no dip");

        Driver drop = new Driver(jump, TestWorld.flat(), new BodyState(new Vec3(0.5, 2.0, 0.5), Vec3.ZERO, false,
                false, 0.0, 0.0f, 0.0f, false, 0.6, 1.8, false));
        drop.run(InputFrame.EMPTY, 20);
        assertTrue(anyStarted(drop, MoveCameras.LANDING), "a 2-block drop dips");
    }

    /** J5: a 4+ block landing with no roll cuts sprint for 6 ticks; a 3-block one does not. No landing shake. */
    @Test
    void hardLandingStumbles() {
        Moveset moves = Moveset.DEFAULT.withJumpPreset(JumpPreset.A);
        for (double height : new double[]{3.0, 5.0}) {
            Driver driver = new Driver(moves, TestWorld.flat(), new BodyState(new Vec3(0.5, height, 0.5), Vec3.ZERO,
                    false, false, 0.0, 0.0f, 0.0f, true, 0.6, 1.8, false));
            driver.runUntilGrounded(Driver.keys("FS"), 60);
            long landed = driver.trace.getLast().tick() + 1;
            driver.run(Driver.keys("FS"), 10);
            var cut = driver.trace.stream().filter(f -> f.output().cancelSprint()).map(Driver.Frame::tick).toList();
            if (height < 4.0) {
                assertTrue(cut.isEmpty(), "3 blocks: no stumble");
            } else {
                assertEquals(List.of(landed, landed + 1, landed + 2, landed + 3, landed + 4, landed + 5), cut);
                assertFalse(frame(driver, landed + 2).after().sprinting(), "sprint is off");
                assertEquals("stumble", frame(driver, landed + 1).output().status().phase());
                double walk = ReferenceIntegrator.groundSpeed(0.6, false);
                assertEquals(0.21586, walk, 1.0e-5, "the integrator's walking speed on stone");
                for (long t = landed; t <= landed + 5; t++) {
                    assertTrue(horizontal(frame(driver, t)) <= walk + 1.0e-9, "clamped to walking at tick " + t);
                }
                assertEquals(walk, horizontal(frame(driver, landed)), 1.0e-9, "the first stumble tick moves at walk");
                assertTrue(horizontal(frame(driver, landed + 7)) > walk + 0.01, "sprint returns after the stumble");
            }
            assertTrue(driver.trace.stream().allMatch(f -> f.output().camera().stream().noneMatch(c ->
                    c instanceof CameraCommand.Start s && s.cue().curves().stream()
                            .anyMatch(curve -> curve.channel() == CameraChannel.SHAKE))), "no shake");
        }

        Driver rolled = new Driver(moves, TestWorld.flat(), new BodyState(new Vec3(0.5, 5.0, 0.5), new Vec3(0, 0, 0.2),
                false, false, 0.0, 0.0f, 0.0f, true, 0.6, 1.8, false));
        while (rolled.body().position().y() > 0.6) {
            rolled.tick(Driver.keys("FS"));
        }
        rolled.tick(Driver.keys("FSN"));
        rolled.run(Driver.keys("FS"), 12);
        assertTrue(rolled.firstStart(MoveKeys.ROLL).isPresent());
        assertTrue(rolled.trace.stream().noneMatch(f -> f.output().cancelSprint()), "a roll takes the landing");
    }

    // ---- Roll ----

    /** RO1: the flip axis comes from the travel-to-look angle; no travel counts as 0 degrees. */
    @Test
    void rollAxisByAngle() {
        RollTuning.Camera camera = Moveset.DEFAULT.roll().camera();
        Vec3 look = new Vec3(0, 0, 1);
        assertEquals(MoveCameras.RollAxis.FORWARD, MoveCameras.rollAxis(camera, Vec3.ZERO, look));
        assertEquals(MoveCameras.RollAxis.FORWARD, MoveCameras.rollAxis(camera, at(60), look));
        assertEquals(MoveCameras.RollAxis.SIDE, MoveCameras.rollAxis(camera, at(61), look));
        assertEquals(MoveCameras.RollAxis.SIDE, MoveCameras.rollAxis(camera, at(90), look));
        assertEquals(MoveCameras.RollAxis.SIDE, MoveCameras.rollAxis(camera, at(120), look));
        assertEquals(MoveCameras.RollAxis.BACKWARD, MoveCameras.rollAxis(camera, at(121), look));
        assertEquals(MoveCameras.RollAxis.BACKWARD, MoveCameras.rollAxis(camera, at(180), look));

        RollTuning roll = Moveset.DEFAULT.roll();
        CameraCue side = MoveCameras.roll(roll, MoveCameras.RollAxis.SIDE, 1.0);
        assertEquals(25.0, peak(side, CameraChannel.PITCH), 1.0e-9, "the dip, no flip");
        assertTrue(side.curves().stream().noneMatch(c -> c.unwound()), "no turn");
        assertEquals(8.0, peak(side, CameraChannel.ROLL), 1.0e-9, "8 toward travel");
        assertEquals(-8.0, peak(MoveCameras.roll(roll, MoveCameras.RollAxis.SIDE, -1.0), CameraChannel.ROLL),
                1.0e-9, "travelling left leans left");
        CameraCue back = MoveCameras.roll(roll, MoveCameras.RollAxis.BACKWARD, 0.0);
        assertEquals(-360.0, TuningAndCameraTest.curve(back, CameraChannel.PITCH, false).track().sample(10.0), 1.0e-9);
        assertEquals(25.0, TuningAndCameraTest.curve(back, CameraChannel.PITCH, true).track().sample(4.0), 1.0e-9,
                "Reduced: the dip in every case");
    }

    static Vec3 at(double degrees) {
        double r = Math.toRadians(degrees);
        return new Vec3(Math.sin(r), 0, Math.cos(r));
    }

    // ---- Slide ----

    /** S2: each tick v = min(0.5, sqrt(v^2 + 0.048 * descended)) * 0.96, and 1-block steps do not end it. */
    @Test
    void slopeGainFromHeightLost() {
        TestWorld world = TestWorld.flat().fill(-3, 0, -40, 3, 3, 4).fill(-3, 0, 5, 3, 2, 7).fill(-3, 0, 8, 3, 1, 10)
                .fill(-3, 0, 11, 3, 0, 13);
        Moveset slide = Moveset.DEFAULT.withEnabled(Set.of(MoveKeys.SLIDE, MoveKeys.JUMP));
        Driver driver = Driver.standing(slide, world, new Vec3(0.5, 4.0, -30.0));
        driver.run(Driver.keys("FS"), 40);
        while (driver.body().position().z() < 3.0) {
            driver.tick(Driver.keys("FS"));
        }
        driver.trace.clear();
        driver.run(Driver.keys("FSN"), 80);
        var start = driver.firstStart(MoveKeys.SLIDE).orElseThrow();
        var end = driver.firstEnd(MoveKeys.SLIDE).orElseThrow();
        boolean airborne = false;
        double descendedTotal = 0;
        for (long t = start.tick() + 1; t < end.tick(); t++) {
            Driver.Frame previous = frame(driver, t - 1);
            Driver.Frame current = frame(driver, t);
            double before = horizontal(previous);
            double descended = Math.max(0.0, previous.before().position().y() - previous.after().position().y());
            descendedTotal += descended;
            double expected = Math.min(0.5, Math.sqrt(before * before + 0.048 * descended)) * 0.96;
            assertEquals(expected, horizontal(current), 1.0e-9, "slide tick " + (t - start.tick()));
            airborne |= !current.before().onGround();
        }
        assertTrue(airborne, "went over a drop");
        assertTrue(descendedTotal >= 2.99, "came down the steps: " + descendedTotal);
        assertTrue(driver.body().position().y() < 1.01, "reached the bottom");
    }

    // ---- Wall slide ----

    /** W1: the equivalent fall height table from the integrator. */
    @Test
    void equivalentFallHeights() {
        assertEquals(0.08, ReferenceIntegrator.equivalentFallHeight(0.12), 0.005);
        assertEquals(0.23, ReferenceIntegrator.equivalentFallHeight(0.2), 0.005);
        assertEquals(0.46, ReferenceIntegrator.equivalentFallHeight(0.3), 0.005);
        assertEquals(0.0, ReferenceIntegrator.equivalentFallHeight(0.0), 1.0e-12);
    }

    static final Moveset WALLS = Moveset.DEFAULT.withEnabled(Set.of(MoveKeys.WALL_JUMP, MoveKeys.JUMP));

    /** W1: entering fast, fall speed approaches the cap by at most 0.15 a tick, and fall distance follows it. */
    @Test
    void wallSlideBrakesAndCapsFallDistance() {
        TestWorld world = TestWorld.flat().fill(-1, 0, -3, -1, 60, 3);
        BodyState body = new BodyState(new Vec3(0.31, 40.0, 0.5), new Vec3(0, -0.8, 0), false, false, 12.0, 90.0f,
                0.0f, false, 0.6, 1.8, false);
        Driver driver = new Driver(WALLS, world, body);
        driver.run(Driver.keys("F"), 8);
        var slideTicks = driver.trace.stream().filter(f -> f.output().events().stream()
                .anyMatch(e -> e instanceof MoveEvent.FallDistanceCap)).toList();
        assertTrue(slideTicks.size() >= 4);
        double previousSpeed = -slideTicks.getFirst().before().velocity().y();
        for (Driver.Frame frame : slideTicks.subList(0, 4)) {
            double before = -frame.before().velocity().y();
            double applied = -(frame.after().position().y() - frame.before().position().y());
            assertEquals(Math.max(before - 0.15, 0.12), applied, 0.02, "braked by at most 0.15");
            double cap = frame.output().events().stream().filter(e -> e instanceof MoveEvent.FallDistanceCap)
                    .map(e -> ((MoveEvent.FallDistanceCap) e).blocks()).findFirst().orElseThrow();
            assertEquals(ReferenceIntegrator.equivalentFallHeight(applied), cap, 1.0e-9);
            assertTrue(frame.after().fallDistance() <= cap + 1.0e-9, "fall distance follows the equivalent height");
            previousSpeed = applied;
        }
        assertTrue(previousSpeed < 0.8);
    }

    /** W1: a brush of a tick or two against a short wall segment does not erase a long fall. */
    @Test
    void briefBrushKeepsALongFall() {
        TestWorld world = TestWorld.flat().fill(-1, 14, -3, -1, 16, 3);
        BodyState body = new BodyState(new Vec3(0.31, 30.0, 0.5), Vec3.ZERO, false, false, 0.0, 90.0f, 0.0f, false,
                0.6, 1.8, false);
        Driver driver = new Driver(WALLS, world, body);
        while (driver.body().position().y() > 19.0) {
            driver.tick(InputFrame.EMPTY);
        }
        driver.runUntilGrounded(Driver.keys("F"), 200);
        var brush = driver.trace.stream().filter(f -> f.output().events().stream()
                .anyMatch(e -> e instanceof MoveEvent.FallDistanceCap)).toList();
        assertFalse(brush.isEmpty(), "brushed the wall");
        assertTrue(brush.size() <= 2, "a brief brush: " + brush.size());
        double afterBrush = brush.getLast().after().fallDistance();
        assertTrue(afterBrush > 5.0, "the fall is kept, not reset: " + afterBrush);
        double landing = driver.trace.get(driver.trace.size() - 2).after().fallDistance();
        assertTrue(landing > 20.0, "lands with most of the 30-block fall: " + landing);
    }

    /** W2: while sliding, a 3 degree roll leans away from the wall. */
    @Test
    void wallSlideLeansAway() {
        TestWorld world = TestWorld.flat().fill(-1, 0, -3, -1, 60, 3);
        BodyState body = new BodyState(new Vec3(0.31, 40.0, 0.5), Vec3.ZERO, false, false, 0.0, 90.0f, 0.0f, false,
                0.6, 1.8, false);
        Driver driver = new Driver(WALLS, world, body);
        driver.run(Driver.keys("F"), 10);
        var slide = driver.trace.stream().filter(f -> f.output().events().stream()
                .anyMatch(e -> e instanceof MoveEvent.FallDistanceCap)).findFirst().orElseThrow();
        double side = new Vec3(1, 0, 0).dot(slide.before().right());
        assertEquals(3.0 * Math.signum(side), slide.output().cameraSignals().roll(), 1.0e-9);
    }

    /** The 26.3 sanity check behind the slide test: S1 values hold with the stock tuning file. */
    @Test
    void slideVariantStillParses() {
        assertEquals(SlideVariant.S1, Moveset.DEFAULT.slide().variant());
        assertEquals(0.19, Moveset.DEFAULT.slide().endSpeed(), 1.0e-12);
    }
}
