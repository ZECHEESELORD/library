package sh.harold.library.movement.core.camera;

import net.kyori.adventure.key.Key;
import sh.harold.library.camera.rig.CameraChannel;
import sh.harold.library.camera.rig.cue.CameraCue;
import sh.harold.library.camera.rig.cue.ChannelCurve;
import sh.harold.library.camera.rig.cue.CueCurve;
import sh.harold.library.movement.BodyState;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.core.tuning.JumpTuning;
import sh.harold.library.movement.core.tuning.MantleTuning;
import sh.harold.library.movement.core.tuning.RollTuning;
import sh.harold.library.movement.core.tuning.SlideTuning;
import sh.harold.library.movement.core.tuning.WallJumpTuning;
import sh.harold.library.tween.Easing;
import sh.harold.library.tween.track.Track;

/**
 * Each move's camera table from 02 as cues. Times are cue-local: {@code k + 1} is the end of move tick {@code Tk}, so a
 * table row "by T2" is local time 3. Pitch is positive looking down; roll is positive raising the left side.
 */
public final class MoveCameras {

    public static final Key JUMP_BOOST = Key.key(MoveKeys.NAMESPACE, "camera/jump_boost");
    public static final Key LANDING = Key.key(MoveKeys.NAMESPACE, "camera/landing");
    public static final Key MANTLE = Key.key(MoveKeys.NAMESPACE, "camera/mantle");
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

    /** The landing dip, plus the hard-landing shake when {@code hard}. */
    public static CameraCue landing(JumpTuning.Camera camera, double fall, boolean hard) {
        double dip = hard ? camera.landingDipMax() : Math.min(camera.landingDipMax(), fall * camera.landingDipPerBlock());
        int down = Math.max(1, camera.landingDipTicks());
        int up = Math.max(1, camera.landingRecoverTicks());
        Track<Double> track = Track.scalar()
                .eased(0.0, 0.0, Easing.QUAD_OUT)
                .eased(down, -dip, Easing.QUAD_IN_OUT)
                .eased(down + up, 0.0, Easing.LINEAR)
                .build();
        CameraCue.Builder builder = CameraCue.builder(LANDING).owner(MoveKeys.JUMP)
                .channel(CameraChannel.EYE_HEIGHT, CueCurve.track(track));
        if (hard && camera.hardLandingShake() > 0.0) {
            builder.channel(CameraChannel.SHAKE, CueCurve.constant(camera.hardLandingShake(),
                    camera.hardLandingShakeTicks()));
        }
        return builder.build();
    }

    /** The climb: the catch bounce, the hang's pitch and roll toward the leading hand, the pull's pitch dip. */
    public static CameraCue climb(MantleTuning.Climb climb, double leadingHandSign) {
        MantleTuning.ClimbCamera camera = climb.camera();
        double settle = climb.settleTick() + 1.0;
        double dip = camera.pullPitchTick() + 1.0;
        double back = camera.pullPitchEndTick() + 1.0;
        Track<Double> eye = Track.scalar()
                .eased(0.0, 0.0, Easing.QUAD_OUT)
                .eased(1.0, -camera.bounce(), Easing.QUAD_IN_OUT)
                .eased(3.0, 0.0, Easing.LINEAR)
                .build();
        Track<Double> pitch = Track.scalar()
                .eased(0.0, 0.0, Easing.QUAD_OUT)
                .eased(settle, camera.hangPitch(), Easing.QUAD_IN_OUT)
                .eased(dip, camera.pullPitch(), Easing.QUAD_IN_OUT)
                .eased(back, 0.0, Easing.LINEAR)
                .build();
        Track<Double> roll = Track.scalar()
                .eased(0.0, 0.0, Easing.QUAD_OUT)
                .eased(settle, camera.hangRoll() * leadingHandSign, Easing.QUAD_IN_OUT)
                .eased(back, 0.0, Easing.LINEAR)
                .build();
        return CameraCue.builder(MANTLE).owner(MoveKeys.MANTLE)
                .channel(CameraChannel.EYE_HEIGHT, CueCurve.track(eye))
                .channel(CameraChannel.PITCH, CueCurve.track(pitch))
                .channel(CameraChannel.ROLL, CueCurve.track(roll))
                .build();
    }

    /**
     * The roll's eye height, absolute: 1.62 through T0, an ease-in to the tuck by the end of T2, held to the end of T6,
     * back to standing by the end of T9, then held standing for {@link #EYE_HOLD_TICKS} more so the host camera is
     * standing with the body before the curve lets go.
     */
    public static Track<Double> rollDesignedEye(RollTuning tuning) {
        RollTuning.Camera camera = tuning.camera();
        double standing = camera.eyeRiseTick() + 1.0;
        return Track.scalar()
                .eased(0.0, STAND, Easing.LINEAR)
                .eased(1.0, STAND, Easing.QUAD_IN)
                .eased(camera.eyeLowTick() + 1.0, camera.eyeLow(), Easing.LINEAR)
                .eased(camera.eyeRiseFromTick() + 1.0, camera.eyeLow(), Easing.QUAD_IN_OUT)
                .eased(standing, STAND, Easing.LINEAR)
                .eased(Math.max(standing, tuning.ticks()) + EYE_HOLD_TICKS, STAND, Easing.LINEAR)
                .build();
    }

    /**
     * The roll: a full forward pitch turn (unwound, so it does not clamp at straight down), FOV kick, the designed eye
     * height and look damping. Reduced replaces the turn with a dip.
     */
    public static CameraCue roll(RollTuning tuning) {
        RollTuning.Camera camera = tuning.camera();
        double half = camera.halfTurnTick() + 1.0;
        double end = tuning.ticks();
        Track<Double> turn = Track.scalar()
                .eased(0.0, 0.0, Easing.QUAD_IN)
                .eased(half, camera.turnDegrees() / 2.0, Easing.QUAD_OUT)
                .eased(end, camera.turnDegrees(), Easing.LINEAR)
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
        return CameraCue.builder(ROLL).owner(MoveKeys.ROLL)
                .curve(ChannelCurve.of(CameraChannel.PITCH, CueCurve.track(turn)).unwoundPitch()
                        .withReduced(CueCurve.track(dip)))
                .channel(CameraChannel.FOV, CueCurve.track(fov))
                .curve(ChannelCurve.of(CameraChannel.EYE_HEIGHT, CueCurve.track(rollDesignedEye(tuning)))
                        .absoluteEyeHeight())
                .lookScale(tuning.lookScale(), tuning.ticks())
                .build();
    }

    /** The slide's designed eye height while sliding: eased out to the low height, then held. */
    public static Track<Double> slideDesignedEye(SlideTuning tuning) {
        SlideTuning.Camera camera = tuning.camera();
        return Track.scalar()
                .eased(0.0, STAND, Easing.QUAD_OUT)
                .eased(camera.eyeLowTick() + 1.0, camera.eyeLow(), Easing.LINEAR)
                .build();
    }

    /** The slide's held eye cue, absolute, from T0 until the slide ends. */
    public static CameraCue slideEye(SlideTuning tuning) {
        return CameraCue.builder(SLIDE_EYE).owner(MoveKeys.SLIDE)
                .curve(ChannelCurve.of(CameraChannel.EYE_HEIGHT,
                        CueCurve.sampled(slideDesignedEye(tuning)::sample, Double.POSITIVE_INFINITY))
                        .absoluteEyeHeight())
                .build();
    }

    /**
     * The slide's exit: the eye, absolute, returns from {@code fromDesigned} to standing over {@code eyeReturnTicks},
     * then holds standing for {@link #EYE_HOLD_TICKS} more so the host camera is standing with the body before the
     * curve lets go.
     *
     * @param stayLow the body stays low (no headroom, crawling): the eye goes to the body's own low height and stays
     */
    public static CameraCue slideExit(SlideTuning tuning, double fromDesigned, boolean stayLow) {
        double target = stayLow ? tuning.eyeHeight() : STAND;
        int ticks = Math.max(1, tuning.camera().eyeReturnTicks());
        Track<Double> designed = Track.scalar()
                .eased(0.0, fromDesigned, Easing.QUAD_IN_OUT)
                .eased(ticks, target, Easing.LINEAR)
                .eased(ticks + EYE_HOLD_TICKS, target, Easing.LINEAR)
                .build();
        return CameraCue.builder(SLIDE_EYE).owner(MoveKeys.SLIDE)
                .curve(ChannelCurve.of(CameraChannel.EYE_HEIGHT,
                        CueCurve.sampled(designed::sample, stayLow ? Double.POSITIVE_INFINITY : designed.endTime()))
                        .absoluteEyeHeight())
                .build();
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
