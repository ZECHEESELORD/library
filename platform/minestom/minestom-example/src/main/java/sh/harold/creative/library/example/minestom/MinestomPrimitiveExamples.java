package sh.harold.creative.library.example.minestom;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.Scheduler;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import sh.harold.creative.library.ambient.AmbientBlendMode;
import sh.harold.creative.library.ambient.AmbientProfile;
import sh.harold.creative.library.ambient.AmbientSnapshot;
import sh.harold.creative.library.ambient.AmbientWeightModel;
import sh.harold.creative.library.ambient.ViewerAmbientState;
import sh.harold.creative.library.ambient.WeightCurve;
import sh.harold.creative.library.ambient.ZoneSpec;
import sh.harold.creative.library.ambient.minestom.MinestomAmbientZonePlatform;
import sh.harold.creative.library.camera.BlendMode;
import sh.harold.creative.library.camera.CameraMotions;
import sh.harold.creative.library.camera.EaseOutCurve;
import sh.harold.creative.library.camera.Waveform;
import sh.harold.creative.library.camera.minestom.MinestomCameraMotionPlatform;
import sh.harold.creative.library.curve.CatmullRomMode;
import sh.harold.creative.library.curve.CurvePath;
import sh.harold.creative.library.curve.CurvePathSpec;
import sh.harold.creative.library.curve.CurveSegmentSpec;
import sh.harold.creative.library.curve.core.CurvePaths;
import sh.harold.creative.library.impulse.AxisMask;
import sh.harold.creative.library.impulse.ImpulseMode;
import sh.harold.creative.library.impulse.ImpulseSpec;
import sh.harold.creative.library.impulse.ImpulseStackMode;
import sh.harold.creative.library.impulse.ImpulseVector;
import sh.harold.creative.library.impulse.minestom.MinestomImpulsePlatform;
import sh.harold.creative.library.message.Message;
import sh.harold.creative.library.overlay.OverlayConflictPolicy;
import sh.harold.creative.library.overlay.ScreenOverlay;
import sh.harold.creative.library.overlay.ScreenOverlayRequest;
import sh.harold.creative.library.overlay.minestom.MinestomScreenOverlayPlatform;
import sh.harold.creative.library.sound.SoundCue;
import sh.harold.creative.library.sound.SoundCueKeys;
import sh.harold.creative.library.sound.minestom.MinestomSoundCuePlatform;
import sh.harold.creative.library.spatial.AnchorRef;
import sh.harold.creative.library.spatial.AnchorSnapshot;
import sh.harold.creative.library.spatial.Angle;
import sh.harold.creative.library.spatial.Frame3;
import sh.harold.creative.library.spatial.Segment3;
import sh.harold.creative.library.spatial.SpaceId;
import sh.harold.creative.library.spatial.Vec3;
import sh.harold.creative.library.spatial.Volume;
import sh.harold.creative.library.spatial.core.CapsuleVolume;
import sh.harold.creative.library.spatial.core.OrientedBoxVolume;
import sh.harold.creative.library.spatial.core.SphereVolume;
import sh.harold.creative.library.telegraph.TelegraphFrame;
import sh.harold.creative.library.telegraph.TelegraphShape;
import sh.harold.creative.library.telegraph.TelegraphSpec;
import sh.harold.creative.library.telegraph.TelegraphTiming;
import sh.harold.creative.library.telegraph.ViewerRelation;
import sh.harold.creative.library.telegraph.ViewerScope;
import sh.harold.creative.library.telegraph.minestom.MinestomTelegraphPlatform;
import sh.harold.creative.library.tick.InstanceConflictPolicy;
import sh.harold.creative.library.tick.KeyedHandle;
import sh.harold.creative.library.trajectory.CollisionHit;
import sh.harold.creative.library.trajectory.CollisionQuery;
import sh.harold.creative.library.trajectory.CollisionResponseMode;
import sh.harold.creative.library.trajectory.PreviewRecomputePolicy;
import sh.harold.creative.library.trajectory.PreviewScope;
import sh.harold.creative.library.trajectory.TrajectoryMotion;
import sh.harold.creative.library.trajectory.TrajectoryPreviewSnapshot;
import sh.harold.creative.library.trajectory.TrajectoryPreviewSpec;
import sh.harold.creative.library.trajectory.core.StandardTrajectoryPreviewController;
import sh.harold.creative.library.trajectory.minestom.MinestomTrajectoryPreviewPlatform;
import sh.harold.creative.library.tween.Easing;
import sh.harold.creative.library.tween.Envelope;
import sh.harold.creative.library.tween.HoldBehavior;
import sh.harold.creative.library.tween.Interpolators;
import sh.harold.creative.library.tween.RepeatMode;
import sh.harold.creative.library.tween.RepeatSpec;
import sh.harold.creative.library.tween.Tween;
import sh.harold.creative.library.tween.TweenHandle;
import sh.harold.creative.library.tween.TweenSample;
import sh.harold.creative.library.tween.core.StandardTweenController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

final class MinestomPrimitiveExamples implements AutoCloseable {

    private static final SpaceId INSTANCE_SPACE = SpaceId.of("example", "minestom-harness");
    private static final long DRAW_TICKS = 70L;
    private static final long SEQUENCE_GAP_TICKS = 26L;
    private static final double DRAW_STEP = 0.45;
    private static final int COLOR_TWEEN = 0x55D6FF;
    private static final int COLOR_CURVE = 0x7EF29A;
    private static final int COLOR_CURVE_ALT = 0xFFD166;
    private static final int COLOR_TRAJECTORY = 0xF6BD60;
    private static final int COLOR_IMPACT = 0xFF6B6B;
    private static final int COLOR_TELEGRAPH = 0xFF7272;
    private static final int COLOR_TELEGRAPH_ALT = 0xC7A6FF;
    private static final int COLOR_AMBIENT = 0x6EC6FF;

    private final Scheduler scheduler;
    private final MinestomSoundCuePlatform sounds;
    private final MinestomScreenOverlayPlatform overlays;
    private final MinestomCameraMotionPlatform camera;
    private final MinestomDevHarnessMessages feedback;
    private final MinestomPrimitiveDebugRenderer debug = new MinestomPrimitiveDebugRenderer();
    private final MinestomTrajectoryPreviewPlatform trajectories = new MinestomTrajectoryPreviewPlatform();
    private final MinestomImpulsePlatform impulses;
    private final Map<UUID, Session> sessions = new LinkedHashMap<>();

    MinestomPrimitiveExamples(
            Scheduler scheduler,
            MinestomSoundCuePlatform sounds,
            MinestomScreenOverlayPlatform overlays,
            MinestomCameraMotionPlatform camera,
            MinestomDevHarnessMessages feedback
    ) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.overlays = Objects.requireNonNull(overlays, "overlays");
        this.camera = Objects.requireNonNull(camera, "camera");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.impulses = new MinestomImpulsePlatform(scheduler, id -> MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(id));
    }

    String tweenUsage() {
        return "/testtweens all|easings|envelopes|vectors|angles|delay|zero|repeat|pause|replace|refresh|clear|help";
    }

    String curveUsage() {
        return "/testcurves all|line|quadratic|cubic|catmull|arc|split|trim|reverse|resample|clear|help";
    }

    String telegraphUsage() {
        return "/testtelegraphs all|circle|ring|rectangle|line|corridor|cone|arc|path|refresh|replace|missinganchor|scopes|clear|help";
    }

    String trajectoryUsage() {
        return "/testtrajectories all|ballistic|responses|oneshot|everytick|threshold|refresh|replace|scopes|clear|help";
    }

    String impulseUsage() {
        return "/testimpulses all|add|set|clamped|dash|pull|push|launch|local|masks|stack|refresh|replace|clear|help";
    }

    String ambientUsage() {
        return "/testambient all|channels|hardedge|feather|weights|blend|priority|ttl|refresh|clear|help";
    }

    String primitivesUsage() {
        return "/testprimitives all|pathwarning|previewimpact|dashassist|zonepulse|clear|help";
    }

    void playTween(Player player, String variant) {
        if ("clear".equals(variant)) {
            clear(player);
            feedback.success(player, "Cleared active tween harness state.");
            return;
        }
        Session session = restart(player);
        feedback.info(player, "Running tween demo {variant}.", Message.slot("variant", variant));
        switch (variant) {
            case "all" -> {
                sequence(session, "easings", 0L, this::runTweenEasings);
                sequence(session, "envelopes", SEQUENCE_GAP_TICKS, this::runTweenEnvelopes);
                sequence(session, "delay", SEQUENCE_GAP_TICKS * 2L, this::runTweenDelay);
                sequence(session, "repeat", SEQUENCE_GAP_TICKS * 3L, this::runTweenRepeat);
                sequence(session, "refresh", SEQUENCE_GAP_TICKS * 4L, this::runTweenRefresh);
            }
            case "easings" -> runTweenEasings(session, player);
            case "envelopes" -> runTweenEnvelopes(session, player);
            case "vectors" -> runTweenVectors(session, player);
            case "angles" -> runTweenAngles(session, player);
            case "delay" -> runTweenDelay(session, player);
            case "zero" -> runTweenZero(session, player);
            case "repeat" -> runTweenRepeat(session, player);
            case "pause" -> runTweenPause(session, player);
            case "replace" -> runTweenReplace(session, player);
            case "refresh" -> runTweenRefresh(session, player);
            default -> feedback.error(player, "Unhandled tween variant {variant}.", Message.slot("variant", variant));
        }
    }

    void playCurve(Player player, String variant) {
        if ("clear".equals(variant)) {
            clear(player);
            feedback.success(player, "Cleared active curve harness state.");
            return;
        }
        Session session = restart(player);
        feedback.info(player, "Running curve demo {variant}.", Message.slot("variant", variant));
        switch (variant) {
            case "all" -> {
                sequence(session, "line", 0L, this::runCurveLine);
                sequence(session, "quadratic", SEQUENCE_GAP_TICKS, this::runCurveQuadratic);
                sequence(session, "cubic", SEQUENCE_GAP_TICKS * 2L, this::runCurveCubic);
                sequence(session, "catmull", SEQUENCE_GAP_TICKS * 3L, this::runCurveCatmull);
                sequence(session, "arc", SEQUENCE_GAP_TICKS * 4L, this::runCurveArc);
            }
            case "line" -> runCurveLine(session, player);
            case "quadratic" -> runCurveQuadratic(session, player);
            case "cubic" -> runCurveCubic(session, player);
            case "catmull" -> runCurveCatmull(session, player);
            case "arc" -> runCurveArc(session, player);
            case "split" -> runCurveSplit(session, player);
            case "trim" -> runCurveTrim(session, player);
            case "reverse" -> runCurveReverse(session, player);
            case "resample" -> runCurveResample(session, player);
            default -> feedback.error(player, "Unhandled curve variant {variant}.", Message.slot("variant", variant));
        }
    }

    void playTelegraph(Player player, String variant) {
        if ("clear".equals(variant)) {
            clear(player);
            feedback.success(player, "Cleared active telegraph harness state.");
            return;
        }
        Session session = restart(player);
        feedback.info(player, "Running telegraph demo {variant}.", Message.slot("variant", variant));
        switch (variant) {
            case "all" -> {
                sequence(session, "circle", 0L, this::runTelegraphCircle);
                sequence(session, "corridor", SEQUENCE_GAP_TICKS, this::runTelegraphCorridor);
                sequence(session, "cone", SEQUENCE_GAP_TICKS * 2L, this::runTelegraphCone);
                sequence(session, "path", SEQUENCE_GAP_TICKS * 3L, this::runTelegraphPath);
                sequence(session, "refresh", SEQUENCE_GAP_TICKS * 4L, this::runTelegraphRefresh);
            }
            case "circle" -> runTelegraphCircle(session, player);
            case "ring" -> runTelegraphRing(session, player);
            case "rectangle" -> runTelegraphRectangle(session, player);
            case "line" -> runTelegraphLine(session, player);
            case "corridor" -> runTelegraphCorridor(session, player);
            case "cone" -> runTelegraphCone(session, player);
            case "arc" -> runTelegraphArc(session, player);
            case "path" -> runTelegraphPath(session, player);
            case "refresh" -> runTelegraphRefresh(session, player);
            case "replace" -> runTelegraphReplace(session, player);
            case "missinganchor" -> runTelegraphMissingAnchor(session, player);
            case "scopes" -> runTelegraphScopes(session, player);
            default -> feedback.error(player, "Unhandled telegraph variant {variant}.", Message.slot("variant", variant));
        }
    }

    void playTrajectory(Player player, String variant) {
        if ("clear".equals(variant)) {
            clear(player);
            feedback.success(player, "Cleared active trajectory harness state.");
            return;
        }
        Session session = restart(player);
        feedback.info(player, "Running trajectory demo {variant}.", Message.slot("variant", variant));
        switch (variant) {
            case "all" -> {
                sequence(session, "ballistic", 0L, this::runTrajectoryBallistic);
                sequence(session, "oneshot", SEQUENCE_GAP_TICKS, this::runTrajectoryOneShot);
                sequence(session, "everytick", SEQUENCE_GAP_TICKS * 2L, this::runTrajectoryEveryTick);
                sequence(session, "threshold", SEQUENCE_GAP_TICKS * 3L, this::runTrajectoryThreshold);
                sequence(session, "refresh", SEQUENCE_GAP_TICKS * 4L, this::runTrajectoryRefresh);
            }
            case "ballistic" -> runTrajectoryBallistic(session, player);
            case "responses" -> runTrajectoryResponses(session, player);
            case "oneshot" -> runTrajectoryOneShot(session, player);
            case "everytick" -> runTrajectoryEveryTick(session, player);
            case "threshold" -> runTrajectoryThreshold(session, player);
            case "refresh" -> runTrajectoryRefresh(session, player);
            case "replace" -> runTrajectoryReplace(session, player);
            case "scopes" -> runTrajectoryScopes(session, player);
            default -> feedback.error(player, "Unhandled trajectory variant {variant}.", Message.slot("variant", variant));
        }
    }

    void playImpulse(Player player, String variant) {
        if ("clear".equals(variant)) {
            clear(player);
            feedback.success(player, "Cleared active impulse harness state.");
            return;
        }
        Session session = restart(player);
        feedback.info(player, "Running impulse demo {variant}.", Message.slot("variant", variant));
        switch (variant) {
            case "all" -> {
                sequence(session, "add", 0L, this::runImpulseAdd);
                sequence(session, "dash", SEQUENCE_GAP_TICKS, this::runImpulseDash);
                sequence(session, "launch", SEQUENCE_GAP_TICKS * 2L, this::runImpulseLaunch);
                sequence(session, "stack", SEQUENCE_GAP_TICKS * 3L, this::runImpulseStack);
                sequence(session, "refresh", SEQUENCE_GAP_TICKS * 4L, this::runImpulseRefresh);
            }
            case "add" -> runImpulseAdd(session, player);
            case "set" -> runImpulseSet(session, player);
            case "clamped" -> runImpulseClamped(session, player);
            case "dash" -> runImpulseDash(session, player);
            case "pull" -> runImpulsePull(session, player);
            case "push" -> runImpulsePush(session, player);
            case "launch" -> runImpulseLaunch(session, player);
            case "local" -> runImpulseLocal(session, player);
            case "masks" -> runImpulseMasks(session, player);
            case "stack" -> runImpulseStack(session, player);
            case "refresh" -> runImpulseRefresh(session, player);
            case "replace" -> runImpulseReplace(session, player);
            default -> feedback.error(player, "Unhandled impulse variant {variant}.", Message.slot("variant", variant));
        }
    }

    void playAmbient(Player player, String variant) {
        if ("clear".equals(variant)) {
            clear(player);
            feedback.success(player, "Cleared active ambient harness state.");
            return;
        }
        Session session = restart(player);
        feedback.info(player, "Running ambient zone demo {variant}.", Message.slot("variant", variant));
        switch (variant) {
            case "all" -> {
                sequence(session, "channels", 0L, this::runAmbientChannels);
                sequence(session, "feather", SEQUENCE_GAP_TICKS, this::runAmbientFeather);
                sequence(session, "blend", SEQUENCE_GAP_TICKS * 2L, this::runAmbientBlend);
                sequence(session, "priority", SEQUENCE_GAP_TICKS * 3L, this::runAmbientPriority);
                sequence(session, "refresh", SEQUENCE_GAP_TICKS * 4L, this::runAmbientRefresh);
            }
            case "channels" -> runAmbientChannels(session, player);
            case "hardedge" -> runAmbientHardEdge(session, player);
            case "feather" -> runAmbientFeather(session, player);
            case "weights" -> runAmbientWeights(session, player);
            case "blend" -> runAmbientBlend(session, player);
            case "priority" -> runAmbientPriority(session, player);
            case "ttl" -> runAmbientTtl(session, player);
            case "refresh" -> runAmbientRefresh(session, player);
            default -> feedback.error(player, "Unhandled ambient variant {variant}.", Message.slot("variant", variant));
        }
    }

    void playPrimitives(Player player, String variant) {
        if ("clear".equals(variant)) {
            clear(player);
            feedback.success(player, "Cleared active primitive composition harness state.");
            return;
        }
        Session session = restart(player);
        feedback.info(player, "Running primitive composition demo {variant}.", Message.slot("variant", variant));
        switch (variant) {
            case "all" -> {
                sequence(session, "pathwarning", 0L, this::runPathWarning);
                sequence(session, "previewimpact", SEQUENCE_GAP_TICKS * 2L, this::runPreviewImpact);
                sequence(session, "dashassist", SEQUENCE_GAP_TICKS * 4L, this::runDashAssist);
                sequence(session, "zonepulse", SEQUENCE_GAP_TICKS * 6L, this::runZonePulse);
            }
            case "pathwarning" -> runPathWarning(session, player);
            case "previewimpact" -> runPreviewImpact(session, player);
            case "dashassist" -> runDashAssist(session, player);
            case "zonepulse" -> runZonePulse(session, player);
            default -> feedback.error(player, "Unhandled primitive composition variant {variant}.", Message.slot("variant", variant));
        }
    }

    void clear(Player player) {
        discard(player.getUuid());
    }

    void discard(UUID playerId) {
        Session session = sessions.remove(playerId);
        if (session != null) {
            session.close();
        }
        camera.stopAll(playerId);
        impulses.stopAll(playerId);
        Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerId);
        if (player != null) {
            overlays.clearAll(player);
        }
    }

    @Override
    public void close() {
        List<Session> current = new ArrayList<>(sessions.values());
        sessions.clear();
        current.forEach(Session::close);
        impulses.close();
    }

    private Session restart(Player player) {
        discard(player.getUuid());
        Session session = new Session(player);
        sessions.put(player.getUuid(), session);
        return session;
    }

    private void sequence(Session session, String label, long delay, Scenario scenario) {
        session.schedule(delay, () -> {
            Player player = session.owner();
            if (player != null) {
                feedback.info(player, "Switching to {label}.", Message.slot("label", label));
                scenario.run(session, player);
            }
        });
    }

    private void runTweenEasings(Session session, Player player) {
        Frame3 frame = demoFrame(player, 4.0, 1.3);
        renderTween(session, frame, vecTween(
                session.key("tween/easings"),
                frame.localToWorldPoint(new Vec3(-3.0, 0.0, 0.0)),
                frame.localToWorldPoint(new Vec3(3.0, 0.0, 0.0)),
                0L,
                12L,
                Easing.BOUNCE,
                Envelope.constant(1.0),
                HoldBehavior.SUPPRESS,
                RepeatSpec.none(),
                InstanceConflictPolicy.REPLACE
        ), COLOR_TWEEN, true, 1.0f, null);
    }

    private void runTweenEnvelopes(Session session, Player player) {
        Frame3 frame = demoFrame(player, 4.0, 1.3);
        renderTween(session, frame, vecTween(
                session.key("tween/envelopes"),
                frame.localToWorldPoint(new Vec3(-2.5, 0.0, 0.0)),
                frame.localToWorldPoint(new Vec3(2.5, 0.0, 0.0)),
                0L,
                16L,
                Easing.SMOOTHSTEP,
                new Envelope.AttackHoldRelease(3L, 5L, 6L, 1.0),
                HoldBehavior.SUPPRESS,
                RepeatSpec.none(),
                InstanceConflictPolicy.REPLACE
        ), COLOR_TWEEN, true, 1.2f, sample -> 0.4f + (float) sample.strength());
    }

    private void runTweenVectors(Session session, Player player) {
        Frame3 frame = demoFrame(player, 4.0, 1.3);
        renderTween(session, frame, vecTween(
                session.key("tween/vectors"),
                frame.localToWorldPoint(new Vec3(-2.0, -0.5, 0.0)),
                frame.localToWorldPoint(new Vec3(2.0, 1.0, 0.0)),
                0L,
                14L,
                Easing.QUAD_IN_OUT,
                Envelope.constant(1.0),
                HoldBehavior.SUPPRESS,
                RepeatSpec.none(),
                InstanceConflictPolicy.REPLACE
        ), COLOR_TWEEN, true, 0.9f, null);
    }

    private void runTweenAngles(Session session, Player player) {
        Frame3 frame = demoFrame(player, 4.0, 1.4);
        StandardTweenController<Angle> controller = new StandardTweenController<>();
        controller.start(new Tween<>(
                session.key("tween/angles"),
                Angle.degrees(-90.0),
                Angle.degrees(90.0),
                Interpolators.shortestAngles(),
                0L,
                14L,
                Easing.SMOOTHERSTEP,
                Envelope.constant(1.0),
                HoldBehavior.SUPPRESS,
                RepeatSpec.none(),
                InstanceConflictPolicy.REPLACE
        ));
        session.repeat(0L, 1L, tick -> {
            Player owner = session.owner();
            if (owner == null) {
                controller.close();
                return false;
            }
            Vec3 center = frame.origin();
            debug.polyline(List.of(owner), circle(frame, 2.2, 24), 0.45, 0x355070, 0.4f);
            for (TweenSample<Angle> sample : controller.tick()) {
                double radians = sample.value().radians();
                Vec3 point = center.add(frame.right().multiply(Math.cos(radians) * 2.2))
                        .add(frame.forward().multiply(Math.sin(radians) * 2.2));
                debug.point(List.of(owner), point, COLOR_TWEEN, 0.9f);
            }
            boolean active = controller.hasActiveTweens();
            if (!active) {
                controller.close();
            }
            return active;
        });
    }

    private void runTweenDelay(Session session, Player player) {
        Frame3 frame = demoFrame(player, 4.0, 1.4);
        renderTween(session, frame, vecTween(
                session.key("tween/delay"),
                frame.localToWorldPoint(new Vec3(-2.5, 0.0, 0.0)),
                frame.localToWorldPoint(new Vec3(2.5, 0.0, 0.0)),
                6L,
                12L,
                Easing.LINEAR,
                Envelope.constant(1.0),
                HoldBehavior.EMIT_FROM_VALUE,
                RepeatSpec.none(),
                InstanceConflictPolicy.REPLACE
        ), COLOR_TWEEN, true, 0.9f, null);
    }

    private void runTweenZero(Session session, Player player) {
        Frame3 frame = demoFrame(player, 4.0, 1.4);
        renderTween(session, frame, vecTween(
                session.key("tween/zero"),
                frame.localToWorldPoint(new Vec3(-1.0, 0.0, 0.0)),
                frame.localToWorldPoint(new Vec3(1.5, 0.0, 0.0)),
                0L,
                0L,
                Easing.LINEAR,
                Envelope.constant(1.0),
                HoldBehavior.SUPPRESS,
                RepeatSpec.none(),
                InstanceConflictPolicy.REPLACE
        ), COLOR_TWEEN, true, 1.1f, null);
    }

    private void runTweenRepeat(Session session, Player player) {
        Frame3 frame = demoFrame(player, 4.0, 1.4);
        renderTween(session, frame, vecTween(
                session.key("tween/repeat"),
                frame.localToWorldPoint(new Vec3(-2.0, 0.0, 0.0)),
                frame.localToWorldPoint(new Vec3(2.0, 0.0, 0.0)),
                0L,
                6L,
                Easing.LINEAR,
                Envelope.constant(1.0),
                HoldBehavior.SUPPRESS,
                new RepeatSpec(RepeatMode.PING_PONG, 1),
                InstanceConflictPolicy.REPLACE
        ), COLOR_TWEEN, true, 0.9f, null);
    }

    private void runTweenPause(Session session, Player player) {
        Frame3 frame = demoFrame(player, 4.0, 1.4);
        StandardTweenController<Vec3> controller = new StandardTweenController<>();
        TweenHandle handle = controller.start(vecTween(
                session.key("tween/pause"),
                frame.localToWorldPoint(new Vec3(-2.0, 0.0, 0.0)),
                frame.localToWorldPoint(new Vec3(2.0, 0.0, 0.0)),
                0L,
                12L,
                Easing.LINEAR,
                Envelope.constant(1.0),
                HoldBehavior.SUPPRESS,
                RepeatSpec.none(),
                InstanceConflictPolicy.REPLACE
        ));
        session.schedule(4L, handle::pause);
        session.schedule(10L, handle::resume);
        renderTween(session, frame, controller, COLOR_TWEEN, true, 0.9f, null);
    }

    private void runTweenReplace(Session session, Player player) {
        Frame3 frame = demoFrame(player, 4.0, 1.4);
        StandardTweenController<Vec3> controller = new StandardTweenController<>();
        controller.start(vecTween(
                session.key("tween/same"),
                frame.localToWorldPoint(new Vec3(-2.0, 0.0, 0.0)),
                frame.localToWorldPoint(new Vec3(2.0, 0.0, 0.0)),
                0L,
                12L,
                Easing.LINEAR,
                Envelope.constant(1.0),
                HoldBehavior.SUPPRESS,
                RepeatSpec.none(),
                InstanceConflictPolicy.REPLACE
        ));
        session.schedule(4L, () -> controller.start(vecTween(
                session.key("tween/same"),
                frame.localToWorldPoint(new Vec3(2.0, 0.0, 0.0)),
                frame.localToWorldPoint(new Vec3(0.0, 1.8, 0.0)),
                0L,
                8L,
                Easing.EXPO_OUT,
                Envelope.constant(1.0),
                HoldBehavior.SUPPRESS,
                RepeatSpec.none(),
                InstanceConflictPolicy.REPLACE
        )));
        renderTween(session, frame, controller, COLOR_TWEEN, true, 1.0f, null);
    }

    private void runTweenRefresh(Session session, Player player) {
        Frame3 frame = demoFrame(player, 4.0, 1.4);
        StandardTweenController<Vec3> controller = new StandardTweenController<>();
        controller.start(vecTween(
                session.key("tween/refresh"),
                frame.localToWorldPoint(new Vec3(-2.5, 0.0, 0.0)),
                frame.localToWorldPoint(new Vec3(2.5, 0.0, 0.0)),
                0L,
                12L,
                Easing.LINEAR,
                Envelope.constant(1.0),
                HoldBehavior.SUPPRESS,
                RepeatSpec.none(),
                InstanceConflictPolicy.REPLACE
        ));
        session.schedule(5L, () -> controller.start(vecTween(
                session.key("tween/refresh"),
                frame.localToWorldPoint(new Vec3(-2.5, 0.0, 0.0)),
                frame.localToWorldPoint(new Vec3(0.0, 1.6, 0.0)),
                0L,
                10L,
                Easing.SMOOTHERSTEP,
                Envelope.constant(1.0),
                HoldBehavior.SUPPRESS,
                RepeatSpec.none(),
                InstanceConflictPolicy.REFRESH
        )));
        renderTween(session, frame, controller, COLOR_TWEEN, true, 1.0f, null);
    }

    private void runCurveLine(Session session, Player player) {
        CurvePath path = CurvePaths.create(new CurvePathSpec(List.of(
                new CurveSegmentSpec.Line(new Vec3(-3.0, 0.0, 0.0), new Vec3(3.0, 0.0, 0.0))
        )));
        renderCurve(session, player, path, COLOR_CURVE, true);
    }

    private void runCurveQuadratic(Session session, Player player) {
        CurvePath path = CurvePaths.create(new CurvePathSpec(List.of(
                new CurveSegmentSpec.QuadraticBezier(new Vec3(-3.0, 0.0, 0.0), new Vec3(0.0, 2.2, 0.0), new Vec3(3.0, 0.0, 0.0))
        )));
        renderCurve(session, player, path, COLOR_CURVE, true);
    }

    private void runCurveCubic(Session session, Player player) {
        CurvePath path = CurvePaths.create(new CurvePathSpec(List.of(
                new CurveSegmentSpec.CubicBezier(
                        new Vec3(-3.0, 0.0, 0.0),
                        new Vec3(-1.5, 2.0, 0.0),
                        new Vec3(1.5, 2.0, 0.0),
                        new Vec3(3.0, 0.0, 0.0)
                )
        )));
        renderCurve(session, player, path, COLOR_CURVE, true);
    }

    private void runCurveCatmull(Session session, Player player) {
        CurvePath path = CurvePaths.create(new CurvePathSpec(List.of(
                new CurveSegmentSpec.CatmullRom(
                        new Vec3(-4.0, 0.0, -1.0),
                        new Vec3(-2.5, 0.5, 0.0),
                        new Vec3(0.5, 1.6, 1.2),
                        new Vec3(3.5, 0.0, 0.2),
                        CatmullRomMode.CENTRIPETAL
                )
        )));
        renderCurve(session, player, path, COLOR_CURVE, true);
    }

    private void runCurveArc(Session session, Player player) {
        CurvePath path = CurvePaths.create(new CurvePathSpec(List.of(
                new CurveSegmentSpec.CircularArc(Vec3.ZERO, new Vec3(2.5, 0.0, 0.0), Vec3.UNIT_Y, Angle.degrees(180.0))
        )));
        renderCurve(session, player, path, COLOR_CURVE, true);
    }

    private void runCurveSplit(Session session, Player player) {
        CurvePath path = CurvePaths.create(new CurvePathSpec(List.of(
                new CurveSegmentSpec.CubicBezier(
                        new Vec3(-3.0, 0.0, 0.0),
                        new Vec3(-1.0, 2.0, 0.0),
                        new Vec3(1.0, 2.0, 0.0),
                        new Vec3(3.0, 0.0, 0.0)
                )
        )));
        Frame3 frame = demoFrame(player, 4.0, 0.9);
        CurvePath leading = path.split(0.5).leading();
        CurvePath trailing = path.split(0.5).trailing();
        renderStatic(session, List.of(player), transform(frame, leading.resampleEvenly(20)), COLOR_CURVE, 0.75f, DRAW_TICKS);
        renderStatic(session, List.of(player), transform(frame, trailing.resampleEvenly(20)), COLOR_CURVE_ALT, 0.75f, DRAW_TICKS);
    }

    private void runCurveTrim(Session session, Player player) {
        CurvePath path = CurvePaths.create(new CurvePathSpec(List.of(
                new CurveSegmentSpec.CubicBezier(
                        new Vec3(-3.0, 0.0, 0.0),
                        new Vec3(-2.0, 1.8, 0.0),
                        new Vec3(2.0, 1.8, 0.0),
                        new Vec3(3.0, 0.0, 0.0)
                )
        )));
        Frame3 frame = demoFrame(player, 4.0, 0.9);
        renderStatic(session, List.of(player), transform(frame, path.resampleEvenly(30)), 0x355070, 0.35f, DRAW_TICKS);
        renderStatic(session, List.of(player), transform(frame, path.trim(0.25, 0.75).resampleEvenly(18)), COLOR_CURVE, 0.9f, DRAW_TICKS);
    }

    private void runCurveReverse(Session session, Player player) {
        CurvePath path = CurvePaths.create(new CurvePathSpec(List.of(
                new CurveSegmentSpec.Line(new Vec3(-3.0, 0.0, 0.0), new Vec3(3.0, 0.0, 0.0))
        )));
        Frame3 frame = demoFrame(player, 4.0, 0.9);
        List<Vec3> worldPoints = transform(frame, path.resampleEvenly(14));
        renderStatic(session, List.of(player), worldPoints, COLOR_CURVE, 0.7f, DRAW_TICKS);
        renderMarkers(session, List.of(player), List.of(
                worldPoints.getFirst(),
                frame.localToWorldPoint(path.reverse().pointAtProgress(0.0))
        ), List.of(COLOR_CURVE_ALT, COLOR_CURVE));
    }

    private void runCurveResample(Session session, Player player) {
        CurvePath path = CurvePaths.create(new CurvePathSpec(List.of(
                new CurveSegmentSpec.CircularArc(Vec3.ZERO, new Vec3(2.2, 0.0, 0.0), Vec3.UNIT_Y, Angle.degrees(180.0))
        )));
        Frame3 frame = demoFrame(player, 4.0, 0.9);
        renderStatic(session, List.of(player), transform(frame, path.resampleEvenly(12)), COLOR_CURVE, 0.95f, DRAW_TICKS);
    }

    private void runTelegraphCircle(Session session, Player player) {
        session.telegraphs.start(new TelegraphSpec(
                session.key("telegraph/circle"),
                fixedAnchor(player),
                new TelegraphShape.Circle(3.0),
                new ViewerScope.Everyone(),
                new TelegraphTiming(4L, 12L, 4L, 1.0, 0.8),
                InstanceConflictPolicy.REPLACE,
                0
        ));
    }

    private void runTelegraphRing(Session session, Player player) {
        session.telegraphs.start(new TelegraphSpec(
                session.key("telegraph/ring"),
                fixedAnchor(player),
                new TelegraphShape.Ring(3.6, 0.45),
                new ViewerScope.Everyone(),
                new TelegraphTiming(4L, 12L, 4L, 1.0, 0.8),
                InstanceConflictPolicy.REPLACE,
                0
        ));
    }

    private void runTelegraphRectangle(Session session, Player player) {
        session.telegraphs.start(new TelegraphSpec(
                session.key("telegraph/rectangle"),
                fixedAnchor(player),
                new TelegraphShape.Rectangle(2.0, 3.2),
                new ViewerScope.Everyone(),
                new TelegraphTiming(3L, 12L, 4L, 1.0, 0.8),
                InstanceConflictPolicy.REPLACE,
                0
        ));
    }

    private void runTelegraphLine(Session session, Player player) {
        session.telegraphs.start(new TelegraphSpec(
                session.key("telegraph/line"),
                fixedAnchor(player),
                new TelegraphShape.LineRibbon(new Vec3(-2.5, 0.0, 0.0), new Vec3(2.5, 0.0, 4.0), 0.25),
                new ViewerScope.Everyone(),
                new TelegraphTiming(3L, 12L, 4L, 1.0, 0.7),
                InstanceConflictPolicy.REPLACE,
                0
        ));
    }

    private void runTelegraphCorridor(Session session, Player player) {
        session.telegraphs.start(new TelegraphSpec(
                session.key("telegraph/corridor"),
                fixedAnchor(player),
                new TelegraphShape.CapsuleCorridor(new Vec3(-1.5, 0.0, 0.0), new Vec3(1.5, 0.0, 5.0), 0.75),
                new ViewerScope.Everyone(),
                new TelegraphTiming(3L, 12L, 4L, 1.0, 0.8),
                InstanceConflictPolicy.REPLACE,
                0
        ));
    }

    private void runTelegraphCone(Session session, Player player) {
        session.telegraphs.start(new TelegraphSpec(
                session.key("telegraph/cone"),
                fixedAnchor(player),
                new TelegraphShape.Cone(5.0, Angle.degrees(35.0)),
                new ViewerScope.Everyone(),
                new TelegraphTiming(4L, 12L, 4L, 1.0, 0.8),
                InstanceConflictPolicy.REPLACE,
                0
        ));
    }

    private void runTelegraphArc(Session session, Player player) {
        session.telegraphs.start(new TelegraphSpec(
                session.key("telegraph/arc"),
                fixedAnchor(player),
                new TelegraphShape.Arc(4.0, 0.35, Angle.degrees(-120.0), Angle.degrees(240.0)),
                new ViewerScope.Everyone(),
                new TelegraphTiming(4L, 12L, 4L, 1.0, 0.8),
                InstanceConflictPolicy.REPLACE,
                0
        ));
    }

    private void runTelegraphPath(Session session, Player player) {
        CurvePath path = CurvePaths.create(new CurvePathSpec(List.of(
                new CurveSegmentSpec.CubicBezier(
                        new Vec3(-2.5, 0.0, 0.0),
                        new Vec3(-1.0, 1.2, 0.0),
                        new Vec3(1.0, 1.2, 3.5),
                        new Vec3(2.5, 0.0, 5.0)
                )
        )));
        session.telegraphs.start(new TelegraphSpec(
                session.key("telegraph/path"),
                fixedAnchor(player),
                new TelegraphShape.PathRibbon(path, 0.35),
                new ViewerScope.Everyone(),
                new TelegraphTiming(3L, 14L, 4L, 1.0, 0.8),
                InstanceConflictPolicy.REPLACE,
                0
        ));
    }

    private void runTelegraphRefresh(Session session, Player player) {
        Key key = session.key("telegraph/same");
        session.telegraphs.start(new TelegraphSpec(
                key,
                fixedAnchor(player),
                new TelegraphShape.Circle(2.5),
                new ViewerScope.Everyone(),
                new TelegraphTiming(2L, 8L, 3L, 1.0, 0.8),
                InstanceConflictPolicy.REPLACE,
                0
        ));
        session.schedule(6L, () -> session.telegraphs.start(new TelegraphSpec(
                key,
                fixedAnchor(player),
                new TelegraphShape.Circle(4.0),
                new ViewerScope.Everyone(),
                new TelegraphTiming(2L, 8L, 3L, 1.0, 0.8),
                InstanceConflictPolicy.REFRESH,
                0
        )));
    }

    private void runTelegraphReplace(Session session, Player player) {
        Key key = session.key("telegraph/replace");
        session.telegraphs.start(new TelegraphSpec(
                key,
                fixedAnchor(player),
                new TelegraphShape.Circle(2.0),
                new ViewerScope.Everyone(),
                new TelegraphTiming(2L, 8L, 3L, 1.0, 0.8),
                InstanceConflictPolicy.REPLACE,
                0
        ));
        session.schedule(6L, () -> session.telegraphs.start(new TelegraphSpec(
                key,
                fixedAnchor(player),
                new TelegraphShape.Rectangle(2.2, 4.0),
                new ViewerScope.Everyone(),
                new TelegraphTiming(2L, 8L, 3L, 1.0, 0.8),
                InstanceConflictPolicy.REPLACE,
                1
        )));
    }

    private void runTelegraphMissingAnchor(Session session, Player player) {
        session.telegraphs.start(new TelegraphSpec(
                session.key("telegraph/missing"),
                new AnchorRef.Entity(INSTANCE_SPACE, UUID.randomUUID()),
                new TelegraphShape.Circle(3.0),
                new ViewerScope.Everyone(),
                new TelegraphTiming(2L, 8L, 3L, 1.0, 0.8),
                InstanceConflictPolicy.REPLACE,
                0
        ));
    }

    private void runTelegraphScopes(Session session, Player player) {
        session.telegraphs.start(new TelegraphSpec(
                session.key("telegraph/everyone"),
                fixedAnchor(player),
                new TelegraphShape.Circle(2.2),
                new ViewerScope.Everyone(),
                new TelegraphTiming(2L, 12L, 2L, 1.0, 0.8),
                InstanceConflictPolicy.REPLACE,
                0
        ));
        session.telegraphs.start(new TelegraphSpec(
                session.key("telegraph/source"),
                new AnchorRef.Offset(entityAnchor(player), new Vec3(0.0, 0.0, 4.0)),
                new TelegraphShape.Cone(3.8, Angle.degrees(22.0)),
                new ViewerScope.SourceOnly(player.getUuid()),
                new TelegraphTiming(2L, 12L, 2L, 1.0, 0.8),
                InstanceConflictPolicy.REPLACE,
                1
        ));
        session.telegraphs.start(new TelegraphSpec(
                session.key("telegraph/explicit"),
                new AnchorRef.Offset(fixedAnchor(player), new Vec3(0.0, 0.0, 6.0)),
                new TelegraphShape.Ring(1.8, 0.2),
                new ViewerScope.Explicit(Set.of(player.getUuid())),
                new TelegraphTiming(2L, 12L, 2L, 1.0, 0.8),
                InstanceConflictPolicy.REPLACE,
                2
        ));
        session.telegraphs.start(new TelegraphSpec(
                session.key("telegraph/relation"),
                new AnchorRef.Offset(fixedAnchor(player), new Vec3(3.0, 0.0, 0.0)),
                new TelegraphShape.Rectangle(1.2, 2.2),
                new ViewerScope.Relation(player.getUuid(), ViewerRelation.ENEMY),
                new TelegraphTiming(2L, 12L, 2L, 1.0, 0.8),
                InstanceConflictPolicy.REPLACE,
                3
        ));
        feedback.info(player, "Relation scopes use the example harness policy: enemy means viewers other than the source.");
    }

    private void runTrajectoryBallistic(Session session, Player player) {
        renderTrajectory(session, player, new PreviewScope.OwnerOnly(player.getUuid()), motion(player, new Vec3(0.55, 0.75, 0.0), new Vec3(0.0, -0.06, 0.0), 18L, CollisionResponseMode.STOP_ON_HIT), false);
    }

    private void runTrajectoryResponses(Session session, Player player) {
        renderTrajectory(session, player, new PreviewScope.OwnerOnly(player.getUuid()), motion(player, new Vec3(0.5, 0.35, 0.0), Vec3.ZERO, 14L, CollisionResponseMode.STOP_ON_HIT), false);
        renderTrajectoryStatic(session, player, new PreviewScope.OwnerOnly(player.getUuid()), motion(player, new Vec3(0.5, 0.35, 0.0), Vec3.ZERO, 14L, CollisionResponseMode.REPORT_ONLY), COLOR_CURVE_ALT);
        renderTrajectoryStatic(session, player, new PreviewScope.OwnerOnly(player.getUuid()), motion(player, new Vec3(0.5, 0.35, 0.0), Vec3.ZERO, 14L, CollisionResponseMode.PASS_THROUGH), COLOR_CURVE);
        feedback.info(player, "BOUNCE and SLIDE currently stop at collision in the shared solver, so this harness does not fake distinct behavior.");
    }

    private void runTrajectoryOneShot(Session session, Player player) {
        AtomicReference<TrajectoryMotion> motion = new AtomicReference<>(motion(player, new Vec3(0.45, 0.6, 0.0), new Vec3(0.0, -0.05, 0.0), 16L, CollisionResponseMode.STOP_ON_HIT));
        renderPreviewController(session, player, "trajectory/oneshot", motion, new PreviewRecomputePolicy.OneShot(), true, false, false);
    }

    private void runTrajectoryEveryTick(Session session, Player player) {
        AtomicReference<TrajectoryMotion> motion = new AtomicReference<>(motion(player, new Vec3(0.45, 0.55, 0.0), new Vec3(0.0, -0.05, 0.0), 16L, CollisionResponseMode.STOP_ON_HIT));
        renderPreviewController(session, player, "trajectory/everytick", motion, new PreviewRecomputePolicy.EveryTick(), false, true, false);
    }

    private void runTrajectoryThreshold(Session session, Player player) {
        AtomicReference<TrajectoryMotion> motion = new AtomicReference<>(motion(player, new Vec3(0.45, 0.55, 0.0), new Vec3(0.0, -0.05, 0.0), 16L, CollisionResponseMode.STOP_ON_HIT));
        renderPreviewController(session, player, "trajectory/threshold", motion, new PreviewRecomputePolicy.Thresholded(1.0, Angle.degrees(12.0)), false, true, false);
    }

    private void runTrajectoryRefresh(Session session, Player player) {
        AtomicReference<TrajectoryMotion> motion = new AtomicReference<>(motion(player, new Vec3(0.42, 0.55, 0.0), new Vec3(0.0, -0.05, 0.0), 16L, CollisionResponseMode.STOP_ON_HIT));
        renderPreviewController(session, player, "trajectory/refresh", motion, new PreviewRecomputePolicy.OneShot(), false, false, true);
    }

    private void runTrajectoryReplace(Session session, Player player) {
        StandardTrajectoryPreviewController controller = new StandardTrajectoryPreviewController();
        AtomicReference<TrajectoryMotion> motion = new AtomicReference<>(motion(player, new Vec3(0.42, 0.55, 0.0), new Vec3(0.0, -0.05, 0.0), 16L, CollisionResponseMode.STOP_ON_HIT));
        controller.start(previewSpec(session.key("trajectory/replace"), player, motion::get, new PreviewRecomputePolicy.EveryTick(), InstanceConflictPolicy.REPLACE));
        session.schedule(6L, () -> controller.start(previewSpec(session.key("trajectory/replace"), player,
                () -> motion(player, new Vec3(0.65, 0.25, 0.0), Vec3.ZERO, 12L, CollisionResponseMode.STOP_ON_HIT),
                new PreviewRecomputePolicy.EveryTick(),
                InstanceConflictPolicy.REPLACE)));
        renderPreviewSnapshots(session, controller);
    }

    private void runTrajectoryScopes(Session session, Player player) {
        renderTrajectory(session, player, new PreviewScope.Everyone(), motion(player, new Vec3(0.45, 0.55, 0.0), new Vec3(0.0, -0.05, 0.0), 14L, CollisionResponseMode.STOP_ON_HIT), false);
        renderTrajectory(session, player, new PreviewScope.Explicit(Set.of(player.getUuid())), motion(player, new Vec3(0.35, 0.7, 0.0), new Vec3(0.0, -0.05, 0.0), 12L, CollisionResponseMode.STOP_ON_HIT), false);
    }

    private void runImpulseAdd(Session session, Player player) {
        startImpulse(player, session, impulseSpec(session.key("impulse/add"), ImpulseMode.ADD_VELOCITY,
                new ImpulseVector.World(new Vec3(0.0, 0.18, 0.65)), AxisMask.ALL, ImpulseStackMode.ADD, 0.0, InstanceConflictPolicy.REPLACE));
    }

    private void runImpulseSet(Session session, Player player) {
        startImpulse(player, session, impulseSpec(session.key("impulse/set"), ImpulseMode.SET_VELOCITY,
                new ImpulseVector.World(new Vec3(0.0, 0.15, 1.15)), AxisMask.ALL, ImpulseStackMode.PRIORITY_WINNER, 0.0, InstanceConflictPolicy.REPLACE));
    }

    private void runImpulseClamped(Session session, Player player) {
        startImpulse(player, session, impulseSpec(session.key("impulse/clamped"), ImpulseMode.CLAMPED_ADD,
                new ImpulseVector.World(new Vec3(0.0, 0.6, 1.5)), AxisMask.ALL, ImpulseStackMode.ADD, 0.8, InstanceConflictPolicy.REPLACE));
    }

    private void runImpulseDash(Session session, Player player) {
        startImpulse(player, session, impulseSpec(session.key("impulse/dash"), ImpulseMode.DASH_TOWARD_DIRECTION,
                new ImpulseVector.LocalHorizontal(new Vec3(0.0, 0.0, 1.3)), AxisMask.HORIZONTAL, ImpulseStackMode.PRIORITY_WINNER, 0.0, InstanceConflictPolicy.REPLACE));
    }

    private void runImpulsePull(Session session, Player player) {
        startImpulse(player, session, impulseSpec(session.key("impulse/pull"), ImpulseMode.PULL_TOWARD_POINT,
                new ImpulseVector.TowardPoint(worldPoint(player, new Vec3(0.0, 1.0, 0.0), 4.0), 0.9), AxisMask.ALL, ImpulseStackMode.ADD, 0.0, InstanceConflictPolicy.REPLACE));
    }

    private void runImpulsePush(Session session, Player player) {
        startImpulse(player, session, impulseSpec(session.key("impulse/push"), ImpulseMode.PUSH_AWAY_FROM_POINT,
                new ImpulseVector.AwayFromPoint(worldPoint(player, new Vec3(0.0, 0.0, 0.0), 2.0), 0.9), AxisMask.HORIZONTAL, ImpulseStackMode.ADD, 0.0, InstanceConflictPolicy.REPLACE));
    }

    private void runImpulseLaunch(Session session, Player player) {
        startImpulse(player, session, impulseSpec(session.key("impulse/launch"), ImpulseMode.UPWARD_LAUNCH,
                new ImpulseVector.World(new Vec3(0.0, 1.1, 0.0)), AxisMask.VERTICAL, ImpulseStackMode.PRIORITY_WINNER, 0.0, InstanceConflictPolicy.REPLACE));
    }

    private void runImpulseLocal(Session session, Player player) {
        startImpulse(player, session, impulseSpec(session.key("impulse/local"), ImpulseMode.ADD_VELOCITY,
                new ImpulseVector.LocalLook(new Vec3(0.0, 0.15, 0.85)), AxisMask.ALL, ImpulseStackMode.ADD, 0.0, InstanceConflictPolicy.REPLACE));
    }

    private void runImpulseMasks(Session session, Player player) {
        startImpulse(player, session, impulseSpec(session.key("impulse/masks"), ImpulseMode.ADD_VELOCITY,
                new ImpulseVector.World(new Vec3(0.0, 0.75, 1.2)), AxisMask.HORIZONTAL, ImpulseStackMode.ADD, 0.0, InstanceConflictPolicy.REPLACE));
    }

    private void runImpulseStack(Session session, Player player) {
        startImpulse(player, session, new ImpulseSpec(
                session.key("impulse/stack/a"),
                ImpulseMode.ADD_VELOCITY,
                new ImpulseVector.World(new Vec3(0.35, 0.15, 0.55)),
                0L,
                5L,
                Envelope.constant(1.0),
                AxisMask.ALL,
                ImpulseStackMode.MAX_MAGNITUDE_PER_AXIS,
                0.0,
                0,
                InstanceConflictPolicy.REPLACE
        ));
        startImpulse(player, session, new ImpulseSpec(
                session.key("impulse/stack/b"),
                ImpulseMode.ADD_VELOCITY,
                new ImpulseVector.World(new Vec3(-0.65, 0.05, 0.35)),
                0L,
                5L,
                Envelope.constant(1.0),
                AxisMask.ALL,
                ImpulseStackMode.MAX_MAGNITUDE_PER_AXIS,
                0.0,
                0,
                InstanceConflictPolicy.REPLACE
        ));
    }

    private void runImpulseRefresh(Session session, Player player) {
        Key key = session.key("impulse/refresh");
        startImpulse(player, session, impulseSpec(key, ImpulseMode.ADD_VELOCITY,
                new ImpulseVector.World(new Vec3(0.0, 0.15, 0.55)), AxisMask.ALL, ImpulseStackMode.ADD, 0.0, InstanceConflictPolicy.REPLACE));
        session.schedule(4L, () -> startImpulse(player, session, impulseSpec(key, ImpulseMode.ADD_VELOCITY,
                new ImpulseVector.World(new Vec3(0.0, 0.35, 1.05)), AxisMask.ALL, ImpulseStackMode.ADD, 0.0, InstanceConflictPolicy.REFRESH)));
    }

    private void runImpulseReplace(Session session, Player player) {
        Key key = session.key("impulse/replace");
        startImpulse(player, session, impulseSpec(key, ImpulseMode.SET_VELOCITY,
                new ImpulseVector.World(new Vec3(0.0, 0.15, 0.55)), AxisMask.ALL, ImpulseStackMode.PRIORITY_WINNER, 0.0, InstanceConflictPolicy.REPLACE));
        session.schedule(4L, () -> startImpulse(player, session, impulseSpec(key, ImpulseMode.SET_VELOCITY,
                new ImpulseVector.World(new Vec3(0.0, 0.10, 1.25)), AxisMask.ALL, ImpulseStackMode.PRIORITY_WINNER, 0.0, InstanceConflictPolicy.REPLACE)));
    }

    private void runAmbientChannels(Session session, Player player) {
        session.ambient.start(zoneSpec(
                session.key("ambient/channels"),
                entityAnchor(player),
                new SphereVolume(Vec3.ZERO, 4.0),
                new AmbientProfile(0.55, 0.55, 0.55, 0.35, 0.4),
                AmbientBlendMode.MAX,
                AmbientWeightModel.hardEdge(),
                0,
                80L,
                InstanceConflictPolicy.REPLACE
        ));
    }

    private void runAmbientHardEdge(Session session, Player player) {
        session.ambient.start(zoneSpec(
                session.key("ambient/hard"),
                fixedAnchor(player),
                new SphereVolume(Vec3.ZERO, 3.0),
                new AmbientProfile(0.45, 0.3, 0.2, 0.2, 0.3),
                AmbientBlendMode.MAX,
                AmbientWeightModel.hardEdge(),
                0,
                80L,
                InstanceConflictPolicy.REPLACE
        ));
    }

    private void runAmbientFeather(Session session, Player player) {
        session.ambient.start(zoneSpec(
                session.key("ambient/feather"),
                fixedAnchor(player),
                new SphereVolume(Vec3.ZERO, 2.5),
                new AmbientProfile(0.65, 0.5, 0.35, 0.25, 0.35),
                AmbientBlendMode.MAX,
                new AmbientWeightModel(2.5, WeightCurve.LINEAR),
                0,
                90L,
                InstanceConflictPolicy.REPLACE
        ));
    }

    private void runAmbientWeights(Session session, Player player) {
        session.ambient.start(zoneSpec(
                session.key("ambient/weights"),
                fixedAnchor(player),
                new CapsuleVolume(new Segment3(new Vec3(0.0, 0.0, -2.0), new Vec3(0.0, 0.0, 3.0)), 2.0),
                new AmbientProfile(0.6, 0.45, 0.25, 0.25, 0.25),
                AmbientBlendMode.MAX,
                new AmbientWeightModel(3.0, WeightCurve.SMOOTHSTEP),
                0,
                90L,
                InstanceConflictPolicy.REPLACE
        ));
    }

    private void runAmbientBlend(Session session, Player player) {
        AnchorRef anchor = fixedAnchor(player);
        session.ambient.start(zoneSpec(
                session.key("ambient/blend/a"),
                anchor,
                new SphereVolume(new Vec3(-1.5, 0.0, 0.0), 3.0),
                new AmbientProfile(0.25, 0.2, 0.2, 0.1, 0.15),
                AmbientBlendMode.WEIGHTED_BLEND,
                new AmbientWeightModel(2.0, WeightCurve.LINEAR),
                1,
                90L,
                InstanceConflictPolicy.REPLACE
        ));
        session.ambient.start(zoneSpec(
                session.key("ambient/blend/b"),
                anchor,
                new SphereVolume(new Vec3(1.5, 0.0, 0.0), 3.0),
                new AmbientProfile(0.7, 0.5, 0.35, 0.25, 0.4),
                AmbientBlendMode.WEIGHTED_BLEND,
                new AmbientWeightModel(2.0, WeightCurve.EXPONENTIAL),
                1,
                90L,
                InstanceConflictPolicy.REPLACE
        ));
    }

    private void runAmbientPriority(Session session, Player player) {
        AnchorRef anchor = fixedAnchor(player);
        session.ambient.start(zoneSpec(
                session.key("ambient/priority/low"),
                anchor,
                new OrientedBoxVolume(Frame3.world(new Vec3(-1.0, 0.0, 0.0)), new Vec3(2.5, 2.0, 2.5)),
                new AmbientProfile(0.2, 0.2, 0.2, 0.1, 0.1),
                AmbientBlendMode.PRIORITY_WINNER,
                AmbientWeightModel.hardEdge(),
                1,
                90L,
                InstanceConflictPolicy.REPLACE
        ));
        session.ambient.start(zoneSpec(
                session.key("ambient/priority/high"),
                anchor,
                new SphereVolume(new Vec3(1.0, 0.0, 0.0), 2.5),
                new AmbientProfile(0.85, 0.6, 0.35, 0.3, 0.45),
                AmbientBlendMode.PRIORITY_WINNER,
                AmbientWeightModel.hardEdge(),
                5,
                90L,
                InstanceConflictPolicy.REPLACE
        ));
    }

    private void runAmbientTtl(Session session, Player player) {
        session.ambient.start(zoneSpec(
                session.key("ambient/ttl"),
                fixedAnchor(player),
                new SphereVolume(Vec3.ZERO, 3.0),
                new AmbientProfile(0.55, 0.35, 0.25, 0.15, 0.15),
                AmbientBlendMode.MAX,
                AmbientWeightModel.hardEdge(),
                0,
                20L,
                InstanceConflictPolicy.REPLACE
        ));
    }

    private void runAmbientRefresh(Session session, Player player) {
        Key key = session.key("ambient/refresh");
        session.ambient.start(zoneSpec(
                key,
                fixedAnchor(player),
                new SphereVolume(Vec3.ZERO, 3.0),
                new AmbientProfile(0.3, 0.2, 0.2, 0.15, 0.1),
                AmbientBlendMode.MAX,
                AmbientWeightModel.hardEdge(),
                0,
                30L,
                InstanceConflictPolicy.REPLACE
        ));
        session.schedule(10L, () -> session.ambient.start(zoneSpec(
                key,
                fixedAnchor(player),
                new SphereVolume(Vec3.ZERO, 3.0),
                new AmbientProfile(0.75, 0.6, 0.35, 0.3, 0.25),
                AmbientBlendMode.MAX,
                AmbientWeightModel.hardEdge(),
                0,
                30L,
                InstanceConflictPolicy.REFRESH
        )));
    }

    private void runPathWarning(Session session, Player player) {
        CurvePath path = CurvePaths.create(new CurvePathSpec(List.of(
                new CurveSegmentSpec.CubicBezier(
                        new Vec3(-2.8, 0.0, 0.0),
                        new Vec3(-1.4, 1.3, 0.0),
                        new Vec3(1.8, 0.8, 3.5),
                        new Vec3(2.6, 0.0, 5.0)
                )
        )));
        session.telegraphs.start(new TelegraphSpec(
                session.key("combo/pathwarning"),
                fixedAnchor(player),
                new TelegraphShape.PathRibbon(path, 0.45),
                new ViewerScope.Everyone(),
                new TelegraphTiming(4L, 16L, 4L, 1.0, 0.9),
                InstanceConflictPolicy.REPLACE,
                0
        ));
    }

    private void runPreviewImpact(Session session, Player player) {
        TrajectoryMotion motion = motion(player, new Vec3(0.55, 0.65, 0.0), new Vec3(0.0, -0.05, 0.0), 16L, CollisionResponseMode.STOP_ON_HIT);
        var result = trajectories.solve(motion, collisionQuery(player.getInstance()));
        renderTrajectoryResult(session, List.of(player), result, COLOR_TRAJECTORY);
        Frame3 impactFrame = Frame3.world(result.endPosition());
        session.telegraphs.start(new TelegraphSpec(
                session.key("combo/impact"),
                new AnchorRef.Fixed(new AnchorSnapshot(INSTANCE_SPACE, impactFrame)),
                new TelegraphShape.Circle(1.8),
                new ViewerScope.Everyone(),
                new TelegraphTiming(2L, 10L, 3L, 1.0, 0.8),
                InstanceConflictPolicy.REPLACE,
                0
        ));
    }

    private void runDashAssist(Session session, Player player) {
        TrajectoryMotion motion = motion(player, new Vec3(0.6, 0.0, 0.0), Vec3.ZERO, 8L, CollisionResponseMode.PASS_THROUGH);
        renderTrajectory(session, player, new PreviewScope.OwnerOnly(player.getUuid()), motion, true);
        session.schedule(12L, () -> startImpulse(player, session, impulseSpec(
                session.key("combo/dash"),
                ImpulseMode.DASH_TOWARD_DIRECTION,
                new ImpulseVector.LocalHorizontal(new Vec3(0.0, 0.0, 1.2)),
                AxisMask.HORIZONTAL,
                ImpulseStackMode.PRIORITY_WINNER,
                0.0,
                InstanceConflictPolicy.REPLACE
        )));
    }

    private void runZonePulse(Session session, Player player) {
        Frame3 frame = demoFrame(player, 4.0, 0.9);
        StandardTweenController<Double> controller = new StandardTweenController<>();
        controller.start(new Tween<>(
                session.key("combo/pulse"),
                0.15,
                0.85,
                Interpolators.doubles(),
                0L,
                8L,
                Easing.SMOOTHSTEP,
                Envelope.constant(1.0),
                HoldBehavior.SUPPRESS,
                new RepeatSpec(RepeatMode.PING_PONG, 3),
                InstanceConflictPolicy.REPLACE
        ));
        session.repeat(0L, 1L, tick -> {
            Player owner = session.owner();
            if (owner == null) {
                controller.close();
                return false;
            }
            List<TweenSample<Double>> samples = controller.tick();
            if (!samples.isEmpty()) {
                double strength = samples.getFirst().value();
                session.ambient.start(zoneSpec(
                        session.key("combo/pulse-zone"),
                        new AnchorRef.Fixed(new AnchorSnapshot(INSTANCE_SPACE, frame)),
                        new SphereVolume(Vec3.ZERO, 3.5),
                        new AmbientProfile(strength, 0.3, 0.2 + (strength * 0.3), strength * 0.5, strength * 0.35),
                        AmbientBlendMode.MAX,
                        AmbientWeightModel.hardEdge(),
                        0,
                        20L,
                        InstanceConflictPolicy.REFRESH
                ));
                debug.point(List.of(owner), frame.origin().add(Vec3.UNIT_Y.multiply(0.2 + strength)), COLOR_AMBIENT, 0.8f + (float) strength);
            }
            boolean active = controller.hasActiveTweens();
            if (!active) {
                controller.close();
            }
            return active;
        });
    }

    private void renderTween(
            Session session,
            Frame3 frame,
            Tween<Vec3> tween,
            int rgb,
            boolean referenceLine,
            float baseScale,
            Function<TweenSample<Vec3>, Float> scaleResolver
    ) {
        StandardTweenController<Vec3> controller = new StandardTweenController<>();
        controller.start(tween);
        renderTween(session, frame, controller, rgb, referenceLine, baseScale, scaleResolver);
    }

    private void renderTween(
            Session session,
            Frame3 frame,
            StandardTweenController<Vec3> controller,
            int rgb,
            boolean referenceLine,
            float baseScale,
            Function<TweenSample<Vec3>, Float> scaleResolver
    ) {
        session.repeat(0L, 1L, tick -> {
            Player owner = session.owner();
            if (owner == null) {
                controller.close();
                return false;
            }
            if (referenceLine) {
                debug.polyline(List.of(owner), List.of(
                        frame.localToWorldPoint(new Vec3(-3.0, 0.0, 0.0)),
                        frame.localToWorldPoint(new Vec3(3.0, 0.0, 0.0))
                ), 0.5, 0x355070, 0.35f);
            }
            for (TweenSample<Vec3> sample : controller.tick()) {
                float scale = scaleResolver == null ? baseScale : scaleResolver.apply(sample);
                debug.point(List.of(owner), sample.value(), rgb, scale);
            }
            boolean active = controller.hasActiveTweens();
            if (!active) {
                controller.close();
            }
            return active;
        });
    }

    private void renderCurve(Session session, Player player, CurvePath path, int rgb, boolean markEnds) {
        Frame3 frame = demoFrame(player, 4.0, 0.9);
        List<Vec3> worldPoints = transform(frame, path.resampleEvenly(28));
        renderStatic(session, List.of(player), worldPoints, rgb, 0.8f, DRAW_TICKS);
        if (markEnds) {
            renderMarkers(session, List.of(player), List.of(worldPoints.getFirst(), worldPoints.getLast()), List.of(COLOR_CURVE_ALT, COLOR_IMPACT));
        }
    }

    private void renderTrajectory(Session session, Player player, PreviewScope scope, TrajectoryMotion motion, boolean clearAtEnd) {
        TrajectoryPreviewSnapshot snapshot = new TrajectoryPreviewSnapshot(session.key("trajectory/static"), scope,
                trajectories.solve(motion, collisionQuery(player.getInstance())), true, 0L);
        List<Player> viewers = session.viewers(snapshot.scope(), player);
        renderTrajectoryResult(session, viewers, snapshot.result(), COLOR_TRAJECTORY);
        if (clearAtEnd) {
            session.schedule(18L, () -> clear(player));
        }
    }

    private void renderTrajectoryStatic(Session session, Player player, PreviewScope scope, TrajectoryMotion motion, int rgb) {
        TrajectoryPreviewSnapshot snapshot = new TrajectoryPreviewSnapshot(session.key("trajectory/static"), scope,
                trajectories.solve(motion, collisionQuery(player.getInstance())), true, 0L);
        renderTrajectoryResult(session, session.viewers(snapshot.scope(), player), snapshot.result(), rgb);
    }

    private void renderPreviewController(
            Session session,
            Player player,
            String keyPath,
            AtomicReference<TrajectoryMotion> motion,
            PreviewRecomputePolicy policy,
            boolean driftLate,
            boolean driftEachTick,
            boolean forceRefresh
    ) {
        StandardTrajectoryPreviewController controller = new StandardTrajectoryPreviewController();
        controller.start(previewSpec(session.key(keyPath), player, motion::get, policy, InstanceConflictPolicy.REPLACE));
        if (driftLate) {
            session.schedule(6L, () -> motion.set(motion(player, new Vec3(0.65, 0.25, 0.0), Vec3.ZERO, 14L, CollisionResponseMode.STOP_ON_HIT)));
        }
        if (forceRefresh) {
            session.schedule(8L, () -> {
                motion.set(motion(player, new Vec3(0.62, 0.32, 0.0), Vec3.ZERO, 14L, CollisionResponseMode.STOP_ON_HIT));
                controller.refresh(session.key(keyPath));
            });
        }
        session.repeat(0L, 1L, tick -> {
            Player owner = session.owner();
            if (owner == null) {
                controller.close();
                return false;
            }
            if (driftEachTick) {
                motion.set(motion(owner, new Vec3(0.45 + (tick * 0.02), 0.55, 0.0), new Vec3(0.0, -0.05, 0.0), 14L, CollisionResponseMode.STOP_ON_HIT));
            }
            for (TrajectoryPreviewSnapshot snapshot : controller.tick()) {
                renderTrajectoryResult(session, session.viewers(snapshot.scope(), owner), snapshot.result(),
                        snapshot.recomputed() ? COLOR_TRAJECTORY : 0x7D8597);
            }
            return tick < 18L;
        });
    }

    private void renderPreviewSnapshots(Session session, StandardTrajectoryPreviewController controller) {
        session.repeat(0L, 1L, tick -> {
            Player owner = session.owner();
            if (owner == null) {
                controller.close();
                return false;
            }
            for (TrajectoryPreviewSnapshot snapshot : controller.tick()) {
                renderTrajectoryResult(session, session.viewers(snapshot.scope(), owner), snapshot.result(),
                        snapshot.recomputed() ? COLOR_TRAJECTORY : 0x7D8597);
            }
            return tick < 18L;
        });
    }

    private void renderTrajectoryResult(Session session, List<Player> viewers, sh.harold.creative.library.trajectory.TrajectoryPreviewResult result, int rgb) {
        if (viewers.isEmpty()) {
            return;
        }
        renderStatic(session, viewers, result.sampledPoints(), rgb, 0.65f, DRAW_TICKS);
        result.firstHit().map(CollisionHit::position).ifPresent(hit -> renderMarkers(session, viewers, List.of(hit), List.of(COLOR_IMPACT)));
    }

    private void renderStatic(Session session, List<Player> viewers, List<Vec3> points, int rgb, float scale, long durationTicks) {
        session.repeat(0L, 2L, tick -> {
            List<Player> online = online(viewers);
            if (online.isEmpty()) {
                return false;
            }
            debug.polyline(online, points, DRAW_STEP, rgb, scale);
            return tick < durationTicks / 2L;
        });
    }

    private void renderMarkers(Session session, List<Player> viewers, List<Vec3> points, List<Integer> colors) {
        session.repeat(0L, 2L, tick -> {
            List<Player> online = online(viewers);
            if (online.isEmpty()) {
                return false;
            }
            for (int index = 0; index < points.size(); index++) {
                debug.point(online, points.get(index), colors.get(Math.min(index, colors.size() - 1)), 1.1f);
            }
            return tick < DRAW_TICKS / 2L;
        });
    }

    private void startImpulse(Player player, Session session, ImpulseSpec spec) {
        KeyedHandle handle = impulses.start(player.getUuid(), spec);
        session.handles.add(handle);
        sounds.play(player, SoundCueKeys.RESULT_CONFIRM);
    }

    private Tween<Vec3> vecTween(
            Key key,
            Vec3 from,
            Vec3 to,
            long delayTicks,
            long durationTicks,
            Easing easing,
            Envelope envelope,
            HoldBehavior holdBehavior,
            RepeatSpec repeat,
            InstanceConflictPolicy conflictPolicy
    ) {
        return new Tween<>(
                key,
                from,
                to,
                Interpolators.vectors(),
                delayTicks,
                durationTicks,
                easing,
                envelope,
                holdBehavior,
                repeat,
                conflictPolicy
        );
    }

    private TrajectoryPreviewSpec previewSpec(
            Key key,
            Player player,
            sh.harold.creative.library.trajectory.TrajectoryMotionSource source,
            PreviewRecomputePolicy policy,
            InstanceConflictPolicy conflictPolicy
    ) {
        return new TrajectoryPreviewSpec(
                key,
                new PreviewScope.OwnerOnly(player.getUuid()),
                source,
                collisionQuery(player.getInstance()),
                policy,
                conflictPolicy
        );
    }

    private CollisionQuery collisionQuery(Instance instance) {
        return trajectories.blockCollision(instance);
    }

    private TrajectoryMotion motion(Player player, Vec3 localVelocity, Vec3 acceleration, long ticks, CollisionResponseMode mode) {
        Frame3 frame = entityFrame(player, false);
        Vec3 start = frame.origin().add(Vec3.UNIT_Y.multiply(1.0));
        return new TrajectoryMotion(
                start,
                frame.localToWorldVector(localVelocity),
                acceleration,
                new Vec3(1.0, 1.0, 1.0),
                0.0,
                ticks,
                mode
        );
    }

    private ImpulseSpec impulseSpec(
            Key key,
            ImpulseMode mode,
            ImpulseVector vector,
            AxisMask axisMask,
            ImpulseStackMode stackMode,
            double maxMagnitude,
            InstanceConflictPolicy conflictPolicy
    ) {
        return new ImpulseSpec(
                key,
                mode,
                vector,
                0L,
                4L,
                Envelope.constant(1.0),
                axisMask,
                stackMode,
                maxMagnitude,
                0,
                conflictPolicy
        );
    }

    private ZoneSpec zoneSpec(
            Key key,
            AnchorRef anchor,
            Volume volume,
            AmbientProfile profile,
            AmbientBlendMode blendMode,
            AmbientWeightModel weightModel,
            int priority,
            long ttlTicks,
            InstanceConflictPolicy conflictPolicy
    ) {
        return new ZoneSpec(key, anchor, volume, profile, blendMode, weightModel, priority, ttlTicks, conflictPolicy);
    }

    private AnchorRef fixedAnchor(Player player) {
        return new AnchorRef.Fixed(new AnchorSnapshot(INSTANCE_SPACE, demoFrame(player, 4.0, 0.0)));
    }

    private AnchorRef entityAnchor(Player player) {
        return new AnchorRef.Entity(INSTANCE_SPACE, player.getUuid());
    }

    private Frame3 demoFrame(Player player, double forwardOffset, double yOffset) {
        Frame3 entityFrame = entityFrame(player, true);
        Vec3 origin = entityFrame.origin().add(entityFrame.forward().multiply(forwardOffset)).add(Vec3.UNIT_Y.multiply(yOffset));
        return Frame3.of(origin, entityFrame.forward(), Vec3.UNIT_Y);
    }

    private Frame3 entityFrame(Entity entity, boolean horizontal) {
        Pos position = entity.getPosition();
        Vec direction = position.direction();
        Vec3 forward = new Vec3(direction.x(), direction.y(), direction.z());
        if (horizontal) {
            forward = new Vec3(forward.x(), 0.0, forward.z());
        }
        if (forward.isZero(1.0e-6)) {
            forward = Vec3.UNIT_Z;
        }
        return Frame3.of(new Vec3(position.x(), position.y(), position.z()), forward, Vec3.UNIT_Y);
    }

    private Vec3 worldPoint(Player player, Vec3 localOffset, double forwardOffset) {
        Frame3 frame = entityFrame(player, true);
        Vec3 origin = frame.origin().add(frame.forward().multiply(forwardOffset));
        return Frame3.of(origin, frame.forward(), Vec3.UNIT_Y).localToWorldPoint(localOffset);
    }

    private List<Vec3> transform(Frame3 frame, List<Vec3> localPoints) {
        List<Vec3> transformed = new ArrayList<>(localPoints.size());
        for (Vec3 point : localPoints) {
            transformed.add(frame.localToWorldPoint(point));
        }
        return transformed;
    }

    private List<Vec3> circle(Frame3 frame, double radius, int samples) {
        return localArc(frame, radius, samples, Angle.degrees(0.0), Angle.degrees(360.0));
    }

    private List<Vec3> localArc(Frame3 frame, double radius, int samples, Angle start, Angle sweep) {
        List<Vec3> points = new ArrayList<>(samples + 1);
        for (int index = 0; index <= samples; index++) {
            double u = index / (double) samples;
            double radians = start.radians() + (sweep.radians() * u);
            Vec3 local = new Vec3(Math.cos(radians) * radius, 0.0, Math.sin(radians) * radius);
            points.add(frame.localToWorldPoint(local));
        }
        return points;
    }

    private List<Vec3> sampleShape(TelegraphFrame frame) {
        Frame3 worldFrame = frame.anchor().frame();
        return switch (frame.shape()) {
            case TelegraphShape.Circle circle -> circle(worldFrame, circle.radius(), 28);
            case TelegraphShape.Ring ring -> circle(worldFrame, ring.radius(), 28);
            case TelegraphShape.Rectangle rectangle -> rectangle(worldFrame, rectangle.halfWidth(), rectangle.halfDepth());
            case TelegraphShape.LineRibbon line -> List.of(
                    worldFrame.localToWorldPoint(line.start()),
                    worldFrame.localToWorldPoint(line.end())
            );
            case TelegraphShape.CapsuleCorridor corridor -> {
                List<Vec3> points = new ArrayList<>();
                points.addAll(circle(Frame3.world(worldFrame.localToWorldPoint(corridor.start())), corridor.radius(), 16));
                points.addAll(circle(Frame3.world(worldFrame.localToWorldPoint(corridor.end())), corridor.radius(), 16));
                points.add(worldFrame.localToWorldPoint(corridor.start()));
                points.add(worldFrame.localToWorldPoint(corridor.end()));
                yield points;
            }
            case TelegraphShape.Cone cone -> cone(worldFrame, cone.radius(), cone.halfAngle());
            case TelegraphShape.Arc arc -> localArc(worldFrame, arc.radius(), 24, arc.startAngle(), arc.sweep());
            case TelegraphShape.PathRibbon ribbon -> transform(worldFrame, ribbon.path().resampleEvenly(24));
        };
    }

    private List<Vec3> rectangle(Frame3 frame, double halfWidth, double halfDepth) {
        Vec3 a = frame.localToWorldPoint(new Vec3(-halfWidth, 0.0, -halfDepth));
        Vec3 b = frame.localToWorldPoint(new Vec3(halfWidth, 0.0, -halfDepth));
        Vec3 c = frame.localToWorldPoint(new Vec3(halfWidth, 0.0, halfDepth));
        Vec3 d = frame.localToWorldPoint(new Vec3(-halfWidth, 0.0, halfDepth));
        return List.of(a, b, c, d, a);
    }

    private List<Vec3> cone(Frame3 frame, double radius, Angle halfAngle) {
        List<Vec3> edge = localArc(frame, radius, 18, halfAngle.multiply(-1.0), halfAngle.multiply(2.0));
        List<Vec3> points = new ArrayList<>(edge.size() + 3);
        points.add(frame.origin());
        points.add(edge.getFirst());
        points.addAll(edge);
        points.add(frame.origin());
        return points;
    }

    private static List<Player> online(Collection<? extends Player> viewers) {
        List<Player> online = new ArrayList<>();
        for (Player viewer : viewers) {
            if (viewer != null && viewer.isOnline()) {
                online.add(viewer);
            }
        }
        return online;
    }

    @FunctionalInterface
    private interface Scenario {
        void run(Session session, Player player);
    }

    private final class Session implements AutoCloseable {

        private final UUID ownerId;
        private final List<Task> tasks = new ArrayList<>();
        private final List<AutoCloseable> handles = new ArrayList<>();
        private final MinestomTelegraphPlatform telegraphs;
        private final MinestomAmbientZonePlatform ambient;

        private boolean closed;
        private int overlayBucket = -1;
        private int soundBucket = -1;
        private int cameraBucket = -1;
        private int borderBucket = -1;

        private Session(Player owner) {
            this.ownerId = owner.getUuid();
            this.telegraphs = new MinestomTelegraphPlatform(scheduler, this::resolveAnchor, this::acceptTelegraphs);
            this.ambient = new MinestomAmbientZonePlatform(scheduler, this::viewerStates, this::resolveAnchor, this::acceptAmbient);
        }

        private Player owner() {
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(ownerId);
            if (player == null || !player.isOnline() || player.isRemoved()) {
                return null;
            }
            return player;
        }

        private Key key(String path) {
            return Key.key("example", "minestom/" + ownerId + "/" + path);
        }

        private void schedule(long delayTicks, Runnable action) {
            tasks.add(scheduler.scheduleTask(() -> {
                if (!closed && owner() != null) {
                    action.run();
                }
            }, delayTicks == 0L ? TaskSchedule.immediate() : TaskSchedule.tick(Math.toIntExact(delayTicks)), TaskSchedule.stop()));
        }

        private void repeat(long delayTicks, long periodTicks, java.util.function.LongPredicate action) {
            final long[] tick = {0L};
            final Task[] task = new Task[1];
            task[0] = scheduler.scheduleTask(() -> {
                if (closed || owner() == null || !action.test(tick[0]++)) {
                    task[0].cancel();
                }
            }, delayTicks == 0L ? TaskSchedule.immediate() : TaskSchedule.tick(Math.toIntExact(delayTicks)), TaskSchedule.tick(Math.toIntExact(periodTicks)));
            tasks.add(task[0]);
        }

        private Optional<AnchorSnapshot> resolveAnchor(AnchorRef anchorRef) {
            return switch (anchorRef) {
                case AnchorRef.Fixed fixed -> Optional.of(fixed.snapshot());
                case AnchorRef.Offset offset -> resolveAnchor(offset.base()).map(snapshot -> snapshot.translated(offset.localOffset()));
                case AnchorRef.Entity entity -> {
                    Player owner = owner();
                    if (owner == null || owner.getInstance() == null) {
                        yield Optional.empty();
                    }
                    Entity minestomEntity = owner.getInstance().getEntityByUuid(entity.entityId());
                    if (minestomEntity == null || minestomEntity.isRemoved()) {
                        yield Optional.empty();
                    }
                    yield Optional.of(new AnchorSnapshot(INSTANCE_SPACE, entityFrame(minestomEntity, true)));
                }
            };
        }

        private List<ViewerAmbientState> viewerStates() {
            Player owner = owner();
            if (owner == null) {
                return List.of();
            }
            Pos position = owner.getPosition();
            return List.of(new ViewerAmbientState(
                    ownerId,
                    INSTANCE_SPACE,
                    new Vec3(position.x(), position.y(), position.z())
            ));
        }

        private void acceptTelegraphs(List<TelegraphFrame> frames) {
            for (TelegraphFrame frame : frames) {
                List<Player> viewers = viewers(frame.viewerScope(), frame.anchor().spaceId());
                if (viewers.isEmpty()) {
                    continue;
                }
                int rgb = telegraphColor(frame.key());
                float scale = Math.max(0.55f, (float) frame.thickness());
                debug.polyline(viewers, sampleShape(frame), DRAW_STEP, applyAlpha(rgb, frame.alpha()), scale);
            }
        }

        private void acceptAmbient(List<AmbientSnapshot> snapshots) {
            Player owner = owner();
            if (owner == null || snapshots.isEmpty()) {
                return;
            }
            AmbientProfile profile = snapshots.getFirst().profile();
            updateOverlay(owner, profile.overlayStrength());
            updateSound(owner, profile.soundStrength());
            updateCamera(owner, profile.cameraStrength());
            updateParticles(owner, profile.particleStrength());
            updateBorder(owner, profile.borderPressure());
        }

        private void updateOverlay(Player owner, Double strength) {
            Key key = key("ambient/overlay");
            int bucket = bucket(strength);
            if (bucket == overlayBucket) {
                return;
            }
            overlayBucket = bucket;
            if (bucket == 0) {
                overlays.clear(owner, key);
                return;
            }
            float opacity = Math.min(0.85f, 0.15f + (bucket * 0.12f));
            overlays.show(owner, new ScreenOverlayRequest(
                    key,
                    new ScreenOverlay(
                            TextColor.color(applyAlpha(COLOR_AMBIENT, 0.9)),
                            opacity,
                            Duration.ZERO,
                            Duration.ofMillis(250),
                            Duration.ofMillis(250),
                            OverlayConflictPolicy.STACK
                    )
            ));
        }

        private void updateSound(Player owner, Double strength) {
            int bucket = bucket(strength);
            if (bucket <= soundBucket) {
                soundBucket = bucket;
                return;
            }
            soundBucket = bucket;
            if (bucket >= 3) {
                sounds.play(owner, new SoundCue.SoundEffect(Sound.sound(
                        Key.key("minecraft", "block.amethyst_block.resonate"),
                        Sound.Source.AMBIENT,
                        0.55f + (bucket * 0.08f),
                        0.9f + (bucket * 0.05f)
                )));
            }
        }

        private void updateCamera(Player owner, Double strength) {
            int bucket = bucket(strength);
            if (bucket == cameraBucket) {
                return;
            }
            cameraBucket = bucket;
            camera.stopAll(owner);
            if (bucket <= 0) {
                return;
            }
            camera.start(owner, CameraMotions.motion(
                    key("ambient/camera"),
                    BlendMode.ADD,
                    CameraMotions.axis(0.15 * bucket, 6L, 0L, Waveform.SINE),
                    CameraMotions.axis(0.10 * bucket, 5L, 0L, Waveform.COSINE),
                    CameraMotions.easeOut(6L, 0.55 + (bucket * 0.08), EaseOutCurve.CUBIC)
            ));
        }

        private void updateParticles(Player owner, Double strength) {
            int bucket = bucket(strength);
            if (bucket <= 0) {
                return;
            }
            Pos position = owner.getPosition();
            Vec3 center = new Vec3(position.x(), position.y() + 1.0, position.z());
            List<Vec3> ring = new ArrayList<>();
            double radius = 0.6 + (bucket * 0.2);
            for (int index = 0; index < 8; index++) {
                double radians = (Math.PI * 2.0 * index) / 8.0;
                ring.add(center.add(new Vec3(Math.cos(radians) * radius, 0.15, Math.sin(radians) * radius)));
            }
            debug.points(List.of(owner), ring, COLOR_AMBIENT, 0.45f + (bucket * 0.1f));
        }

        private void updateBorder(Player owner, Double strength) {
            int bucket = bucket(strength);
            if (bucket == borderBucket) {
                return;
            }
            borderBucket = bucket;
            if (bucket > 0) {
                feedback.info(owner, "Ambient border pressure now {level}/5.", Message.slot("level", bucket));
            }
        }

        private List<Player> viewers(ViewerScope scope, SpaceId spaceId) {
            Player owner = owner();
            if (owner == null || owner.getInstance() == null) {
                return List.of();
            }
            return switch (scope) {
                case ViewerScope.Everyone ignored -> owner.getInstance().getPlayers().stream().toList();
                case ViewerScope.SourceOnly sourceOnly -> onlinePlayers(Set.of(sourceOnly.sourceId()));
                case ViewerScope.Explicit explicit -> onlinePlayers(explicit.viewerIds());
                case ViewerScope.Relation relation -> relationViewers(relation);
            };
        }

        private List<Player> viewers(PreviewScope scope, Player owner) {
            return switch (scope) {
                case PreviewScope.Everyone ignored -> owner.getInstance() == null ? List.of() : owner.getInstance().getPlayers().stream().toList();
                case PreviewScope.OwnerOnly ownerOnly -> onlinePlayers(Set.of(ownerOnly.ownerId()));
                case PreviewScope.Explicit explicit -> onlinePlayers(explicit.viewerIds());
            };
        }

        private List<Player> onlinePlayers(Set<UUID> viewerIds) {
            return viewerIds.stream()
                    .map(MinecraftServer.getConnectionManager()::getOnlinePlayerByUuid)
                    .filter(Objects::nonNull)
                    .filter(Player::isOnline)
                    .toList();
        }

        private List<Player> relationViewers(ViewerScope.Relation relation) {
            Player owner = owner();
            if (owner == null || owner.getInstance() == null) {
                return List.of();
            }
            return owner.getInstance().getPlayers().stream()
                    .filter(player -> switch (relation.relation()) {
                        case ENEMY -> !player.getUuid().equals(relation.sourceId());
                        case ALLY, PARTY, TEAM -> player.getUuid().equals(relation.sourceId());
                    })
                    .toList();
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            telegraphs.close();
            ambient.close();
            for (AutoCloseable handle : handles) {
                try {
                    handle.close();
                } catch (Exception ignored) {
                }
            }
            tasks.forEach(Task::cancel);
            Player owner = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(ownerId);
            if (owner != null) {
                overlays.clearAll(owner);
            }
        }
    }

    private static int telegraphColor(Key key) {
        String value = key.value();
        if (value.contains("combo") || value.contains("impact")) {
            return COLOR_TELEGRAPH_ALT;
        }
        if (value.contains("path")) {
            return COLOR_CURVE_ALT;
        }
        return COLOR_TELEGRAPH;
    }

    private static int bucket(Double strength) {
        if (strength == null || strength <= 0.05) {
            return 0;
        }
        return Math.max(1, Math.min(5, (int) Math.ceil(strength * 5.0)));
    }

    private static int applyAlpha(int rgb, double alpha) {
        double clamped = Math.max(0.0, Math.min(1.0, alpha));
        int red = (int) (((rgb >> 16) & 0xFF) * clamped);
        int green = (int) (((rgb >> 8) & 0xFF) * clamped);
        int blue = (int) ((rgb & 0xFF) * clamped);
        return (red << 16) | (green << 8) | blue;
    }
}
