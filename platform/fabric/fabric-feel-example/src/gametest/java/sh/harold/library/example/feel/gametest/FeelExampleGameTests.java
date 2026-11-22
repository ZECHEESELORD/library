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
    private static final List<String> LAYER_COLUMNS = List.of("camera.pitch_add", "camera.roll", "camera.fov_add",
            "camera.eye_height", "camera.shake");

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
                scenario("mantle", "vault", () -> vault(context, world));
                scenario("mantle", "ledge_2 standing tap", () -> ledgeTwoStanding(context, world));
                scenario("mantle", "climb", () -> climb(context, world));
                scenario("mantle", "no headroom", () -> noHeadroom(context, world));
                scenario("roll", "roll", () -> roll(context, world, "roll_full", IntensityProfile.FULL));
                scenario("roll", "sneak held", () -> rollHeldSneak(context, world));
                scenario("roll", "reduced", () -> roll(context, world, "roll_reduced", IntensityProfile.REDUCED));
                scenario("slide", "slide", () -> slide(context, world));
                scenario("wall_jump", "single wall", () -> singleWall(context, world));
                scenario("wall_jump", "single wall from a drop", () -> singleWallDrop(context, world));
                scenario("wall_jump", "shaft_3 chain", () -> shaftChain(context, world));
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
            return new Live(player.getY(), player.getDeltaMovement().y, player.onGround(),
                    status.move().map(Key::value).orElse(""), status.moveTick(), started,
                    script == null ? 0 : script.remaining());
        });
    }

    private record Live(double y, double vy, boolean onGround, String move, long moveTick, List<String> started,
                        int remaining) {
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
        checks.check("jump", "boost tap: no camera change", "every camera layer 0",
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

    /**
     * A vault needs the ledge top 0.9 to 1.1 above the feet when the ledge first comes within reach. Standing at the
     * wall, a jump meets the 1.6 limit first (a climb), so the vault is approached with a sprint jump from a range of
     * start gaps, keeping the first that vaults; every attempt's CSV is kept.
     */
    private void vault(ClientGameTestContext context, TestSingleplayerContext world) {
        FeelCourse.Station station = course.station("ledge_2");
        double wallZ = origin.getZ() + 3.0;
        FeelRecorder.Table found = null;
        int t0 = -1;
        List<String> tried = new ArrayList<>();
        for (int step = 0; step <= 8 && found == null; step++) {
            double gap = 0.8 + step * 0.1;
            tp(context, world, station.start().getX() + 0.5, station.start().getY(), wallZ - 0.3 - gap, 0.0f);
            String name = String.format(Locale.ROOT, "mantle_vault_gap%.1f", gap);
            FeelRecorder.Table t = record(context, name, "FSJ FS*25 -*10", 36, (i, live) -> false);
            int start = t.started("mantle", 0);
            String kind = start < 0 ? "none" : t.s(start, "move.phase");
            tried.add(String.format(Locale.ROOT, "%.1f=%s", gap, kind));
            if (start >= 0 && kind.startsWith("vault")) {
                found = t;
                t0 = start;
            }
        }
        checks.check("mantle", "vault reachable at ledge_2 (sprint jump, start gap 0.8..1.6)", "a vault",
                (found == null ? "no vault: " : "vault: ") + String.join(" ", tried), found != null,
                found == null ? "mantle_vault_gap*.csv" : found.ref(t0));
        if (found == null) {
            return;
        }
        FeelRecorder.Table t = found;
        int mantleRows = 0;
        for (int row = t0; row < t.size() && "movement:mantle".equals(t.s(row, "move.id")); row++) {
            mantleRows++;
        }
        checks.equal("mantle", "vault: move runs T0..T4 (rows with move.id mantle)", 5, mantleRows, t.ref(t0));
        int t5 = t0 + 5;
        boolean ended = t.s(t5, "test.ended").contains("mantle:completed")
                && !"movement:mantle".equals(t.s(t5, "move.id"));
        checks.check("mantle", "vault: full control at T5 (move completes)", "ended mantle:completed at T5",
                "move.id='" + t.s(t5, "move.id") + "' ended='" + t.s(t5, "test.ended") + "'", ended, t.ref(t5));
        double speed = Math.hypot(t.d(t5, "vel_x"), t.d(t5, "vel_z"));
        checks.check("mantle", "vault: still moving at T5", "horizontal speed > 0", FeelChecks.fmt(speed),
                speed > 1.0e-3, t.ref(t5));
    }

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
            if (!shot[0] && live.move().equals("mantle") && live.moveTick() == 4) {
                shot[0] = true;
                shot(context, "mantle_climb_T4");
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
        checks.check("mantle", "climb: screenshot at T4", "taken", shot[0] ? "taken" : "missed", shot[0],
                t.ref(t0 + 4));
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
        FeelRecorder.Table tap = record(context, "mantle_no_headroom_tap", "F*15 FJ F*20", 36, (i, live) -> false);
        start = tap.started("mantle", 0);
        checks.check("mantle", "ledge_2_no_headroom refuses (tap jump)", "no mantle",
                start < 0 ? "no mantle" : "mantle " + tap.s(start, "move.phase"), start < 0,
                start < 0 ? tap.name() + ".csv all rows" : tap.ref(start));
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
        checks.near("slide", "4.2 blocks in 16 ticks (start of T0 to end of T15)", 4.2, distance, 0.05,
                t.ref(t0 - 1) + " to tick=" + t.tick(t15));
        checks.near("slide", "eye height 0.5 at T3 (drawn camera_eye_height)", 0.5, t.d(t0 + 3, "camera_eye_height"),
                0.02, t.ref(t0 + 3));
        for (int k = 0; k <= 3; k++) {
            checks.near("slide", "hitbox 0.6 at T" + k, 0.6, t.d(t0 + k, "body.height"), 1.0e-6, t.ref(t0 + k));
        }
        // T0 moves at the start speed, so the speed after 16 decays is the displacement over T16.
        double speed15 = step(t, t15);
        double speed16 = step(t, t15 + 1);
        checks.near("slide", "speed after 16 ticks of decay = displacement over T16 (02: ends at 0.18 b/t; over T15 "
                + FeelChecks.fmt(speed15) + ")", 0.18, speed16, 0.01, t.ref(t15 + 1));
        int end = t0;
        while (end < t.size() && "movement:slide".equals(t.s(end, "move.id"))
                && List.of("drop", "slide").contains(t.s(end, "move.phase"))) {
            end++;
        }
        checks.note("slide", "length of the sliding phases (drop + slide) on flat ground",
                "02 reads as a 16-tick slide", "T0..T" + (end - t0 - 1) + " (" + (end - t0) + " ticks), then phase '"
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

    /** The server player's health: a column on a server telemetry recording, and a direct read. */
    private static final class ServerHealth {

        private boolean registered;
        private MemoryTelemetrySink sink;

        void register(TestSingleplayerContext world) {
            world.getServer().runOnServer(server -> {
                if (!registered) {
                    registered = true;
                    FabricServerTelemetry.registry().registerDouble("health", ServerPlayer::getHealth);
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
            for (MemoryTelemetrySink.Row row : sink.rows()) {
                if (column >= 0 && row.values()[column] instanceof Number number) {
                    lowest = Float.isNaN(lowest) ? number.floatValue() : Math.min(lowest, number.floatValue());
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
