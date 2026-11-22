package sh.harold.library.movement.core;

import org.junit.jupiter.api.Test;
import sh.harold.library.movement.InputFrame;
import sh.harold.library.movement.JumpPreset;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.core.tuning.Moveset;
import sh.harold.library.spatial.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 02's jump table ("Numbers from design/sims/jump_curves.py") under the reference integrator. Apex within 0.01, apex
 * tick and air ticks exact. Sprint-jump distance is checked within {@link #DISTANCE_TOLERANCE}; the sim models the
 * sprint takeoff with a simplified friction step, so the integrator's distances are reported next to 02's.
 */
class JumpTableTest {

    static final double APEX_TOLERANCE = 0.01;
    static final double DISTANCE_TOLERANCE = 0.15;

    record Row(String name, JumpPreset preset, int hold, double apex, int apexTick, int airTicks, double distance) {
    }

    record Result(double apex, int apexTick, int airTicks, double distance) {
    }

    static final List<Row> TABLE = List.of(
            new Row("vanilla tap", JumpPreset.A, 1, 1.25, 6, 12, 3.63),
            new Row("A held 2", JumpPreset.A, 2, 1.25, 6, 12, 3.63),
            new Row("A held 4", JumpPreset.A, 4, 1.52, 7, 14, 4.20),
            new Row("A held 6", JumpPreset.A, 6, 1.72, 8, 15, 4.48),
            new Row("A held 8", JumpPreset.A, 8, 1.83, 9, 17, 5.05),
            new Row("A held 10", JumpPreset.A, 10, 1.87, 11, 18, 5.34),
            new Row("A held 20", JumpPreset.A, 20, 1.87, 11, 18, 5.34),
            new Row("B 4-tick squat", JumpPreset.B, 4, 1.65, 6, 13, 3.91),
            new Row("B 8-tick squat", JumpPreset.B, 8, 2.17, 7, 15, 4.48),
            new Row("C released on tick 1", JumpPreset.C, 1, 0.82, 3, 8, 2.49),
            new Row("C held 20", JumpPreset.C, 20, 1.25, 6, 12, 3.63));

    static Result measure(JumpPreset preset, int hold, boolean sprint) {
        Moveset moveset = Moveset.DEFAULT.withEnabled(Set.of(MoveKeys.JUMP)).withJumpPreset(preset);
        Driver driver = Driver.standing(moveset, TestWorld.flat(), new Vec3(0.5, 0.0, 0.5));
        InputFrame move = sprint ? Driver.keys("FS") : InputFrame.EMPTY;
        if (sprint) {
            driver.run(move, 40);
        }
        driver.trace.clear();
        int held = 0;
        Vec3 takeoff = null;
        double groundY = driver.body().position().y();
        double apex = Double.NEGATIVE_INFINITY;
        int apexTick = 0;
        int airTick = 0;
        for (int i = 0; i < 80; i++) {
            boolean jumpKey = held < hold;
            var before = driver.body();
            var output = driver.tick(move.withJump(jumpKey));
            if (jumpKey) {
                held++;
            }
            if (takeoff == null && output.jump()) {
                takeoff = before.position();
            }
            if (takeoff == null) {
                continue;
            }
            airTick++;
            double y = driver.body().position().y() - groundY;
            if (y > apex + 1.0e-12) {
                apex = y;
                apexTick = airTick;
            }
            if (driver.body().onGround()) {
                Vec3 delta = driver.body().position().subtract(takeoff);
                return new Result(apex, apexTick, airTick, Math.hypot(delta.x(), delta.z()));
            }
        }
        throw new AssertionError("never landed");
    }

    @Test
    void reproducesTheJumpTableHeights() {
        List<String> report = new ArrayList<>();
        for (Row row : TABLE) {
            Result result = measure(row.preset(), row.hold(), false);
            report.add(String.format("%-22s apex %.3f (02 %.2f) apexTick %d (02 %d) air %d (02 %d)", row.name(),
                    result.apex(), row.apex(), result.apexTick(), row.apexTick(), result.airTicks(), row.airTicks()));
        }
        System.out.println(String.join("\n", report));
        for (Row row : TABLE) {
            Result result = measure(row.preset(), row.hold(), false);
            assertEquals(row.apex(), result.apex(), APEX_TOLERANCE, row.name() + " apex");
            assertEquals(row.apexTick(), result.apexTick(), row.name() + " apex tick");
            assertEquals(row.airTicks(), result.airTicks(), row.name() + " air ticks");
        }
    }

    @Test
    void sprintJumpDistancesAgreeWithinTolerance() {
        List<String> report = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (Row row : TABLE) {
            if (row.preset() == JumpPreset.B) {
                continue;
            }
            Result result = measure(row.preset(), row.hold(), true);
            String line = String.format("%-22s sprint distance %.3f (02 %.2f) air %d", row.name(), result.distance(),
                    row.distance(), result.airTicks());
            report.add(line);
            if (Math.abs(result.distance() - row.distance()) > DISTANCE_TOLERANCE) {
                failures.add(line);
            }
        }
        System.out.println(String.join("\n", report));
        assertTrue(failures.isEmpty(), "sprint distances off: \n" + String.join("\n", failures));
    }

    /**
     * Mismatch with 02: preset B's sprint-jump distances. 02 says the squat moves at x0.6, but its numbers come from a
     * sim that takes off at full sprint speed after the squat. Under the integrator the squat slows the run-up, so B
     * jumps land short of 02's 3.91 and 4.48. This test pins the integrator's values so a change is noticed; the
     * design owner decides whether the squat keeps sprint speed or the table changes.
     */
    @Test
    void reportsPresetBSprintDistanceMismatch() {
        Result four = measure(JumpPreset.B, 4, true);
        Result eight = measure(JumpPreset.B, 8, true);
        System.out.printf("MISMATCH 02 B 4-tick squat sprint distance: 02 3.91, integrator %.3f%n", four.distance());
        System.out.printf("MISMATCH 02 B 8-tick squat sprint distance: 02 4.48, integrator %.3f%n", eight.distance());
        assertEquals(3.63, four.distance(), 0.02);
        assertEquals(4.15, eight.distance(), 0.02);
        assertTrue(Math.abs(four.distance() - 3.91) > DISTANCE_TOLERANCE);
        assertTrue(Math.abs(eight.distance() - 4.48) > DISTANCE_TOLERANCE);
    }
}
