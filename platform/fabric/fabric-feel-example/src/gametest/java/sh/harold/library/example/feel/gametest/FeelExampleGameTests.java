package sh.harold.library.example.feel.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.kyori.adventure.key.Key;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import sh.harold.library.camera.rig.IntensityProfile;
import sh.harold.library.devtools.fabric.client.CaptureClock;
import sh.harold.library.example.feel.FeelCourse;
import sh.harold.library.example.feel.FeelExample;
import sh.harold.library.example.feel.client.FeelExampleClient;
import sh.harold.library.movement.JumpPreset;
import sh.harold.library.movement.MoveEvent;
import sh.harold.library.movement.SlideVariant;
import sh.harold.library.movement.fabric.client.ClientMovement;
import sh.harold.library.movement.fabric.client.InputScript;
import sh.harold.library.net.fabric.client.ClientServerClock;
import sh.harold.library.telemetry.TelemetryHandle;
import sh.harold.library.telemetry.core.MemoryTelemetrySink;
import sh.harold.library.telemetry.fabric.FabricServerTelemetry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Drives the five stock moves on the feel course with simulated input ({@link ClientMovement#simulateInput}), records
 * one telemetry CSV per scenario ({@link FeelRecorder}) and checks each move's tick timeline against
 * {@code randomrpg/design/02-movement-and-camera.md}. {@code T0} is the tick the move's input is accepted: the row
 * whose {@code test.started} names the move.
 *
 * <p>Every assertion is collected ({@link FeelChecks}) and the test fails at the end if any failed, so one run lists
 * every mismatch. Output goes to {@code feel-evidence/} in the run directory: the merged CSVs, the raw tick and
 * frame CSVs, the server CSVs of the roll runs, {@code results.md} and the screenshots.
 *
 * <p>The capture clock runs at 20 fps for the whole test, so each lockstep tick draws exactly one frame at partial 0
 * and frame telemetry repeats run to run.
 */
public final class FeelExampleGameTests implements FabricClientGameTest {

    private static final int SHOT_WIDTH = 854;
    private static final int SHOT_HEIGHT = 480;
    private static final double STAND_EYE = 1.62;
    private static final List<String> ARM_COLUMNS = List.of("camera.arm_main_x", "camera.arm_main_y",
            "camera.arm_main_z", "camera.arm_main_rot_x", "camera.arm_main_rot_y", "camera.arm_main_rot_z",
            "camera.arm_off_x", "camera.arm_off_y", "camera.arm_off_z", "camera.arm_off_rot_x", "camera.arm_off_rot_y",
            "camera.arm_off_rot_z");
    private static final List<String> LAYER_COLUMNS = java.util.stream.Stream.concat(Stream.of("camera.pitch_add",
            "camera.roll", "camera.fov_add", "camera.eye_height", "camera.shake"), ARM_COLUMNS.stream()).toList();

    private Path out;
    private Path shots;
    private FeelChecks checks;
    private InputScript script;
    private BlockPos origin;
    private FeelCourse.Course course;
    private final ServerHealth health = new ServerHealth();

    @Override
    public void runTest(ClientGameTestContext context) {
        out = FabricLoader.getInstance().getGameDir().resolve("feel-evidence");
        shots = out.resolve("screenshots");
        checks = new FeelChecks();
        FeelRecorder.registerColumns(context);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            setUpWorld(context, world);
            handshake(context);
            CaptureClock.Handle clock = context.computeOnClient(client -> CaptureClock.start(20));
            try {
                scenario("idle", "idle at start", () -> idle(context, world, "idle_start", true));
                scenario("jump", "boost held", () -> boostHeld(context, world));
                scenario("jump", "boost tap", () -> boostTap(context, world));
                scenario("jump", "fresh press boost", () -> freshPress(context, world));
                scenario("jump", "landing dip threshold", () -> landingDip(context, world));
                scenario("jump", "stumble", () -> stumble(context, world));
                scenario("mantle", "walking tap at a step", () -> walkingTap(context, world));
                scenario("mantle", "vault", () -> vault(context, world, false));
                scenario("mantle", "vault hop", () -> vault(context, world, true));
                scenario("mantle", "ledge_2 standing tap", () -> ledgeTwoStanding(context, world));
                scenario("mantle", "climb", () -> climb(context, world));
                scenario("mantle", "climb hop", () -> climbHop(context, world));
                scenario("mantle", "climb with sprint held", () -> climbSprint(context, world));
                scenario("mantle", "no headroom", () -> noHeadroom(context, world));
                scenario("roll", "roll", () -> roll(context, world, "roll_full", IntensityProfile.FULL));
                scenario("roll", "sneak held", () -> rollHeldSneak(context, world));
                scenario("roll", "reduced", () -> roll(context, world, "roll_reduced", IntensityProfile.REDUCED));
                scenario("roll", "sideways", () -> rollAxis(context, world, "roll_side", "L", true));
                scenario("roll", "backward", () -> rollAxis(context, world, "roll_back", "B", false));
                scenario("slide", "slide", () -> slide(context, world));
                scenario("wall_jump", "single wall", () -> singleWall(context, world));
                scenario("wall_jump", "single wall from a drop", () -> singleWallDrop(context, world));
                scenario("wall_jump", "shaft_3 chain", () -> shaftChain(context, world));
                scenario("wall_slide", "fall damage", () -> wallSlideFall(context, world));
                scenario("idle", "idle at end", () -> idle(context, world, "idle_end", false));
            } finally {
                context.runOnClient(client -> {
                    clock.close();
                    FeelExampleClient.setCameraIntensity(IntensityProfile.FULL);
                });
            }
        }
        checks.finish(out);
    }

    private void scenario(String move, String name, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | AssertionError error) {
            checks.broken(move, name, error);
        }
    }

    // ---- world ----

    private void setUpWorld(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("gamemode survival @a");
        world.getServer().runCommand("difficulty peaceful");
        world.getServer().runCommand("time set noon");
        world.getServer().runCommand("weather clear");
        int feetY = world.getServer().computeOnServer(server ->
                server.getPlayerList().getPlayers().getFirst().blockPosition().getY());
        origin = new BlockPos(0, feetY, 0);
        BlockPos at = origin;
        course = world.getServer().computeOnServer(server -> FeelCourse.build(server.overworld(), at));
        world.getServer().runOnServer(server -> {
            FeelExample.setJumpPreset(JumpPreset.A);
            FeelExample.setSlideVariant(SlideVariant.S1);
        });
        health.register(world);
        context.waitTicks(5);
    }

    /** The net handshake: the server's moveset reached the client and the tick clock is sampling. */
    private void handshake(ClientGameTestContext context) {
        context.waitTicks(40);
        boolean connected = context.computeOnClient(client -> client.getConnection() != null);
        int moves = context.computeOnClient(client -> ClientMovement.current()
                .map(movement -> movement.spec().moveset().enabled().size()).orElse(-1));
        int samples = context.computeOnClient(client -> ClientServerClock.sampleCount());
        checks.check("net", "handshake: still connected after join", "connected", connected ? "connected" : "gone",
                connected, "-");
        checks.equal("net", "handshake: server moveset reached the client (enabled moves)", 5, moves, "-");
        checks.check("net", "handshake: server tick clock sampled", "> 0 samples", Integer.toString(samples),
                samples > 0, "-");
    }

    private void tp(ClientGameTestContext context, TestSingleplayerContext world, double x, double y, double z,
                    float yaw) {
        script(context, "");
        world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.4f %.4f %.4f %.2f 0", x, y, z, yaw));
        context.waitFor(client -> client.player != null && client.player.onGround()
                && client.player.position().distanceToSqr(x, y, z) < 1.0e-4, 100);
        world.getServer().runOnServer(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.setHealth(player.getMaxHealth());
                player.getFoodData().setFoodLevel(20);
                player.resetFallDistance();
            }
        });
        world.getConnection().waitForChunksRender();
        context.waitTicks(12);
    }

    private void tp(ClientGameTestContext context, TestSingleplayerContext world, FeelCourse.Station station) {
        tp(context, world, station.start().getX() + 0.5, station.start().getY(), station.start().getZ() + 0.5,
                station.yaw());
    }

    private void script(ClientGameTestContext context, String frames) {
        context.runOnClient(client -> script = ClientMovement.current().orElseThrow().simulateInput(frames));
    }

    private Live live(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            var player = client.player;
            ClientMovement movement = ClientMovement.current().orElseThrow();
            var status = movement.status();
            List<String> started = movement.lastOutput().events().stream()
                    .filter(MoveEvent.Started.class::isInstance)
                    .map(event -> ((MoveEvent.Started) event).move().value())
                    .toList();
            return new Live(player.getX(), player.getY(), player.getZ(), player.getDeltaMovement().y,
                    player.onGround(),
                    status.move().map(Key::value).orElse(""), status.moveTick(), started,
                    script == null ? 0 : script.remaining());
        });
    }

    private record Live(double x, double y, double z, double vy, boolean onGround, String move, long moveTick,
                        List<String> started, int remaining) {
    }

    @FunctionalInterface
    private interface TickBody {
        /** Called after each recorded tick; returns true to stop recording. */
        boolean tick(int index, Live live);
    }

    private FeelRecorder.Table record(ClientGameTestContext context, String name, String frames, int maxTicks,
                                      TickBody body) {
        FeelRecorder recorder = FeelRecorder.start(context);
        script(context, frames);
        for (int i = 0; i < maxTicks; i++) {
            context.waitTick();
            if (body.tick(i, live(context))) {
                break;
            }
        }
        script(context, "");
        return recorder.stop(context, out, name);
    }

    /** A fixed-size shot of the end of the current tick (delta 1 draws rig time {@code n}, as a frame row does). */
    private void shot(ClientGameTestContext context, String name) {
        Path path = context.takeScreenshot(TestScreenshotOptions.of(name).withSize(SHOT_WIDTH, SHOT_HEIGHT)
                .withDeltaTicks(1.0f).withDestinationDir(shots).disableCounterPrefix());
        System.out.println("SCREENSHOT " + path.toAbsolutePath());
    }

    private double floorY() {
        return origin.getY();
    }

    /** Horizontal displacement over one recorded tick. */
    private static double step(FeelRecorder.Table t, int row) {
        if (row < 1 || row >= t.size()) {
            return Double.NaN;
        }
        return Math.hypot(t.d(row, "pos_x") - t.d(row - 1, "pos_x"), t.d(row, "pos_z") - t.d(row - 1, "pos_z"));
    }

    private static double maxAbs(FeelRecorder.Table t, int from, int to, String column) {
        double max = 0.0;
        for (int row = Math.max(0, from); row <= to && row < t.size(); row++) {
            double v = t.d(row, column);
            if (!Double.isNaN(v)) {
                max = Math.max(max, Math.abs(v));
            }
        }
        return max;
    }

    // ---- idle ----

    private void idle(ClientGameTestContext context, TestSingleplayerContext world, String name, boolean first) {
        tp(context, world, origin.getX() + 10.5, origin.getY(), origin.getZ() + 18.5, 0.0f);
        FeelRecorder.Table t = record(context, name, "-*20", 20, (i, live) -> false);
        double baseFov = context.computeOnClient(client -> (double) client.options.fov().get());
        String label = first ? " (before any move)" : " (after every move)";
        double worst = 0.0;
        int worstRow = -1;
        String worstColumn = "-";
        int nanRows = 0;
        for (int row = 0; row < t.size(); row++) {
            if (Double.isNaN(t.d(row, "camera.fov_add"))) {
                nanRows++;
            }
            for (String column : LAYER_COLUMNS) {
                double v = t.d(row, column);
                if (!Double.isNaN(v) && Math.abs(v) > worst) {
                    worst = Math.abs(v);
                    worstRow = row;
                    worstColumn = column;
                }
            }
            double look = t.d(row, "camera.look_scale");
            if (!Double.isNaN(look) && Math.abs(look - 1.0) > worst) {
                worst = Math.abs(look - 1.0);
                worstRow = row;
                worstColumn = "camera.look_scale";
            }
        }
        checks.check("idle", "camera layers at rest every tick" + label,
                "offsets exactly 0 and look_scale 1 (NaN = rig not open)",
                (worst == 0.0 ? "all at rest" : worstColumn + " off by " + worst) + "; NaN rows " + nanRows + "/"
                        + t.size(), worst == 0.0, worst == 0.0 ? t.name() + ".csv all rows" : t.ref(worstRow));
        double maxRoll = maxAbs(t, 0, t.size(), "roll");
        // A row with no frame (the stop tick, whose frame is never drawn) has NaN drawn columns: skipped and counted.
        double maxEye = 0.0;
        double maxFov = 0.0;
        int drawn = 0;
        for (int row = 0; row < t.size(); row++) {
            double eye = t.d(row, "camera_eye_height");
            double fov = t.d(row, "fov");
            if (Double.isNaN(eye) || Double.isNaN(fov)) {
                continue;
            }
            drawn++;
            maxEye = Math.max(maxEye, Math.abs(eye - STAND_EYE));
            maxFov = Math.max(maxFov, Math.abs(fov - baseFov));
        }
        String rows = t.name() + ".csv " + drawn + "/" + t.size() + " rows with a frame";
        checks.check("idle", "every recorded tick but the stop tick has a drawn frame" + label,
                ">= " + (t.size() - 1) + " rows", drawn + " rows", drawn >= t.size() - 1, rows);
        checks.near("idle", "drawn camera roll stays 0" + label, 0.0, maxRoll, 1.0e-6, rows);
        checks.near("idle", "drawn camera eye height stays 1.62 (max |eye - 1.62|)" + label, 0.0, maxEye, 1.0e-4,
                rows);
        checks.near("idle", "drawn FOV equals the options FOV " + FeelChecks.fmt(baseFov) + " (max |fov - base|)"
                + label, 0.0, maxFov, 1.0e-4, rows);
        if (first) {
            shot(context, "idle");
        }
    }

    // ---- variable jump ----

    private void boostHeld(ClientGameTestContext context, TestSingleplayerContext world) {
        tp(context, world, origin.getX() + 10.5, origin.getY(), origin.getZ() + 18.5, 0.0f);
        FeelRecorder.Table t = record(context, "jump_boost_held", "J*12 -*30", 42, (i, live) -> false);
        jumpCurve(t, "boost held (preset A, J held 12 ticks)", 1.87, 11, 18);
        int peak = -1;
        double fov = 0.0;
        for (int row = 0; row < t.size(); row++) {
            double v = t.d(row, "camera.fov_add");
            if (!Double.isNaN(v) && v > fov) {
                fov = v;
                peak = row;
            }
        }
        checks.near("jump", "boost held: FOV kick peak (02 camera: +2)", 2.0, fov, 0.05,
                peak < 0 ? t.name() + ".csv (no FOV change)" : t.ref(peak));
        pressLength(t, "J held 12 ticks", 12);
    }

    private void boostTap(ClientGameTestContext context, TestSingleplayerContext world) {
        tp(context, world, origin.getX() + 10.5, origin.getY(), origin.getZ() + 18.5, 0.0f);
        FeelRecorder.Table t = record(context, "jump_boost_tap", "J -*30", 31, (i, live) -> false);
        jumpCurve(t, "boost tap (J for 1 tick)", 1.25, 6, 12);
        double worst = 0.0;
        int worstRow = -1;
        String worstColumn = "-";
        for (int row = 0; row < t.size(); row++) {
            for (String column : LAYER_COLUMNS) {
                double v = t.d(row, column);
                if (!Double.isNaN(v) && Math.abs(v) > worst) {
                    worst = Math.abs(v);
                    worstRow = row;
                    worstColumn = column;
                }
            }
        }
        pressLength(t, "J tapped 1 tick", 1);
        checks.check("jump", "boost tap: no camera change (arm channels included)", "every camera layer 0",
                worst == 0.0 ? "all 0" : worstColumn + " = " + FeelChecks.fmt(worst), worst == 0.0,
                worst == 0.0 ? t.name() + ".csv all rows" : t.ref(worstRow));
    }

    /** Air tick 1 is the takeoff tick (the first row above the floor); the landing tick counts as an air tick. */
    private void jumpCurve(FeelRecorder.Table t, String label, double apex, int apexTick, int airTicks) {
        int takeoff = -1;
        for (int row = 0; row < t.size(); row++) {
            if (t.d(row, "pos_y") - floorY() > 1.0e-3) {
                takeoff = row;
                break;
            }
        }
        if (takeoff < 0) {
            checks.check("jump", label + ": takeoff", "a jump", "never left the floor", false, t.name() + ".csv");
            return;
        }
        int land = -1;
        double top = -1.0;
        int topRow = takeoff;
        for (int row = takeoff; row < t.size(); row++) {
            double h = t.d(row, "pos_y") - floorY();
            if (h > top) {
                top = h;
                topRow = row;
            }
            if (row > takeoff && t.b(row, "on_ground")) {
                land = row;
                break;
            }
        }
        checks.near("jump", label + ": apex height", apex, top, 0.01, t.ref(topRow));
        checks.equal("jump", label + ": apex air tick", apexTick, topRow - takeoff + 1, t.ref(topRow));
        checks.equal("jump", label + ": air ticks (takeoff to landing, inclusive)", airTicks,
                land < 0 ? -1 : land - takeoff + 1, land < 0 ? t.name() + ".csv (no landing)" : t.ref(land));
    }

    // ---- mantle ----

    /** What a standing tap jump at the 2-block wall gives; 02's trigger rule predicts a climb (top 1.58 up). */
    private void ledgeTwoStanding(ClientGameTestContext context, TestSingleplayerContext world) {
        tp(context, world, course.station("ledge_2"));
        FeelRecorder.Table t = record(context, "mantle_ledge2_standing", "F*15 FJ F*20", 36, (i, live) -> false);
        int start = t.started("mantle", 0);
        String kind = start < 0 ? "none" : t.s(start, "move.phase");
        checks.check("mantle", "ledge_2 from standing, tap jump: mantles (02 trigger rule gives a climb)",
                "climb", kind, kind.startsWith("climb"), start < 0 ? t.name() + ".csv" : t.ref(start));
    }

    private void climb(ClientGameTestContext context, TestSingleplayerContext world) {
        tp(context, world, course.station("ledge_3"));
        boolean[] shot = {false};
        FeelRecorder.Table t = record(context, "mantle_climb", "F*15 FJ*14 F*20", 50, (i, live) -> {
            if (!shot[0] && live.move().equals("mantle") && live.moveTick() == 3) {
                shot[0] = true;
                shot(context, "mantle_climb_T3");
            }
            return false;
        });
        int t0 = t.started("mantle", 0);
        String kind = t0 < 0 ? "none" : t.s(t0, "move.phase");
        checks.check("mantle", "ledge_3 after a held jump: climb", "climb", kind, kind.startsWith("climb"),
                t0 < 0 ? t.name() + ".csv" : t.ref(t0));
        if (t0 < 0) {
            return;
        }
        int t6 = t0 + 6;
        int t7 = t0 + 7;
        int t9 = t0 + 9;
        checks.check("mantle", "climb: still pulled (no input) at T6", "phase climb_pull", t.s(t6, "move.phase"),
                "climb_pull".equals(t.s(t6, "move.phase")), t.ref(t6));
        double vz = t.d(t7, "vel_z");
        checks.check("mantle", "climb: movement accepted at T7", "phase climb_stand, forward speed > 0",
                "phase " + t.s(t7, "move.phase") + ", vel_z " + FeelChecks.fmt(vz),
                "climb_stand".equals(t.s(t7, "move.phase")) && vz > 1.0e-3, t.ref(t7));
        boolean ended = t.s(t9, "test.ended").contains("mantle:completed")
                && !"movement:mantle".equals(t.s(t9, "move.id"));
        checks.check("mantle", "climb: full control at T9 (move completes)", "ended mantle:completed at T9",
                "move.id='" + t.s(t9, "move.id") + "' ended='" + t.s(t9, "test.ended") + "'", ended, t.ref(t9));
        checks.near("mantle", "climb: MA10 walk exit at T7 is exactly 0.2159, no input push on top (displacement "
                + "over T7)", 0.2159, step(t, t7), 0.005, t.ref(t7));
        checks.check("mantle", "climb: screenshot at T3", "taken", shot[0] ? "taken" : "missed", shot[0],
                t.ref(t0 + 3));
        climbCamera(t, t0);
    }

    private void noHeadroom(ClientGameTestContext context, TestSingleplayerContext world) {
        FeelCourse.Station station = course.station("ledge_2_no_headroom");
        tp(context, world, station);
        FeelRecorder.Table held = record(context, "mantle_no_headroom_held", "F*15 FJ*14 F*20", 50,
                (i, live) -> false);
        int start = held.started("mantle", 0);
        checks.check("mantle", "ledge_2_no_headroom refuses (held jump)", "no mantle",
                start < 0 ? "no mantle" : "mantle " + held.s(start, "move.phase"), start < 0,
                start < 0 ? held.name() + ".csv all rows" : held.ref(start));
        tp(context, world, station);
        boolean[] slapShot = {false};
        FeelRecorder.Table tap = record(context, "mantle_no_headroom_tap", "F*15 FJ F*20", 36, (i, live) -> {
            if (!slapShot[0] && context.computeOnClient(client -> sh.harold.library.camera.fabric.client.CameraFrameHook.armPose(true).y()) > 0.05) {
                slapShot[0] = true;
                shot(context, "mantle_refusal_slap");
            }
            return false;
        });
        start = tap.started("mantle", 0);
        checks.check("mantle", "ledge_2_no_headroom refuses (tap jump)", "no mantle",
                start < 0 ? "no mantle" : "mantle " + tap.s(start, "move.phase"), start < 0,
                start < 0 ? tap.name() + ".csv all rows" : tap.ref(start));
        refusalSlap(tap, held);
    }

    // ---- roll ----

    /** Walks off the 13-block pillar, then presses sneak for 3 ticks once the feet are under 3.5 blocks up. */
    private void roll(ClientGameTestContext context, TestSingleplayerContext world, String name,
                      IntensityProfile profile) {
        context.runOnClient(client -> FeelExampleClient.setCameraIntensity(profile));
        try {
            tp(context, world, course.station("drop_13"));
            boolean full = profile == IntensityProfile.FULL;
            int[] phase = {0};
            int[] landedAt = {-1};
            boolean[] shot = {false};
            float before = health.now(world);
            TelemetryHandle server = health.start(world);
            FeelRecorder.Table t = record(context, name, "F*60", 120, (i, live) -> {
                if (phase[0] == 0 && !live.onGround()) {
                    phase[0] = 1;
                    script(context, "-*100");
                } else if (phase[0] == 1 && live.vy() < 0.0 && live.y() - floorY() < 3.5) {
                    phase[0] = 2;
                    script(context, "N*3 -*60");
                } else if (phase[0] == 2 && live.onGround() && landedAt[0] < 0) {
                    landedAt[0] = i;
                }
                if (full && !shot[0] && live.move().equals("roll") && live.moveTick() == 5) {
                    shot[0] = true;
                    shot(context, "roll_T5");
                }
                return landedAt[0] >= 0 && i - landedAt[0] >= 30;
            });
            float lowest = health.stop(world, server, out.resolve(name + ".server.csv"));
            String move = "roll";
            String label = full ? "" : " (Reduced)";
            int t0 = t.started("roll", 0);
            checks.check(move, "13-block drop with a timed sneak rolls" + label, "roll starts",
                    t0 < 0 ? "no roll" : "roll, move.tick " + t.s(t0, "move.tick") + " on its first row", t0 >= 0,
                    t0 < 0 ? t.name() + ".csv" : t.ref(t0));
            checks.near(move, "no damage from the 13-block drop (server health, lowest in the run)" + label,
                    before, lowest, 1.0e-3, name + ".server.csv health");
            if (t0 < 0) {
                return;
            }
            if (full) {
                checks.near(move, "eye height 0.9 by T2 (drawn camera_eye_height)", 0.9,
                        t.d(t0 + 2, "camera_eye_height"), 0.02, t.ref(t0 + 2));
                int press = t0 - 1;
                while (press > 0 && !(t.b(press, "input_shift") && !t.b(press - 1, "input_shift"))) {
                    press--;
                }
                double maxRise = 0.0;
                int riseRow = press;
                for (int row = press + 1; row <= t0 + 2; row++) {
                    double rise = t.d(row, "camera_eye_height") - t.d(row - 1, "camera_eye_height");
                    if (rise > maxRise) {
                        maxRise = rise;
                        riseRow = row;
                    }
                }
                checks.near(move, "drawn eye never rises from the sneak press through T2 (max rise per row)", 0.0,
                        maxRise, 1.0e-4, t.ref(press) + " to " + t.ref(t0 + 2) + ", largest at " + t.ref(riseRow));
                for (int k = 3; k <= 6; k++) {
                    checks.near(move, "eye height stays 0.9 at T" + k + " (drawn camera_eye_height)", 0.9,
                            t.d(t0 + k, "camera_eye_height"), 0.02, t.ref(t0 + k));
                }
                for (int k = 11; k <= 15 && t0 + k < t.size() - 1; k++) {
                    checks.near(move, "eye cue offset 0 after T10, T" + k + " (camera.eye_height)", 0.0,
                            t.d(t0 + k, "camera.eye_height"), 1.0e-4, t.ref(t0 + k));
                }
                checks.near(move, "180 deg of added pitch at T5", 180.0, t.d(t0 + 5, "camera.pitch_add"), 1.0,
                        t.ref(t0 + 5));
                int peak = t0;
                for (int row = t0; row <= t0 + 10 && row < t.size(); row++) {
                    if (t.d(row, "camera.fov_add") > t.d(peak, "camera.fov_add")) {
                        peak = row;
                    }
                }
                checks.equal(move, "FOV peak at T3 (tick of max camera.fov_add)", 3, peak - t0, t.ref(peak));
                checks.near(move, "FOV peak +6", 6.0, t.d(peak, "camera.fov_add"), 0.05, t.ref(peak));
                int t10 = t0 + 10;
                double pitch = t.d(t10, "camera.pitch_add");
                checks.near(move, "pitch back by T10 (camera.pitch_add mod 360; raw " + FeelChecks.fmt(pitch) + ")",
                        0.0, Math.IEEEremainder(pitch, 360.0), 0.5, t.ref(t10));
                checks.near(move, "FOV back by T10", 0.0, t.d(t10, "camera.fov_add"), 0.05, t.ref(t10));
                checks.near(move, "eye height back to 1.62 by T10 (drawn camera_eye_height)", STAND_EYE,
                        t.d(t10, "camera_eye_height"), 0.02, t.ref(t10));
                checks.check(move, "screenshot at T5", "taken", shot[0] ? "taken" : "missed", shot[0],
                        t.ref(t0 + 5));
            } else {
                double max = Double.NEGATIVE_INFINITY;
                int maxRow = t0;
                for (int row = t0; row <= t0 + 12 && row < t.size(); row++) {
                    double v = t.d(row, "camera.pitch_add");
                    if (v > max) {
                        max = v;
                        maxRow = row;
                    }
                }
                checks.near(move, "Reduced: a 25 deg dip (max camera.pitch_add)", 25.0, max, 0.5, t.ref(maxRow));
                checks.check(move, "Reduced: no 360 turn", "max pitch_add < 90", FeelChecks.fmt(max), max < 90.0,
                        t.ref(maxRow));
            }
        } finally {
            context.runOnClient(client -> FeelExampleClient.setCameraIntensity(IntensityProfile.FULL));
        }
    }

    /** Sneak pressed as soon as the feet leave the pillar and held through the landing: a press outside the window. */
    private void rollHeldSneak(ClientGameTestContext context, TestSingleplayerContext world) {
        tp(context, world, course.station("drop_13"));
        int[] phase = {0};
        int[] landedAt = {-1};
        float before = health.now(world);
        TelemetryHandle server = health.start(world);
        FeelRecorder.Table t = record(context, "roll_sneak_held", "F*60", 120, (i, live) -> {
            if (phase[0] == 0 && !live.onGround()) {
                phase[0] = 1;
                script(context, "N*100");
            } else if (phase[0] == 1 && live.onGround() && landedAt[0] < 0) {
                landedAt[0] = i;
            }
            return landedAt[0] >= 0 && i - landedAt[0] >= 20;
        });
        float lowest = health.stop(world, server, out.resolve("roll_sneak_held.server.csv"));
        int start = t.started("roll", 0);
        checks.check("roll", "sneak held through the fall does not roll", "no roll", start < 0 ? "no roll" : "roll",
                start < 0, start < 0 ? t.name() + ".csv all rows" : t.ref(start));
        checks.check("roll", "sneak held: the fall hurts (control for the damage check)", "health drops",
                FeelChecks.fmt(before) + " -> lowest " + FeelChecks.fmt(lowest), lowest < before,
                "roll_sneak_held.server.csv health");
    }

    // ---- slide ----

    private void slide(ClientGameTestContext context, TestSingleplayerContext world) {
        tp(context, world, course.station("duct"));
        boolean[] shot = {false};
        FeelRecorder.Table t = record(context, "slide", "FS*14 FSN*30 F*6 -*10", 60, (i, live) -> {
            if (!shot[0] && live.move().equals("slide") && live.moveTick() == 3) {
                shot[0] = true;
                shot(context, "slide_T3");
            }
            return false;
        });
        int t0 = t.started("slide", 0);
        checks.check("slide", "S1: sneak while sprinting starts a slide", "slide", t0 < 0 ? "no slide" : "slide",
                t0 >= 0, t0 < 0 ? t.name() + ".csv" : t.ref(t0));
        if (t0 < 2) {
            return;
        }
        double entry = Math.hypot(t.d(t0 - 1, "pos_x") - t.d(t0 - 2, "pos_x"),
                t.d(t0 - 1, "pos_z") - t.d(t0 - 2, "pos_z"));
        checks.check("slide", "precondition: ground speed on the tick before T0 >= 0.25", ">= 0.25",
                FeelChecks.fmt(entry), entry >= 0.25, t.ref(t0 - 1));
        int t15 = t0 + 15;
        double distance = Math.hypot(t.d(t15, "pos_x") - t.d(t0 - 1, "pos_x"),
                t.d(t15, "pos_z") - t.d(t0 - 1, "pos_z"));
        checks.near("slide", "S1: 4.21 blocks in 16 ticks (start of T0 to end of T15)", 4.21, distance, 0.05,
                t.ref(t0 - 1) + " to tick=" + t.tick(t15));
        checks.near("slide", "eye height 0.5 at T3 (drawn camera_eye_height)", 0.5, t.d(t0 + 3, "camera_eye_height"),
                0.02, t.ref(t0 + 3));
        for (int k = 0; k <= 3; k++) {
            checks.near("slide", "hitbox 0.6 at T" + k, 0.6, t.d(t0 + k, "body.height"), 1.0e-6, t.ref(t0 + k));
        }
        checks.near("slide", "S1: the last slide tick T15 still moves at the floor (displacement over T15)", 0.19014,
                step(t, t15), 0.005, t.ref(t15));
        int end = t0;
        while (end < t.size() && "movement:slide".equals(t.s(end, "move.id"))
                && List.of("drop", "slide").contains(t.s(end, "move.phase"))) {
            end++;
        }
        checks.equal("slide", "S1: the sliding phases (drop + slide) end on the speed floor after 16 ticks", 16,
                end - t0, t.ref(Math.min(end, t.size() - 1)));
        checks.note("slide", "length of the sliding phases (drop + slide) on flat ground",
                "S1: a 16-tick slide", "T0..T" + (end - t0 - 1) + " (" + (end - t0) + " ticks), then phase '"
                        + (end < t.size() ? t.s(end, "move.phase") : "?") + "' at speed "
                        + FeelChecks.fmt(end < t.size() ? step(t, end) : Double.NaN) + "; distance to then "
                        + FeelChecks.fmt(Math.hypot(t.d(end - 1, "pos_x") - t.d(t0 - 1, "pos_x"),
                        t.d(end - 1, "pos_z") - t.d(t0 - 1, "pos_z"))),
                t.ref(Math.min(end, t.size() - 1)));
        checks.check("slide", "screenshot at T3", "taken", shot[0] ? "taken" : "missed", shot[0], t.ref(t0 + 3));
    }

    // ---- wall jump ----

    /** Walk into the wall, jump, then press jump every other tick with forward held: one kick, then refusals. */
    private void singleWall(ClientGameTestContext context, TestSingleplayerContext world) {
        FeelCourse.Station station = course.station("single_wall");
        tp(context, world, station);
        boolean[] shot = {false};
        int[] kickAt = {-1};
        String frames = "F*8 FJ F*3 " + "F FJ ".repeat(20) + "F*20";
        FeelRecorder.Table t = record(context, "wall_jump_single", frames, 80, (i, live) -> {
            if (kickAt[0] < 0 && live.started().contains("wall_jump")) {
                kickAt[0] = i;
            }
            if (!shot[0] && kickAt[0] >= 0 && i == kickAt[0] + 2) {
                shot[0] = true;
                shot(context, "wall_jump_T2");
            }
            return false;
        });
        List<Integer> kicks = t.allStarted("wall_jump");
        checks.check("wall_jump", "single wall: a kick happens", ">= 1 kick", kicks.size() + " kicks",
                !kicks.isEmpty(), kicks.isEmpty() ? t.name() + ".csv" : t.ref(kicks.getFirst()));
        if (kicks.isEmpty()) {
            return;
        }
        int t0 = kicks.getFirst();
        kickTimeline(t, t0, "single wall");
        sameWall(t, t0, station.start().getX(), "single wall from the floor", false);
        checks.check("wall_jump", "screenshot at T2", "taken", shot[0] ? "taken" : "missed", shot[0], t.ref(t0 + 2));
    }

    /**
     * The same-wall rule needs a second press while touching the wall the player kicked off, still airborne. From the
     * floor the kick lands before air control brings the player back, so this run starts 7 blocks up against the
     * single wall, kicks after 4 falling ticks and keeps pressing jump every other tick with forward held.
     */
    private void singleWallDrop(ClientGameTestContext context, TestSingleplayerContext world) {
        FeelCourse.Station station = course.station("single_wall");
        double face = station.start().getX();
        double x = face + 0.31;
        double y = station.start().getY() + 7.0;
        double z = station.start().getZ() + 0.5;
        script(context, "");
        world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.4f %.4f %.4f %.2f 0", x, y, z,
                station.yaw()));
        context.waitFor(client -> client.player != null && Math.abs(client.player.getX() - x) < 1.0e-3
                && client.player.getY() > y - 1.0, 100);
        String frames = "F*4 FJ " + "F FJ ".repeat(25) + "F*10";
        FeelRecorder.Table t = record(context, "wall_jump_single_drop", frames, 70, (i, live) -> false);
        List<Integer> kicks = t.allStarted("wall_jump");
        checks.check("wall_jump", "single wall from a drop: a kick happens", ">= 1 kick", kicks.size() + " kicks",
                !kicks.isEmpty(), kicks.isEmpty() ? t.name() + ".csv" : t.ref(kicks.getFirst()));
        if (!kicks.isEmpty()) {
            sameWall(t, kicks.getFirst(), face, "single wall from a drop", true);
        }
    }

    /** No kick between {@code t0} and landing; counts the fresh jump presses made within 0.1 of the wall face. */
    private void sameWall(FeelRecorder.Table t, int t0, double face, String label, boolean mustExercise) {
        int land = t.size();
        for (int row = t0 + 1; row < t.size(); row++) {
            if (t.b(row, "on_ground")) {
                land = row;
                break;
            }
        }
        int contactPresses = 0;
        double closest = Double.POSITIVE_INFINITY;
        int closestRow = t0;
        for (int row = t0 + 1; row < land; row++) {
            double gap = t.d(row, "pos_x") - 0.3 - face;
            if (gap < closest) {
                closest = gap;
                closestRow = row;
            }
            boolean pressed = t.s(row, "move.input").contains("J") && !t.s(row - 1, "move.input").contains("J");
            if (gap <= 0.1 + 1.0e-6 && pressed) {
                contactPresses++;
            }
        }
        int landRow = land;
        long extra = t.allStarted("wall_jump").stream().filter(row -> row > t0 && row < landRow).count();
        String span = t.ref(t0) + " to " + (land < t.size() ? "tick=" + t.tick(land) : "end");
        checks.equal("wall_jump", label + ": same-wall rule, no second kick before landing", 0, extra, span);
        String actual = contactPresses + " presses; closest air gap " + FeelChecks.fmt(closest);
        if (mustExercise) {
            checks.check("wall_jump", label + ": same-wall rule exercised (jump pressed again within 0.1 of the "
                    + "wall, airborne)", ">= 1 press", actual, contactPresses > 0, span);
        } else {
            checks.note("wall_jump", label + ": presses within 0.1 of the wall after the kick, airborne",
                    "air control does not return to the wall before landing", actual, t.ref(closestRow));
        }
    }

    private void kickTimeline(FeelRecorder.Table t, int t0, String label) {
        double rise = t.d(t0, "pos_y") - t.d(t0 - 1, "pos_y");
        checks.near("wall_jump", label + ": upward speed 0.48 at T0 (pos_y gain over T0)", 0.48, rise, 0.005,
                t.ref(t0));
        checks.near("wall_jump", label + ": vel_y after T0's gravity and drag, (0.48 - 0.08) * 0.98", 0.392,
                t.d(t0, "vel_y"), 0.005, t.ref(t0));
        checks.near("wall_jump", label + ": roll 7 deg by T2 (|camera.roll|)", 7.0,
                Math.abs(t.d(t0 + 2, "camera.roll")), 0.1, t.ref(t0 + 2));
        checks.near("wall_jump", label + ": FOV +5 by T2", 5.0, t.d(t0 + 2, "camera.fov_add"), 0.1, t.ref(t0 + 2));
        checks.near("wall_jump", label + ": roll back by T10", 0.0, t.d(t0 + 10, "camera.roll"), 0.05,
                t.ref(t0 + 10));
        checks.near("wall_jump", label + ": FOV back by T10", 0.0, t.d(t0 + 10, "camera.fov_add"), 0.05,
                t.ref(t0 + 10));
    }

    /** Back into the west wall, jump, then press jump every other tick with no movement keys. */
    private void shaftChain(ClientGameTestContext context, TestSingleplayerContext world) {
        tp(context, world, course.station("shaft_3"));
        String frames = "B*6 BJ -*2 " + "J - ".repeat(60);
        FeelRecorder.Table t = record(context, "wall_jump_shaft3", frames, 130, (i, live) -> false);
        List<Integer> kicks = t.allStarted("wall_jump");
        if (kicks.isEmpty()) {
            checks.check("wall_jump", "shaft_3: kicks chain between the facing walls without landing",
                    ">= 3 kicks, alternating sides", "0 kicks", false, t.name() + ".csv");
            return;
        }
        int first = kicks.getFirst();
        int land = t.size();
        for (int row = first + 1; row < t.size(); row++) {
            if (t.b(row, "on_ground")) {
                land = row;
                break;
            }
        }
        int chained = 0;
        int alternating = 0;
        double lastDir = 0.0;
        StringBuilder sides = new StringBuilder();
        for (int row : kicks) {
            if (row >= land) {
                break;
            }
            chained++;
            double dir = Math.signum(t.d(row, "vel_x"));
            sides.append(dir > 0 ? "E" : "W");
            if (lastDir != 0.0 && dir != lastDir) {
                alternating++;
            }
            lastDir = dir;
        }
        double top = 0.0;
        for (int row = 0; row < land; row++) {
            top = Math.max(top, t.d(row, "pos_y") - floorY());
        }
        checks.check("wall_jump", "shaft_3: kicks chain between the facing walls without landing",
                ">= 3 kicks, alternating sides", chained + " kicks (" + sides + "), peak height "
                        + FeelChecks.fmt(top), chained >= 3 && alternating == chained - 1,
                t.ref(first) + " to " + (land < t.size() ? "tick=" + t.tick(land) : "end"));
    }

    // ---- 2026-11-23 rulings ----

    /** J3: the press-length column shows the length of the press once, on the tick it is released. */
    private void pressLength(FeelRecorder.Table t, String label, long expected) {
        long max = 0;
        int at = -1;
        for (int row = 0; row < t.size(); row++) {
            double v = t.d(row, "move.jump_press_ticks");
            if (!Double.isNaN(v) && v > max) {
                max = (long) v;
                at = row;
            }
        }
        checks.equal("jump", "J3: move.jump_press_ticks reports the press length, " + label, expected, max,
                at < 0 ? t.name() + ".csv (column never set)" : t.ref(at));
    }

    /** First row whose recorded input holds {@code key}, at or after {@code from}; -1 when none. */
    private static int firstInput(FeelRecorder.Table t, String key, int from) {
        for (int row = Math.max(0, from); row < t.size(); row++) {
            if (t.s(row, "move.input").contains(key)) {
                return row;
            }
        }
        return -1;
    }

    private static int firstGrounded(FeelRecorder.Table t, int from) {
        for (int row = Math.max(0, from); row < t.size(); row++) {
            if (t.b(row, "on_ground")) {
                return row;
            }
        }
        return -1;
    }

    private static double maxOf(FeelRecorder.Table t, int from, int to, String column) {
        double max = Double.NEGATIVE_INFINITY;
        for (int row = Math.max(0, from); row <= to && row < t.size(); row++) {
            double v = t.d(row, column);
            if (!Double.isNaN(v)) {
                max = Math.max(max, v);
            }
        }
        return max;
    }

    private static double minOf(FeelRecorder.Table t, int from, int to, String column) {
        return -maxOfNeg(t, from, to, column);
    }

    private static double maxOfNeg(FeelRecorder.Table t, int from, int to, String column) {
        double max = Double.NEGATIVE_INFINITY;
        for (int row = Math.max(0, from); row <= to && row < t.size(); row++) {
            double v = t.d(row, column);
            if (!Double.isNaN(v)) {
                max = Math.max(max, -v);
            }
        }
        return max;
    }

    /** Teleports into the air (no ground wait), with health, food and fall distance restored. */
    private void airTp(ClientGameTestContext context, TestSingleplayerContext world, double x, double y, double z,
                       float yaw) {
        script(context, "");
        world.getServer().runOnServer(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.setHealth(player.getMaxHealth());
                player.getFoodData().setFoodLevel(20);
                player.resetFallDistance();
            }
        });
        world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.4f %.4f %.4f %.2f 0", x, y, z, yaw));
        context.waitFor(client -> client.player != null && Math.abs(client.player.getX() - x) < 1.0e-3
                && client.player.getY() > y - 1.0, 100);
        world.getServer().runOnServer(server -> server.getPlayerList().getPlayers()
                .forEach(ServerPlayer::resetFallDistance));
    }

    /**
     * Runs toward a wall at {@code wallZ} (+z) with {@code run}, switching to {@code then} on the first tick that ends
     * with the feet within 0.45 (the mantle reach) of its face; stops when {@code then} has played out.
     */
    private FeelRecorder.Table approach(ClientGameTestContext context, String name, String run, String then,
                                        double wallZ, int maxTicks, TickBody after) {
        boolean[] switched = {false};
        return record(context, name, run, maxTicks, (i, live) -> {
            boolean done = switched[0] && live.remaining() == 0;
            if (!switched[0] && wallZ - (live.z() + 0.3) <= 0.45) {
                switched[0] = true;
                script(context, then);
            }
            return after.tick(i, live) || done;
        });
    }

    /** MA5: walking (sprint off) into a 1-block step and tapping jump at it is the vanilla jump, not a mantle. */
    private void walkingTap(ClientGameTestContext context, TestSingleplayerContext world) {
        tp(context, world, course.station("step_1"));
        double wallZ = origin.getZ() + 34.0;
        FeelRecorder.Table t = approach(context, "mantle_walking_tap", "F*90", "FJ F*3 -*25", wallZ, 100,
                (i, live) -> false);
        int tap = firstInput(t, "J", 0);
        if (tap < 1) {
            checks.check("mantle", "walking tap: the tap happened", "a J row", "none", false, t.name() + ".csv");
            return;
        }
        double speed = step(t, tap - 1);
        checks.check("mantle", "precondition: walking at the tap (sprint state off; walk speed 0.2159)",
                "test.sprinting false", "sprinting " + t.s(tap, "test.sprinting") + ", speed "
                        + FeelChecks.fmt(speed), !t.b(tap, "test.sprinting"), t.ref(tap));
        int start = t.started("mantle", 0);
        checks.check("mantle", "MA5: a walking tap at a 1-block step does not mantle", "no mantle",
                start < 0 ? "no mantle" : "mantle " + t.s(start, "move.phase"), start < 0,
                start < 0 ? t.name() + ".csv all rows" : t.ref(start));
        int last = t.size() - 1;
        checks.near("mantle", "MA5: the vanilla jump lands on the step (feet 1.0 up)", 1.0,
                t.d(last, "pos_y") - floorY(), 1.0e-3, t.ref(last));
        checks.near("mantle", "MA5: the jump is vanilla (first-tick rise 0.42)", 0.42,
                t.d(tap, "pos_y") - t.d(tap - 1, "pos_y"), 1.0e-3, t.ref(tap));
    }

    /**
     * MA6, MA7, MA2, MA3, MA18, MA10: sprinting into the 1-block step and tapping jump at it vaults, keeps the run's
     * speed, nods the camera and plants the main arm. With {@code hop}, a jump pressed at T4 fires on T5.
     */
    private void vault(ClientGameTestContext context, TestSingleplayerContext world, boolean hop) {
        tp(context, world, course.station("step_1"));
        double wallZ = origin.getZ() + 34.0;
        boolean[] hopped = {false};
        String name = hop ? "mantle_vault_hop" : "mantle_vault";
        FeelRecorder.Table t = approach(context, name, "FS*90", "FSJ FS*7 -*20", wallZ, 110, (i, live) -> {
            if (hop && !hopped[0] && live.started().contains("mantle")) {
                hopped[0] = true;
                script(context, "FS*3 FSJ FS*3 -*20");
            }
            return false;
        });
        String label = hop ? "vault hop" : "vault";
        int t0 = t.started("mantle", 0);
        String kind = t0 < 0 ? "none" : t.s(t0, "move.phase");
        checks.check("mantle", label + ": a sprint tap at the 1-block step vaults (MA6)", "vault_swing", kind,
                kind.startsWith("vault"), t0 < 0 ? t.name() + ".csv" : t.ref(t0));
        if (t0 < 2) {
            return;
        }
        double entry = step(t, t0 - 1);
        checks.check("mantle", label + ": precondition, entered at sprint speed", "> 0.27 and sprinting",
                FeelChecks.fmt(entry) + ", sprinting " + t.s(t0 - 1, "test.sprinting"),
                entry > 0.27 && t.b(t0 - 1, "test.sprinting"), t.ref(t0 - 1));
        int mantleRows = 0;
        for (int row = t0; row < t.size() && "movement:mantle".equals(t.s(row, "move.id")); row++) {
            mantleRows++;
        }
        checks.equal("mantle", label + ": move runs T0..T4 (MA15)", 5, mantleRows, t.ref(t0));
        int t5 = t0 + 5;
        checks.check("mantle", label + ": move completes at T5", "ended mantle:completed",
                "ended='" + t.s(t5, "test.ended") + "'", t.s(t5, "test.ended").contains("mantle:completed"),
                t.ref(t5));
        if (!hop) {
            checks.near("mantle", "vault: exit tick T5 moves at exactly max(entry " + FeelChecks.fmt(entry)
                    + ", 0.2806), no input push on top (displacement over T5)", Math.max(entry, 0.2806), step(t, t5),
                    0.005, t.ref(t5));
            for (int k = 5; k <= 7; k++) {
                double s = step(t, t0 + k);
                checks.check("mantle", "vault: speed kept at T" + k + " (MA7: exit = max(entry "
                        + FeelChecks.fmt(entry) + ", 0.2806))", "> 0.27", FeelChecks.fmt(s), s > 0.27, t.ref(t0 + k));
            }
            int last = t.size() - 1;
            checks.near("mantle", "vault: ends standing on the step (feet 1.0 up)", 1.0, t.d(last, "pos_y") - floorY(),
                    1.0e-3, t.ref(last));
            checks.near("mantle", "vault: MA2 nod +6 at T2 (camera.pitch_add)", 6.0, t.d(t0 + 2, "camera.pitch_add"),
                    0.1, t.ref(t0 + 2));
            checks.near("mantle", "vault: MA2 nod back to 0 by T5", 0.0, t.d(t5, "camera.pitch_add"), 0.05,
                    t.ref(t5));
            checks.near("mantle", "vault: MA2 no roll (max |camera.roll| T0..T6)", 0.0, maxAbs(t, t0, t0 + 6,
                    "camera.roll"), 1.0e-6, t.ref(t0) + " to " + t.ref(t0 + 6));
            checks.near("mantle", "vault: MA3 main arm planted at T0 (camera.arm_main_rot_x)", -46.0,
                    t.d(t0, "camera.arm_main_rot_x"), 0.5, t.ref(t0));
            checks.near("mantle", "vault: MA3 main arm up 0.24 at T0 (camera.arm_main_y)", 0.24,
                    t.d(t0, "camera.arm_main_y"), 0.005, t.ref(t0));
            double off = 0.0;
            for (String column : ARM_COLUMNS.subList(6, 12)) {
                off = Math.max(off, maxAbs(t, t0, t0 + 6, column));
            }
            checks.near("mantle", "vault: MA3 only the planting arm moves (max |off arm| T0..T6)", 0.0, off, 1.0e-6,
                    t.ref(t0) + " to " + t.ref(t0 + 6));
            checks.near("mantle", "vault: arms back to rest by T5 (max |arm| at T5)", 0.0, armMax(t, t5), 1.0e-6,
                    t.ref(t5));
            checks.near("mantle", "vault: MA18 no FOV (max |camera.fov_add| T0..T8)", 0.0,
                    maxAbs(t, t0, t0 + 8, "camera.fov_add"), 1.0e-6, t.ref(t0) + " to " + t.ref(t0 + 8));
        } else {
            checks.check("mantle", "vault hop: precondition, jump pressed at T4", "J at T4",
                    t.s(t0 + 4, "move.input"), t.s(t0 + 4, "move.input").contains("J"), t.ref(t0 + 4));
            double rise = t.d(t5, "pos_y") - t.d(t5 - 1, "pos_y");
            checks.near("mantle", "vault hop: MA10 the jump fires on T5 (pos_y rise over T5)", 0.42, rise, 0.01,
                    t.ref(t5));
            checks.near("mantle", "vault hop: MA10 the hop tick T5 moves at exactly max(entry "
                    + FeelChecks.fmt(entry) + ", 0.2806) (displacement over T5)", Math.max(entry, 0.2806),
                    step(t, t5), 0.005, t.ref(t5));
            double apex = maxOf(t, t5, t.size() - 1, "pos_y") - floorY() - 1.0;
            checks.check("mantle", "vault hop: MA10 no extra height (apex above the step)", "<= 1.26",
                    FeelChecks.fmt(apex), apex <= 1.26, t.name() + ".csv after " + t.ref(t5));
        }
    }

    private static double armMax(FeelRecorder.Table t, int row) {
        double max = 0.0;
        for (String column : ARM_COLUMNS) {
            double v = t.d(row, column);
            if (!Double.isNaN(v)) {
                max = Math.max(max, Math.abs(v));
            }
        }
        return max;
    }

    /** MA1, MA3, MA13, MA14, MA18 on the recorded climb. */
    private void climbCamera(FeelRecorder.Table t, int t0) {
        double[][] pitch = {{0, 4}, {1, 8}, {3, 15}, {5, 10}, {8, 0}};
        for (double[] key : pitch) {
            int row = t0 + (int) key[0];
            checks.near("mantle", "climb: MA1 pitch +" + FeelChecks.fmt(key[1]) + " at T" + (int) key[0]
                    + " (camera.pitch_add, looking down at the hands)", key[1], t.d(row, "camera.pitch_add"), 0.1,
                    t.ref(row));
        }
        double t7 = t.d(t0 + 7, "camera.pitch_add");
        checks.check("mantle", "climb: MA1 pitch at most +3 at T7", "0 <= pitch_add <= 3", FeelChecks.fmt(t7),
                t7 >= -1.0e-6 && t7 <= 3.0 + 1.0e-6, t.ref(t0 + 7));
        checks.near("mantle", "climb: MA13 eye drops 0.08 at T0 (camera.eye_height)", -0.08,
                t.d(t0, "camera.eye_height"), 0.002, t.ref(t0));
        checks.near("mantle", "climb: MA13 eye recovered by T2", 0.0, t.d(t0 + 2, "camera.eye_height"), 0.002,
                t.ref(t0 + 2));
        double r1 = t.d(t0 + 1, "camera.roll");
        double r4 = t.d(t0 + 4, "camera.roll");
        checks.near("mantle", "climb: MA14 roll 2 toward the lead hand at T1 (|camera.roll|)", 2.0, Math.abs(r1),
                0.05, t.ref(t0 + 1));
        checks.check("mantle", "climb: MA14 roll rocks back 1.5 the other way at T4", "-1.5 x sign(T1)",
                FeelChecks.fmt(r4), Math.abs(r4 + 1.5 * Math.signum(r1)) <= 0.05, t.ref(t0 + 4));
        checks.near("mantle", "climb: MA14 roll back to 0 by T8", 0.0, t.d(t0 + 8, "camera.roll"), 0.05,
                t.ref(t0 + 8));
        for (int k = 1; k <= 3; k++) {
            checks.near("mantle", "climb: MA3 main arm grip at T" + k + " (camera.arm_main_rot_x)", -46.0,
                    t.d(t0 + k, "camera.arm_main_rot_x"), 0.5, t.ref(t0 + k));
        }
        checks.near("mantle", "climb: MA3 off arm grip at T3 (camera.arm_off_rot_x)", -46.0,
                t.d(t0 + 3, "camera.arm_off_rot_x"), 0.5, t.ref(t0 + 3));
        checks.near("mantle", "climb: MA3 both arms up 0.24 at T3 (camera.arm_main_y)", 0.24,
                t.d(t0 + 3, "camera.arm_main_y"), 0.005, t.ref(t0 + 3));
        checks.near("mantle", "climb: MA3 arms back to rest at T8 (max |arm|)", 0.0, armMax(t, t0 + 8), 1.0e-6,
                t.ref(t0 + 8));
        checks.near("mantle", "climb: MA18 no FOV (max |camera.fov_add| T0..T9)", 0.0,
                maxAbs(t, t0, t0 + 9, "camera.fov_add"), 1.0e-6, t.ref(t0) + " to " + t.ref(t0 + 9));
    }

    /** MA10: a jump pressed at T5 of a climb fires at T7 with +0.1 forward and an FOV +2 pulse. */
    private void climbHop(ClientGameTestContext context, TestSingleplayerContext world) {
        tp(context, world, course.station("ledge_3"));
        boolean[] switched = {false};
        FeelRecorder.Table t = record(context, "mantle_climb_hop", "F*15 FJ*14 F*20", 50, (i, live) -> {
            if (!switched[0] && live.started().contains("mantle")) {
                switched[0] = true;
                script(context, "F*4 FJ F*25");
            }
            return false;
        });
        int t0 = t.started("mantle", 0);
        String kind = t0 < 0 ? "none" : t.s(t0, "move.phase");
        checks.check("mantle", "climb hop: ledge_3 climb", "climb", kind, kind.startsWith("climb"),
                t0 < 0 ? t.name() + ".csv" : t.ref(t0));
        if (t0 < 0) {
            return;
        }
        int t7 = t0 + 7;
        checks.check("mantle", "climb hop: precondition, a fresh jump press at T5", "J at T5, none at T4",
                t.s(t0 + 4, "move.input") + " / " + t.s(t0 + 5, "move.input"),
                t.s(t0 + 5, "move.input").contains("J") && !t.s(t0 + 4, "move.input").contains("J"), t.ref(t0 + 5));
        double early = t.d(t0 + 6, "vel_y");
        checks.check("mantle", "climb hop: nothing fires before T7", "no upward launch at T6",
                "vel_y " + FeelChecks.fmt(early), early < 0.3, t.ref(t0 + 6));
        checks.near("mantle", "climb hop: MA10 the jump fires at T7 (pos_y rise over T7)", 0.42,
                t.d(t7, "pos_y") - t.d(t7 - 1, "pos_y"), 0.01, t.ref(t7));
        checks.near("mantle", "climb hop: MA10 walk exit 0.2159 + 0.1 forward (displacement over T7)", 0.3159,
                step(t, t7), 0.005, t.ref(t7));
        double fov = maxOf(t, t7, t7 + 8, "camera.fov_add");
        checks.near("mantle", "climb hop: MA10/MA18 FOV +2 pulse after T7 (max camera.fov_add)", 2.0, fov, 0.05,
                t.ref(t7) + " to " + t.ref(t7 + 8));
    }

    /** MA10: a climb with sprint held exits at sprint speed on T7, with no input push on top. */
    private void climbSprint(ClientGameTestContext context, TestSingleplayerContext world) {
        tp(context, world, course.station("ledge_3"));
        boolean[] switched = {false};
        FeelRecorder.Table t = record(context, "mantle_climb_sprint", "F*15 FJ*14 F*20", 50, (i, live) -> {
            if (!switched[0] && live.started().contains("mantle")) {
                switched[0] = true;
                script(context, "FS*30");
            }
            return false;
        });
        int t0 = t.started("mantle", 0);
        String kind = t0 < 0 ? "none" : t.s(t0, "move.phase");
        checks.check("mantle", "climb sprint: ledge_3 climb", "climb", kind, kind.startsWith("climb"),
                t0 < 0 ? t.name() + ".csv" : t.ref(t0));
        if (t0 < 0) {
            return;
        }
        int t7 = t0 + 7;
        checks.check("mantle", "climb sprint: precondition, sprint held and sprinting at T7", "FS, test.sprinting true",
                t.s(t7, "move.input") + ", sprinting " + t.s(t7, "test.sprinting"),
                t.s(t7, "move.input").contains("S") && t.b(t7, "test.sprinting"), t.ref(t7));
        checks.near("mantle", "climb sprint: MA10 sprint exit at T7 is exactly 0.2806 (displacement over T7)", 0.2806,
                step(t, t7), 0.005, t.ref(t7));
    }

    /** MA12: the refused tap shows one slap on both arms and moves exactly like a vanilla tap jump. */
    private void refusalSlap(FeelRecorder.Table tap, FeelRecorder.Table held) {
        double main = maxOf(tap, 0, tap.size() - 1, "camera.arm_main_y");
        double off = maxOf(tap, 0, tap.size() - 1, "camera.arm_off_y");
        checks.check("mantle", "refusal: MA12 slap shows (arm channels non-zero)", "arm_main_y > 0, arm_off_y > 0",
                "main " + FeelChecks.fmt(main) + ", off " + FeelChecks.fmt(off), main > 0.0 && off > 0.0,
                tap.name() + ".csv camera.arm_*");
        checks.equal("mantle", "refusal: MA12 one slap per press (tap)", 1, onsets(tap), tap.name() + ".csv");
        checks.equal("mantle", "refusal: MA12 one slap per press (jump held 14 ticks)", 1, onsets(held),
                held.name() + ".csv");
        int press = firstInput(tap, "J", 0);
        if (press < 1) {
            return;
        }
        // Vanilla tap jump: 0.42 up, then (vy - 0.08) * 0.98, landing on the floor; the wall blocks +z throughout.
        double y = 0.42;
        double vy = (0.42 - 0.08) * 0.98;
        double worst = 0.0;
        int worstRow = press;
        double z = tap.d(press - 1, "pos_z");
        double worstZ = 0.0;
        // Forward stays held into the wall all the way down; the refusal blocks the wall slide for the rest of the
        // airtime, so the body follows the vanilla tap jump to the floor.
        int landed = firstGrounded(tap, press + 1);
        int slide = tap.started("wall_slide", press);
        boolean slidAirborne = slide >= 0 && (landed < 0 || slide <= landed);
        checks.check("mantle", "refusal: no wall slide for the rest of the refused airtime (forward held into the "
                        + "wall)", "no wall_slide start before landing", (slide < 0 ? "no wall slide"
                        : "wall slide from " + tap.ref(slide)) + (landed < 0 ? ", no landing" : ", landed " + tap.ref(landed)),
                !slidAirborne && landed >= 0, slide < 0 ? tap.ref(press) + " to " + (landed < 0 ? "end" : tap.ref(landed))
                        : tap.ref(slide));
        int stop = Math.min(tap.size(), landed < 0 ? press + 16 : landed + 2);
        for (int row = press; row < stop; row++) {
            double dy = Math.abs(tap.d(row, "pos_y") - floorY() - y);
            if (dy > worst) {
                worst = dy;
                worstRow = row;
            }
            worstZ = Math.max(worstZ, Math.abs(tap.d(row, "pos_z") - z));
            y += vy;
            vy = (vy - 0.08) * 0.98;
            if (y <= 0.0) {
                y = 0.0;
                vy = 0.0;
            }
        }
        checks.near("mantle", "refusal: MA12 no motion change, pos_y follows the vanilla tap jump through the landing "
                + "(max |dy|, " + (stop - press) + " rows)", 0.0, worst, 1.0e-3, tap.ref(worstRow));
        checks.near("mantle", "refusal: MA12 no motion change, pos_z stays at the wall (max |dz|)", 0.0, worstZ,
                1.0e-3, tap.ref(press) + " to " + tap.ref(Math.min(tap.size() - 1, press + 15)));
    }

    private static int onsets(FeelRecorder.Table t) {
        int count = 0;
        double previous = 0.0;
        for (int row = 0; row < t.size(); row++) {
            double v = t.d(row, "camera.arm_main_y");
            if (Double.isNaN(v)) {
                continue;
            }
            if (v > 1.0e-6 && previous <= 1.0e-6) {
                count++;
            }
            previous = v;
        }
        return count;
    }

    /** J2: holding jump through a landing auto-repeats a vanilla jump; only the fresh press is boosted. */
    private void freshPress(ClientGameTestContext context, TestSingleplayerContext world) {
        tp(context, world, origin.getX() + 10.5, origin.getY(), origin.getZ() + 18.5, 0.0f);
        FeelRecorder.Table t = record(context, "jump_fresh_press", "J*40 -*25", 65, (i, live) -> false);
        List<Integer> takeoffs = new ArrayList<>();
        for (int row = 0; row < t.size(); row++) {
            boolean up = t.d(row, "pos_y") - floorY() > 1.0e-3;
            boolean before = row > 0 && t.d(row - 1, "pos_y") - floorY() > 1.0e-3;
            if (up && !before) {
                takeoffs.add(row);
            }
        }
        checks.check("jump", "fresh press: holding J auto-repeats the jump", ">= 2 takeoffs",
                takeoffs.size() + " takeoffs", takeoffs.size() >= 2, t.name() + ".csv");
        if (takeoffs.size() < 2) {
            return;
        }
        int first = takeoffs.get(0);
        int second = takeoffs.get(1);
        double apex1 = maxOf(t, first, second - 1, "pos_y") - floorY();
        int land2 = firstGrounded(t, second + 1);
        double apex2 = maxOf(t, second, land2 < 0 ? t.size() - 1 : land2, "pos_y") - floorY();
        checks.near("jump", "fresh press: J2 the fresh press is boosted (apex)", 1.87, apex1, 0.01, t.ref(first));
        checks.near("jump", "fresh press: J2 the held auto-repeat is vanilla (apex)", 1.2522, apex2, 0.01,
                t.ref(second));
        checks.near("jump", "fresh press: J2 no boost FOV on the auto-repeat (max |camera.fov_add|)", 0.0,
                maxAbs(t, second, second + 12, "camera.fov_add"), 1.0e-6, t.ref(second) + " to "
                        + t.ref(second + 12));
    }

    /** J4: no dip for a 1-block drop; 0.03 + 0.045 x (fall - 1.3) for a 2-block one. */
    private void landingDip(ClientGameTestContext context, TestSingleplayerContext world) {
        for (int height : new int[]{1, 2}) {
            tp(context, world, course.station("drop_" + height));
            int[] phase = {0};
            int[] landedAt = {-1};
            FeelRecorder.Table t = record(context, "landing_drop_" + height, "F*40", 70, (i, live) -> {
                if (phase[0] == 0 && !live.onGround()) {
                    phase[0] = 1;
                    script(context, "-*60");
                } else if (phase[0] == 1 && live.onGround() && landedAt[0] < 0) {
                    landedAt[0] = i;
                }
                return landedAt[0] >= 0 && i - landedAt[0] >= 15;
            });
            // The fall is the drop height: body.fall_distance is sampled at tick ends, so its largest recorded value
            // misses the last air tick before vanilla clears it on landing.
            double expected = height <= 1.3 ? 0.0 : Math.min(0.25, 0.03 + 0.045 * (height - 1.3));
            double dip = minOf(t, 0, t.size() - 1, "camera.eye_height");
            checks.near("jump", "J4: landing dip after a " + height + "-block drop (min camera.eye_height)",
                    -expected, dip, 0.003, t.name() + ".csv");
        }
    }

    /** J5: a hard landing (8 blocks, no roll) cuts sprint for 6 ticks, with no camera shake. */
    private void stumble(ClientGameTestContext context, TestSingleplayerContext world) {
        tp(context, world, course.station("drop_8"));
        int[] phase = {0};
        int[] landedAt = {-1};
        FeelRecorder.Table t = record(context, "jump_stumble", "FS*80", 70, (i, live) -> {
            if (phase[0] == 0 && !live.onGround()) {
                phase[0] = 1;
            } else if (phase[0] == 1 && live.onGround() && landedAt[0] < 0) {
                landedAt[0] = i;
            }
            return landedAt[0] >= 0 && i - landedAt[0] >= 12;
        });
        int air = -1;
        for (int row = 0; row < t.size(); row++) {
            if (!t.b(row, "on_ground")) {
                air = row;
                break;
            }
        }
        int land = air < 0 ? -1 : firstGrounded(t, air);
        if (land < 0) {
            checks.check("jump", "stumble: landed", "a landing", "none", false, t.name() + ".csv");
            return;
        }
        checks.check("jump", "stumble: precondition, sprinting before the landing", "test.sprinting true",
                t.s(land - 1, "test.sprinting"), t.b(land - 1, "test.sprinting"), t.ref(land - 1));
        int stumbling = 0;
        int sprintingRows = 0;
        for (int row = land; row <= land + 7 && row < t.size(); row++) {
            if ("stumble".equals(t.s(row, "move.phase"))) {
                stumbling++;
            }
        }
        for (int row = land + 1; row <= land + 6 && row < t.size(); row++) {
            if (t.b(row, "test.sprinting")) {
                sprintingRows++;
            }
        }
        checks.equal("jump", "stumble: J5 six stumble ticks (move.phase)", 6, stumbling, t.ref(land) + " to "
                + t.ref(land + 7));
        checks.equal("jump", "stumble: J5 sprint off on the six ticks after landing (rows with test.sprinting)", 0,
                sprintingRows, t.ref(land + 1) + " to " + t.ref(land + 6));
        checks.near("jump", "stumble: J5 no landing shake (max camera.shake)", 0.0,
                maxAbs(t, 0, t.size() - 1, "camera.shake"), 1.0e-6, t.name() + ".csv");
        // Vanilla walking speed on stone from the reference integrator: 0.1 * 0.98 * 0.216 / 0.6^3 / (1 - 0.6 * 0.91).
        double walk = 0.1 * 0.98 * (0.21600002 / (0.6 * 0.6 * 0.6)) / (1.0 - 0.6 * 0.91);
        int first = -1;
        double fastest = 0.0;
        int fastestRow = -1;
        for (int row = land; row <= land + 7 && row < t.size(); row++) {
            if ("stumble".equals(t.s(row, "move.phase"))) {
                if (first < 0) {
                    first = row;
                }
                // A row's phase and its displacement come from the same tick (engine step, then travel).
                double s = step(t, row);
                if (s > fastest) {
                    fastest = s;
                    fastestRow = row;
                }
            }
        }
        if (first >= 0) {
            checks.near("jump", "stumble: J5 the first stumble tick moves at exactly walking speed "
                    + FeelChecks.fmt(walk) + " (displacement over the first stumble row)", walk,
                    step(t, first), 0.005, t.ref(first));
            checks.check("jump", "stumble: J5 never faster than walking speed on the six stumble ticks (max "
                    + "displacement)", "<= " + FeelChecks.fmt(walk + 0.005), FeelChecks.fmt(fastest),
                    fastest <= walk + 0.005, fastestRow < 0 ? t.name() + ".csv" : t.ref(fastestRow));
        }
        checks.note("jump", "stumble: speed and sprint after the stumble", "sprint key still held",
                "displacement over L+9 " + FeelChecks.fmt(step(t, land + 9)) + ", sprinting at L+8 "
                        + t.s(Math.min(t.size() - 1, land + 8), "test.sprinting"), t.ref(land + 9));
    }

    /** RO1: a sideways or backward roll; the camera never turns yaw. */
    private void rollAxis(ClientGameTestContext context, TestSingleplayerContext world, String name, String key,
                          boolean side) {
        tp(context, world, course.station("drop_13"));
        int[] phase = {0};
        int[] landedAt = {-1};
        boolean[] shot = {false};
        float before = health.now(world);
        TelemetryHandle server = health.start(world);
        FeelRecorder.Table t = record(context, name, key + "*60", 120, (i, live) -> {
            if (phase[0] == 0 && !live.onGround()) {
                phase[0] = 1;
                script(context, "-*100");
            } else if (phase[0] == 1 && live.vy() < 0.0 && live.y() - floorY() < 3.5) {
                phase[0] = 2;
                script(context, "N*3 -*60");
            } else if (phase[0] == 2 && live.onGround() && landedAt[0] < 0) {
                landedAt[0] = i;
            }
            if (side && !shot[0] && live.move().equals("roll") && live.moveTick() == 3) {
                shot[0] = true;
                shot(context, "roll_side_T3");
            }
            return landedAt[0] >= 0 && i - landedAt[0] >= 30;
        });
        float lowest = health.stop(world, server, out.resolve(name + ".server.csv"));
        String label = side ? "sideways roll" : "backward roll";
        int t0 = t.started("roll", 0);
        checks.check("roll", label + ": the roll starts", "roll", t0 < 0 ? "no roll" : "roll", t0 >= 0,
                t0 < 0 ? t.name() + ".csv" : t.ref(t0));
        checks.near("roll", label + ": no damage (server health, lowest)", before, lowest, 1.0e-3,
                name + ".server.csv health");
        if (t0 < 1) {
            return;
        }
        double yaw0 = t.d(t0 - 1, "camera.yaw");
        double yawDrift = 0.0;
        for (int row = t0; row <= t0 + 12 && row < t.size(); row++) {
            yawDrift = Math.max(yawDrift, Math.abs(t.d(row, "camera.yaw") - yaw0));
        }
        checks.near("roll", label + ": RO1 no yaw (max |camera.yaw - yaw before| T0..T12)", 0.0, yawDrift, 1.0e-4,
                t.ref(t0) + " to " + t.ref(t0 + 12));
        if (side) {
            double roll = maxAbs(t, t0, t0 + 12, "camera.roll");
            double pitch = maxOf(t, t0, t0 + 12, "camera.pitch_add");
            checks.near("roll", "sideways roll: RO1 8 deg roll toward the travel side (max |camera.roll|)", 8.0, roll,
                    0.1, t.ref(t0) + " to " + t.ref(t0 + 12));
            checks.near("roll", "sideways roll: RO1 the 25 deg dip, no flip (max camera.pitch_add)", 25.0, pitch, 0.5,
                    t.ref(t0) + " to " + t.ref(t0 + 12));
            checks.check("roll", "sideways roll: screenshot at T3", "taken", shot[0] ? "taken" : "missed", shot[0],
                    t.ref(t0 + 3));
        } else {
            checks.near("roll", "backward roll: RO1 -180 deg of added pitch at T5", -180.0,
                    t.d(t0 + 5, "camera.pitch_add"), 1.0, t.ref(t0 + 5));
            double pitch = t.d(t0 + 10, "camera.pitch_add");
            checks.near("roll", "backward roll: pitch back by T10 (mod 360; raw " + FeelChecks.fmt(pitch) + ")", 0.0,
                    Math.IEEEremainder(pitch, 360.0), 0.5, t.ref(t0 + 10));
            checks.near("roll", "backward roll: no roll channel (max |camera.roll|)", 0.0,
                    maxAbs(t, t0, t0 + 12, "camera.roll"), 1.0e-6, t.ref(t0) + " to " + t.ref(t0 + 12));
        }
    }

    /**
     * W1: a 9-block drop that slides down the single wall lands at a fall distance no larger than the equivalent height
     * of its speed, so it takes no damage; the same drop without pushing into the wall hurts.
     */
    private void wallSlideFall(ClientGameTestContext context, TestSingleplayerContext world) {
        FeelCourse.Station station = course.station("single_wall");
        double face = station.start().getX();
        for (boolean push : new boolean[]{true, false}) {
            airTp(context, world, face + 0.31, station.start().getY() + 9.0, station.start().getZ() + 0.5,
                    station.yaw());
            float before = health.now(world);
            TelemetryHandle server = health.start(world);
            String name = push ? "wall_slide_fall" : "wall_slide_fall_control";
            int[] landedAt = {-1};
            FeelRecorder.Table t = record(context, name, push ? "-*4 F*80" : "-*80", 90, (i, live) -> {
                if (live.onGround() && landedAt[0] < 0) {
                    landedAt[0] = i;
                }
                return landedAt[0] >= 0 && i - landedAt[0] >= 10;
            });
            float lowest = health.stop(world, server, out.resolve(name + ".server.csv"));
            double serverFall = health.maxFall;
            if (push) {
                int slide = t.started("wall_slide", 0);
                int land = firstGrounded(t, 0);
                checks.check("wall_slide", "W1: the drop slides down the wall", "wall_slide starts",
                        slide < 0 ? "no slide" : "slide", slide >= 0, slide < 0 ? t.name() + ".csv" : t.ref(slide));
                checks.near("wall_slide", "W1: a 9-block drop down the wall takes no damage (server health, lowest)",
                        before, lowest, 1.0e-3, name + ".server.csv health");
                if (land < 2) {
                    checks.check("wall_slide", "W1: landed", "a landing", "none", false, t.name() + ".csv");
                    continue;
                }
                double clientFall = maxOf(t, 0, land - 1, "body.fall_distance");
                checks.near("wall_slide", "W1: client and server agree on the capped fall distance at landing (max "
                        + "client body.fall_distance before landing " + FeelChecks.fmt(clientFall)
                        + " vs max server fall_distance)", serverFall, clientFall, 0.05, t.ref(land - 1) + " / "
                        + name + ".server.csv fall_distance");
                // The landing dip reads the engine's landing fall: the fall distance at the start of the last air
                // tick (the capped value recorded on the row before landing) plus that tick's drop.
                double landingFall = t.d(land - 1, "body.fall_distance")
                        + Math.max(0.0, t.d(land - 1, "pos_y") - t.d(land, "pos_y"));
                double expected = landingFall <= 1.3 ? 0.0 : Math.min(0.25, 0.03 + 0.045 * (landingFall - 1.3));
                checks.near("wall_slide", "W1/J4: landing dip reads the capped landing fall "
                        + FeelChecks.fmt(landingFall) + " (min camera.eye_height after landing)", -expected,
                        minOf(t, land, t.size() - 1, "camera.eye_height"), 0.003, t.ref(land) + " onward");
            } else {
                checks.check("wall_slide", "W1 control: the same drop without pushing into the wall hurts",
                        "health drops", FeelChecks.fmt(before) + " -> lowest " + FeelChecks.fmt(lowest),
                        lowest < before, name + ".server.csv health");
            }
        }
    }

    /** The server player's health: a column on a server telemetry recording, and a direct read. */
    private static final class ServerHealth {

        private boolean registered;
        private MemoryTelemetrySink sink;
        /** The largest server fall distance the last stopped recording saw. */
        double maxFall = Double.NaN;

        void register(TestSingleplayerContext world) {
            world.getServer().runOnServer(server -> {
                if (!registered) {
                    registered = true;
                    FabricServerTelemetry.registry().registerDouble("health", ServerPlayer::getHealth);
                    FabricServerTelemetry.registry().registerDouble("fall_distance", player -> player.fallDistance);
                }
            });
        }

        TelemetryHandle start(TestSingleplayerContext world) {
            sink = new MemoryTelemetrySink();
            MemoryTelemetrySink target = sink;
            return world.getServer().computeOnServer(server ->
                    FabricServerTelemetry.start(server.getPlayerList().getPlayers().getFirst(), target));
        }

        float now(TestSingleplayerContext world) {
            return world.getServer().computeOnServer(server ->
                    server.getPlayerList().getPlayers().getFirst().getHealth());
        }

        /** Stops the recording, writes it and returns the lowest health it saw. */
        float stop(TestSingleplayerContext world, TelemetryHandle handle, Path csv) {
            world.getServer().runOnServer(server -> handle.close());
            float lowest = Float.NaN;
            int column = sink.columns().indexOf("health");
            int fallColumn = sink.columns().indexOf("fall_distance");
            maxFall = Double.NaN;
            for (MemoryTelemetrySink.Row row : sink.rows()) {
                if (column >= 0 && row.values()[column] instanceof Number number) {
                    lowest = Float.isNaN(lowest) ? number.floatValue() : Math.min(lowest, number.floatValue());
                }
                if (fallColumn >= 0 && row.values()[fallColumn] instanceof Number number) {
                    maxFall = Double.isNaN(maxFall) ? number.doubleValue() : Math.max(maxFall, number.doubleValue());
                }
            }
            try {
                Files.createDirectories(csv.getParent());
                try (Writer writer = Files.newBufferedWriter(csv)) {
                    sink.writeCsv(writer);
                }
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return lowest;
        }
    }
}
