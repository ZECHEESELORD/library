package sh.harold.library.npc.behavior.core;

import sh.harold.library.npc.behavior.NpcPersonalityPreset;
import sh.harold.library.npc.behavior.NpcPersonalityTuning;
import sh.harold.library.npc.behavior.NpcSustainMode;

import java.util.Objects;

/** Stateful head/body controller constrained to what a vanilla mannequin can express. */
public final class NpcGazeController {

    private final float homeYaw;
    private final float homePitch;
    private float bodyYaw;
    private float headYaw;
    private float pitch;
    private NpcAttentionStack.GazeTarget target;
    private long acquiredAtTick;

    public NpcGazeController(float homeYaw, float homePitch) {
        requireFinite(homeYaw, "homeYaw");
        requireFinite(homePitch, "homePitch");
        this.homeYaw = wrapDegrees(homeYaw);
        this.homePitch = clampPitch(homePitch);
        this.bodyYaw = this.homeYaw;
        this.headYaw = this.homeYaw;
        this.pitch = this.homePitch;
    }

    public void target(NpcAttentionStack.GazeTarget target, long tick) {
        this.target = Objects.requireNonNull(target, "target");
        this.acquiredAtTick = tick;
    }

    /** Updates a moving target without replaying acquisition expression. */
    public void retarget(NpcAttentionStack.GazeTarget target) {
        this.target = Objects.requireNonNull(target, "target");
    }

    public void home(long tick) {
        target(new NpcAttentionStack.GazeTarget(homeYaw, homePitch), tick);
    }

    public State tick(
            long tick,
            NpcPersonalityPreset preset,
            NpcPersonalityTuning tuning,
            NpcSustainMode sustainMode
    ) {
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(tuning, "tuning");
        Objects.requireNonNull(sustainMode, "sustainMode");
        if (target == null) {
            home(tick);
        }

        NpcPersonalityMotion.Signature signature = NpcPersonalityMotion.signature(preset).tuned(tuning);
        long elapsed = Math.max(0L, tick - acquiredAtTick);
        float targetYaw = target.yaw();
        float targetPitch = target.pitch() + signature.pitchBiasDegrees();

        if (signature.acquisitionOvershootDegrees() != 0.0f && elapsed < 5L) {
            float direction = Math.signum(shortestDelta(bodyYaw, target.yaw()));
            targetYaw += direction * signature.acquisitionOvershootDegrees() * ((5.0f - elapsed) / 5.0f);
        }

        if (sustainMode == NpcSustainMode.NATURAL && signature.naturalMicroGlanceDegrees() != 0.0f) {
            double phase = ((tick + preset.ordinal() * 13L) % 47L) / 47.0 * Math.PI * 2.0;
            targetYaw += (float) Math.sin(phase) * signature.naturalMicroGlanceDegrees();
        }

        float targetRelativeToBody = shortestDelta(bodyYaw, targetYaw);
        if (elapsed >= signature.bodyFollowDelayTicks()
                && Math.abs(targetRelativeToBody) > NpcPersonalityMotion.BODY_FOLLOW_THRESHOLD) {
            float desiredBody = targetYaw - Math.copySign(NpcPersonalityMotion.BODY_FOLLOW_THRESHOLD, targetRelativeToBody);
            bodyYaw = moveAngle(bodyYaw, desiredBody, signature.bodyDegreesPerTick());
        }

        float clampedRelative = clamp(
                shortestDelta(bodyYaw, targetYaw),
                -NpcPersonalityMotion.MAXIMUM_HEAD_YAW_FROM_BODY,
                NpcPersonalityMotion.MAXIMUM_HEAD_YAW_FROM_BODY
        );
        float desiredHead = wrapDegrees(bodyYaw + clampedRelative);
        headYaw = moveAngle(headYaw, desiredHead, signature.headDegreesPerTick());
        // Body movement can invalidate the previous relative angle by a tiny amount.
        headYaw = wrapDegrees(bodyYaw + clamp(
                shortestDelta(bodyYaw, headYaw),
                -NpcPersonalityMotion.MAXIMUM_HEAD_YAW_FROM_BODY,
                NpcPersonalityMotion.MAXIMUM_HEAD_YAW_FROM_BODY
        ));
        pitch = moveLinear(pitch, clampPitch(targetPitch), signature.pitchDegreesPerTick());
        return state();
    }

    public State state() {
        return new State(bodyYaw, headYaw, pitch);
    }

    private static float moveAngle(float current, float target, float maximumDelta) {
        float delta = shortestDelta(current, target);
        return wrapDegrees(current + clamp(delta, -maximumDelta, maximumDelta));
    }

    private static float moveLinear(float current, float target, float maximumDelta) {
        return current + clamp(target - current, -maximumDelta, maximumDelta);
    }

    static float shortestDelta(float from, float to) {
        return wrapDegrees(to - from);
    }

    static float wrapDegrees(float value) {
        float wrapped = value % 360.0f;
        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        }
        if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }

    private static float clampPitch(float value) {
        return clamp(
                value,
                NpcPersonalityMotion.MAXIMUM_UP_PITCH,
                NpcPersonalityMotion.MAXIMUM_DOWN_PITCH
        );
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    public record State(float bodyYaw, float headYaw, float pitch) {
    }
}
