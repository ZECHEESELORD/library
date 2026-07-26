package sh.harold.library.npc.behavior.core;

import sh.harold.library.entity.EntityPose;

import java.util.Objects;

/**
 * Applies short, vanilla-safe gesture curves to a complete mannequin frame.
 * It deliberately uses only head/body yaw, pitch and crouching; there is no
 * facial animation, roll, arbitrary limb joint, or real lean channel.
 */
final class NpcGestureComposer {
    private Active active;

    void start(NpcRenderAnimation animation, long tick) {
        Objects.requireNonNull(animation, "animation");
        active = new Active(animation, tick, tick + animation.durationTicks());
    }

    NpcRenderFrame compose(NpcRenderFrame base, long tick) {
        Objects.requireNonNull(base, "base");
        Active current = active;
        if (current == null) {
            return base;
        }
        if (!hasFrameMotion(current.animation().type())) {
            active = null;
            return base;
        }
        if (tick >= current.expiresAt()) {
            active = null;
            return base;
        }

        double progress = Math.max(0.0, Math.min(
                1.0,
                (tick - current.startedAt()) / (double) current.animation().durationTicks()
        ));
        double pulse = Math.sin(Math.PI * progress);
        double alternating = Math.sin(Math.PI * 2.0 * progress);
        float bodyYaw = base.bodyYaw();
        float headYaw = base.headYaw();
        float pitch = base.pitch();
        EntityPose pose = base.pose();

        switch (current.animation().type()) {
            case NOD -> pitch += (float) (alternating * 7.0);
            case HEAD_FLICK_UP -> pitch -= (float) (pulse * 11.0);
            case HEAD_FLICK_DOWN -> pitch += (float) (pulse * 12.0);
            case DOUBLE_TAKE -> headYaw += (float) (alternating * 20.0);
            case LOOK_AROUND -> {
                headYaw += (float) (alternating * 32.0);
                bodyYaw += (float) (alternating * 8.0);
            }
            case CROUCH_PULSE -> {
                if (progress >= 0.18 && progress <= 0.82) {
                    pose = EntityPose.CROUCHING;
                }
                pitch += (float) (pulse * 4.0);
            }
            case LEAN_FORWARD_PROXY -> {
                if (progress >= 0.18 && progress <= 0.82) {
                    pose = EntityPose.CROUCHING;
                }
                pitch += (float) (pulse * 10.0);
            }
            case LEAN_BACK_PROXY -> pitch -= (float) (pulse * 9.0);
            default -> {
                return base;
            }
        }

        float relativeHead = NpcGazeController.shortestDelta(bodyYaw, headYaw);
        relativeHead = clamp(
                relativeHead,
                -NpcPersonalityMotion.MAXIMUM_HEAD_YAW_FROM_BODY,
                NpcPersonalityMotion.MAXIMUM_HEAD_YAW_FROM_BODY
        );
        headYaw = NpcGazeController.wrapDegrees(bodyYaw + relativeHead);
        pitch = clamp(
                pitch,
                NpcPersonalityMotion.MAXIMUM_UP_PITCH,
                NpcPersonalityMotion.MAXIMUM_DOWN_PITCH
        );
        return new NpcRenderFrame(
                NpcGazeController.wrapDegrees(bodyYaw),
                headYaw,
                pitch,
                pose,
                base.equipment(),
                base.usingMainHand(),
                base.usingOffHand()
        );
    }

    boolean active() {
        return active != null && hasFrameMotion(active.animation().type());
    }

    void clear() {
        active = null;
    }

    private static boolean hasFrameMotion(NpcRenderAnimation.Type type) {
        return switch (type) {
            case NOD, HEAD_FLICK_UP, HEAD_FLICK_DOWN, DOUBLE_TAKE, LOOK_AROUND,
                    CROUCH_PULSE, LEAN_FORWARD_PROXY, LEAN_BACK_PROXY -> true;
            default -> false;
        };
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record Active(NpcRenderAnimation animation, long startedAt, long expiresAt) {
    }
}
