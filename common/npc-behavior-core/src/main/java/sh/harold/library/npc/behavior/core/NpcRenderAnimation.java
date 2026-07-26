package sh.harold.library.npc.behavior.core;

import java.util.Objects;

public record NpcRenderAnimation(Type type, int durationTicks) {
    public NpcRenderAnimation {
        type = Objects.requireNonNull(type, "type");
        if (durationTicks < 1) {
            throw new IllegalArgumentException("durationTicks must be positive");
        }
    }

    public enum Type {
        SWING_MAIN_HAND,
        SWING_OFF_HAND,
        USE_MAIN_HAND,
        USE_OFF_HAND,
        NOD,
        HEAD_FLICK_UP,
        HEAD_FLICK_DOWN,
        WAVE,
        DOUBLE_TAKE,
        LOOK_AROUND,
        CROUCH_PULSE,
        LEAN_FORWARD_PROXY,
        LEAN_BACK_PROXY
    }
}
