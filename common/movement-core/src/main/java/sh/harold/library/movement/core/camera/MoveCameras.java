package sh.harold.library.movement.core.camera;

import net.kyori.adventure.key.Key;
import sh.harold.library.camera.rig.CameraChannel;
import sh.harold.library.camera.rig.cue.CameraCue;
import sh.harold.library.camera.rig.cue.ChannelCurve;
import sh.harold.library.camera.rig.cue.CueCurve;
import sh.harold.library.movement.BodyState;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.core.tuning.ArmKey;
import sh.harold.library.movement.core.tuning.JumpTuning;
import sh.harold.library.movement.core.tuning.MantleTuning;
import sh.harold.library.movement.core.tuning.RollTuning;
import sh.harold.library.movement.core.tuning.SlideTuning;
import sh.harold.library.movement.core.tuning.TickKey;
import sh.harold.library.movement.core.tuning.WallJumpTuning;
import sh.harold.library.tween.Easing;
import sh.harold.library.spatial.Vec3;
import sh.harold.library.tween.track.Track;

import java.util.List;

/**
 * Each move's camera table from 02 as cues. Times are cue-local: {@code k + 1} is the end of move tick {@code Tk}, so a
 * table row "by T2" is local time 3. Pitch is positive looking down; roll is positive raising the left side.
 */
public final class MoveCameras {

    public static final Key JUMP_BOOST = Key.key(MoveKeys.NAMESPACE, "camera/jump_boost");
    public static final Key LANDING = Key.key(MoveKeys.NAMESPACE, "camera/landing");
    public static final Key MANTLE = Key.key(MoveKeys.NAMESPACE, "camera/mantle");
    public static final Key MANTLE_HOP = Key.key(MoveKeys.NAMESPACE, "camera/mantle_hop");
    public static final Key MANTLE_REFUSAL = Key.key(MoveKeys.NAMESPACE, "camera/mantle_refusal");
    public static final Key ROLL = Key.key(MoveKeys.NAMESPACE, "camera/roll");
    public static final Key SLIDE_EYE = Key.key(MoveKeys.NAMESPACE, "camera/slide_eye");
    public static final Key WALL_KICK = Key.key(MoveKeys.NAMESPACE, "camera/wall_kick");

    private static final double STAND = BodyState.PLAYER_EYE_HEIGHT;

    /**
     * An absolute eye curve holds its last value this many ticks past the pose's end, so the body is standing and the
     * host camera's eye easing has been held there before the curve lets go.
     */
    public static final int EYE_HOLD_TICKS = 2;

    private MoveCameras() {
    }

    /** Boost held long enough: FOV eased in, held until the host releases it at the apex. */
    public static CameraCue jumpBoost(JumpTuning.Camera camera) {
        return CameraCue.builder(JUMP_BOOST).owner(MoveKeys.JUMP)
                .channel(CameraChannel.FOV, CueCurve.held(camera.boostFov(), camera.boostFovInTicks(),
                        Easing.QUAD_OUT, camera.boostFovOutTicks(), Easing.QUAD_IN_OUT))
                .build();
    }

    /**
     * The landing dip (J4): {@link JumpTuning.Camera#landingDip(double)} deep, down over {@code landingDipTicks}, back
     * over {@code landingRecoverTicks} with an ease-out. No shake: a hard landing is a stumble, and the vanilla hurt
     * tilt covers damage (J5).
     */
    public static CameraCue landing(JumpTuning.Camera camera, double fall) {
        double dip = camera.landingDip(fall);
        int down = Math.max(1, camera.landingDipTicks());
        int up = Math.max(1, camera.landingRecoverTicks());
        Track<Double> track = Track.scalar()
                .eased(0.0, 0.0, Easing.QUAD_OUT)
                .eased(down, -dip, Easing.QUAD_OUT)
                .eased(down + up, 0.0, Easing.LINEAR)
                .build();
        return CameraCue.builder(LANDING).owner(MoveKeys.JUMP)
                .channel(CameraChannel.EYE_HEIGHT, CueCurve.track(track))
                .build();
    }

    /**
     * The climb (MA1, MA3, MA13, MA14, MA18): the catch bounce, the look down at the hands and ledge (added to the
     * player's pitch; Reduced keeps {@code reducedPitchScale} of it), the rocking roll toward the leading hand
     * ({@code leadingHandSign}), and both arms gripping then sliding down and out. No FOV.
     */
    public static CameraCue climb(MantleTuning.Climb climb, double leadingHandSign) {
        MantleTuning.ClimbCamera camera = climb.camera();
        CameraCue.Builder builder = CameraCue.builder(MANTLE).owner(MoveKeys.MANTLE)
                .channel(CameraChannel.EYE_HEIGHT, CueCurve.track(keyed(camera.eye(), 1.0)))
                .curve(ChannelCurve.of(CameraChannel.PITCH, CueCurve.track(keyed(camera.pitch(), 1.0)))
                        .withReduced(CueCurve.track(keyed(camera.pitch(), camera.reducedPitchScale()))))
                .channel(CameraChannel.ROLL, CueCurve.track(keyed(camera.roll(), leadingHandSign)));
        arms(builder, CameraChannel.ARM_MAIN, camera.arms());
        arms(builder, CameraChannel.ARM_OFF, camera.arms());
        return builder.build();
    }

    /** The vault (MA2, MA3): the nod, halved in Reduced, and the planting main arm. No roll, no FOV. */
    public static CameraCue vault(MantleTuning.Vault vault) {
        MantleTuning.VaultCamera camera = vault.camera();
        CameraCue.Builder builder = CameraCue.builder(MANTLE).owner(MoveKeys.MANTLE)
                .curve(ChannelCurve.of(CameraChannel.PITCH, CueCurve.track(keyed(camera.pitch(), 1.0)))
                        .withReduced(CueCurve.track(keyed(camera.pitch(), camera.reducedPitchScale()))));
        arms(builder, CameraChannel.ARM_MAIN, camera.mainArm());
        return builder.build();
    }

    /** The mantle hop's FOV kick (MA10), the one FOV change a mantle makes. */
    public static CameraCue mantleHop(MantleTuning.ClimbCamera camera) {
        return CameraCue.builder(MANTLE_HOP).owner(MoveKeys.MANTLE)
                .channel(CameraChannel.FOV, CueCurve.pulse(camera.hopFov(), Math.max(1, camera.hopFovInTicks()),
                        Easing.QUAD_OUT, 0.0, Math.max(1, camera.hopFovOutTicks()), Easing.QUAD_IN_OUT))
                .build();
    }

    /** A refused mantle's slap (MA12): both arms touch the wall. Nothing else moves. */
    public static CameraCue mantleRefusal(MantleTuning.Refusal refusal) {
        CameraCue.Builder builder = CameraCue.builder(MANTLE_REFUSAL).owner(MoveKeys.MANTLE_REFUSAL);
        arms(builder, CameraChannel.ARM_MAIN, refusal.arms());
        arms(builder, CameraChannel.ARM_OFF, refusal.arms());
        return builder.build();
    }

    /** Keys in move ticks as a track from rest at cue time 0, each value times {@code scale}, eased in and out. */
    static Track<Double> keyed(List<TickKey> keys, double scale) {
        var track = Track.scalar().eased(0.0, 0.0, Easing.QUAD_IN_OUT);
        for (TickKey key : keys) {
            track.eased(key.localTime(), key.value() * scale, Easing.QUAD_IN_OUT);
        }
        return track.build();
    }

    private static void arms(CameraCue.Builder builder, List<CameraChannel> channels, List<ArmKey> keys) {
        if (keys.isEmpty()) {
            return;
        }
        for (int axis = 0; axis < 6; axis++) {
            boolean moves = false;
            var track = Track.scalar().eased(0.0, 0.0, Easing.QUAD_IN_OUT);
            for (ArmKey key : keys) {
                double value = key.pose().get(axis);
                moves |= value != 0.0;
                track.eased(key.localTime(), value, Easing.QUAD_IN_OUT);
            }
            if (moves) {
                builder.channel(channels.get(axis), CueCurve.track(track.build()));
            }
        }
    }

    /** The roll's eye height from a standing start: {@link #rollDesignedEye(RollTuning, double)} from 1.62. */
    public static Track<Double> rollDesignedEye(RollTuning tuning) {
        return rollDesignedEye(tuning, STAND);
    }

    /**
     * The roll's eye height, absolute: held where the camera was ({@code from}) through T0, an ease-in to the tuck by
     * the end of T2, held to the end of T6, back to standing by the end of T9, then held standing for
     * {@link #EYE_HOLD_TICKS} more so the host camera is standing with the body before the curve lets go. 02 draws
     * {@code from} as 1.62; the sneak press usually has the player crouching already, and the tuck starts from there.
     */
    public static Track<Double> rollDesignedEye(RollTuning tuning, double from) {
        RollTuning.Camera camera = tuning.camera();
        double standing = camera.eyeRiseTick() + 1.0;
        return Track.scalar()
                .eased(0.0, from, Easing.LINEAR)
                .eased(1.0, from, Easing.QUAD_IN)
                .eased(camera.eyeLowTick() + 1.0, camera.eyeLow(), Easing.LINEAR)
                .eased(camera.eyeRiseFromTick() + 1.0, camera.eyeLow(), Easing.QUAD_IN_OUT)
                .eased(standing, STAND, Easing.LINEAR)
                .eased(Math.max(standing, tuning.ticks()) + EYE_HOLD_TICKS, STAND, Easing.LINEAR)
                .build();
    }

    /** Which way the roll turns the view (RO1). The camera never turns yaw. */
    public enum RollAxis {
        /** Travel within {@code sideMinDegrees} of the look: a forward pitch turn. */
        FORWARD,
        /** Between: no turn; the dip and a roll toward the travel side. */
        SIDE,
        /** More than {@code sideMaxDegrees} from the look: a backward pitch turn. */
        BACKWARD
    }

    /**
     * The roll axis for horizontal travel {@code travel} and look {@code facing}. With no horizontal travel the angle
     * counts as 0, a forward turn.
     */
    public static RollAxis rollAxis(RollTuning.Camera camera, Vec3 travel, Vec3 facing) {
        Vec3 flatTravel = travel.withY(0.0);
        Vec3 flatFacing = facing.withY(0.0);
        if (flatTravel.lengthSquared() < 1.0e-8 || flatFacing.lengthSquared() < 1.0e-8) {
            return RollAxis.FORWARD;
        }
        double cos = Math.clamp(flatTravel.normalize().dot(flatFacing.normalize()), -1.0, 1.0);
        double degrees = Math.toDegrees(Math.acos(cos));
        if (degrees <= camera.sideMinDegrees() + 1.0e-9) {
            return RollAxis.FORWARD;
        }
        return degrees <= camera.sideMaxDegrees() + 1.0e-9 ? RollAxis.SIDE : RollAxis.BACKWARD;
    }

    /** The forward roll: {@link #roll(RollTuning, RollAxis, double)} with a forward turn. */
    public static CameraCue roll(RollTuning tuning) {
        return roll(tuning, RollAxis.FORWARD, 0.0);
    }

    /**
     * The roll: a full pitch turn forward or backward (unwound, so it does not clamp at straight down), or for a
     * sideways roll the dip plus {@code sideRoll} toward {@code sideSign} (positive: travelling to the right); then the
     * FOV kick, the designed eye height and look damping. Reduced always uses the dip, with no roll.
     */
    public static CameraCue roll(RollTuning tuning, RollAxis axis, double sideSign) {
        RollTuning.Camera camera = tuning.camera();
        double half = camera.halfTurnTick() + 1.0;
        double end = tuning.ticks();
        double turnSign = axis == RollAxis.BACKWARD ? -1.0 : 1.0;
        Track<Double> turn = Track.scalar()
                .eased(0.0, 0.0, Easing.QUAD_IN)
                .eased(half, turnSign * camera.turnDegrees() / 2.0, Easing.QUAD_OUT)
                .eased(end, turnSign * camera.turnDegrees(), Easing.LINEAR)
                .build();
        Track<Double> dip = Track.scalar()
                .eased(0.0, 0.0, Easing.QUAD_OUT)
                .eased(camera.fovPeakTick() + 1.0, camera.reducedDip(), Easing.QUAD_IN_OUT)
                .eased(end, 0.0, Easing.LINEAR)
                .build();
        Track<Double> fov = Track.scalar()
                .eased(0.0, 0.0, Easing.QUAD_OUT)
                .eased(camera.fovPeakTick() + 1.0, camera.fov(), Easing.QUAD_IN_OUT)
                .eased(camera.fovEndTick(), 0.0, Easing.LINEAR)
                .build();
        CameraCue.Builder builder = CameraCue.builder(ROLL).owner(MoveKeys.ROLL);
        switch (axis) {
            case FORWARD -> builder.curve(ChannelCurve.of(CameraChannel.PITCH, CueCurve.track(turn)).unwoundPitch()
                    .withReduced(CueCurve.track(dip)));
            case BACKWARD -> builder.curve(ChannelCurve.of(CameraChannel.PITCH, CueCurve.track(turn))
                    .unwoundPitch().withReduced(CueCurve.track(dip)));
            case SIDE -> {
                builder.channel(CameraChannel.PITCH, CueCurve.track(dip));
                double lean = camera.sideRoll() * Math.signum(sideSign);
                if (lean != 0.0) {
                    builder.channel(CameraChannel.ROLL, CueCurve.track(Track.scalar()
                            .eased(0.0, 0.0, Easing.QUAD_OUT)
                            .eased(camera.fovPeakTick() + 1.0, lean, Easing.QUAD_IN_OUT)
                            .eased(end, 0.0, Easing.LINEAR)
                            .build()));
                }
            }
        }
        return builder
                .channel(CameraChannel.FOV, CueCurve.track(fov))
                .curve(ChannelCurve.of(CameraChannel.EYE_HEIGHT, CueCurve.track(rollDesignedEye(tuning)))
                        .absoluteEyeHeightFrom(eye -> CueCurve.track(rollDesignedEye(tuning, eye))))
                .lookScale(tuning.lookScale(), tuning.ticks())
                .build();
    }

    /** The slide's eye height from a standing start: {@link #slideDesignedEye(SlideTuning, double)} from 1.62. */
    public static Track<Double> slideDesignedEye(SlideTuning tuning) {
        return slideDesignedEye(tuning, STAND);
    }

    /** The slide's designed eye height while sliding: eased out from {@code from} to the low height, then held. */
    public static Track<Double> slideDesignedEye(SlideTuning tuning, double from) {
        SlideTuning.Camera camera = tuning.camera();
        return Track.scalar()
                .eased(0.0, from, Easing.QUAD_OUT)
                .eased(camera.eyeLowTick() + 1.0, camera.eyeLow(), Easing.LINEAR)
                .build();
    }

    /** The slide's held eye cue, absolute and from the current eye, from T0 until the slide ends. */
    public static CameraCue slideEye(SlideTuning tuning) {
        return CameraCue.builder(SLIDE_EYE).owner(MoveKeys.SLIDE)
                .curve(ChannelCurve.of(CameraChannel.EYE_HEIGHT, held(slideDesignedEye(tuning)))
                        .absoluteEyeHeightFrom(eye -> held(slideDesignedEye(tuning, eye))))
                .build();
    }

    /**
     * The slide's exit: the eye, absolute, returns from wherever the camera is to standing over
     * {@code eyeReturnTicks}, then holds standing for {@link #EYE_HOLD_TICKS} more so the host camera is standing with
     * the body before the curve lets go.
     *
     * @param stayLow the body stays low (no headroom, crawling): the eye goes to the body's own low height and stays
     */
    public static CameraCue slideExit(SlideTuning tuning, boolean stayLow) {
        return CameraCue.builder(SLIDE_EYE).owner(MoveKeys.SLIDE)
                .curve(ChannelCurve.of(CameraChannel.EYE_HEIGHT, slideExitEye(tuning, tuning.camera().eyeLow(), stayLow))
                        .absoluteEyeHeightFrom(eye -> slideExitEye(tuning, eye, stayLow)))
                .build();
    }

    private static CueCurve slideExitEye(SlideTuning tuning, double from, boolean stayLow) {
        double target = stayLow ? tuning.eyeHeight() : STAND;
        int ticks = Math.max(1, tuning.camera().eyeReturnTicks());
        Track<Double> designed = Track.scalar()
                .eased(0.0, from, Easing.QUAD_IN_OUT)
                .eased(ticks, target, Easing.LINEAR)
                .eased(ticks + EYE_HOLD_TICKS, target, Easing.LINEAR)
                .build();
        return stayLow ? held(designed) : CueCurve.track(designed);
    }

    private static CueCurve held(Track<Double> track) {
        return CueCurve.sampled(track::sample, Double.POSITIVE_INFINITY);
    }

    /** The wall kick: roll leaning away from the wall ({@code awaySign}) and FOV, held, then eased back. */
    public static CameraCue wallKick(WallJumpTuning.Camera camera, double awaySign) {
        double in = camera.inTick() + 1.0;
        double hold = camera.holdTick() + 1.0;
        double back = camera.backTick() + 1.0;
        return CameraCue.builder(WALL_KICK).owner(MoveKeys.WALL_JUMP)
                .channel(CameraChannel.ROLL, CueCurve.track(kick(camera.roll() * awaySign, in, hold, back)))
                .channel(CameraChannel.FOV, CueCurve.track(kick(camera.fov(), in, hold, back)))
                .build();
    }

    private static Track<Double> kick(double amount, double in, double hold, double back) {
        return Track.scalar()
                .eased(0.0, 0.0, Easing.QUAD_OUT)
                .eased(in, amount, Easing.LINEAR)
                .eased(hold, amount, Easing.QUAD_IN_OUT)
                .eased(back, 0.0, Easing.LINEAR)
                .build();
    }
}
