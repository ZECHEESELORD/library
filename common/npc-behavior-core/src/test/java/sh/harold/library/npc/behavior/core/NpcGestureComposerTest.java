package sh.harold.library.npc.behavior.core;

import org.junit.jupiter.api.Test;
import sh.harold.library.entity.EntityPose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcGestureComposerTest {

    @Test
    void allVanillaSafeGestureCurvesMoveAndReturnToBase() {
        NpcRenderFrame base = NpcRenderFrame.standing(0.0f, 0.0f);
        for (NpcRenderAnimation.Type type : new NpcRenderAnimation.Type[]{
                NpcRenderAnimation.Type.NOD,
                NpcRenderAnimation.Type.HEAD_FLICK_UP,
                NpcRenderAnimation.Type.HEAD_FLICK_DOWN,
                NpcRenderAnimation.Type.DOUBLE_TAKE,
                NpcRenderAnimation.Type.LOOK_AROUND,
                NpcRenderAnimation.Type.CROUCH_PULSE,
                NpcRenderAnimation.Type.LEAN_FORWARD_PROXY,
                NpcRenderAnimation.Type.LEAN_BACK_PROXY
        }) {
            NpcGestureComposer composer = new NpcGestureComposer();
            composer.start(new NpcRenderAnimation(type, 8), 10L);
            NpcRenderFrame middle = composer.compose(base, 12L);
            assertNotEquals(base, middle, type + " should alter a vanilla frame");
            assertTrue(Math.abs(NpcGazeController.shortestDelta(middle.bodyYaw(), middle.headYaw())) <= 55.0f);
            assertTrue(middle.pitch() >= -25.0f && middle.pitch() <= 35.0f);
            assertEquals(base, composer.compose(base, 18L), type + " should restore the current underlying frame");
        }
    }

    @Test
    void crouchAndLeanForwardAreExplicitPoseProxies() {
        NpcRenderFrame base = NpcRenderFrame.standing(0.0f, 0.0f);
        NpcGestureComposer crouch = new NpcGestureComposer();
        crouch.start(new NpcRenderAnimation(NpcRenderAnimation.Type.CROUCH_PULSE, 10), 0L);
        assertEquals(EntityPose.CROUCHING, crouch.compose(base, 5L).pose());

        NpcGestureComposer lean = new NpcGestureComposer();
        lean.start(new NpcRenderAnimation(NpcRenderAnimation.Type.LEAN_FORWARD_PROXY, 10), 0L);
        assertEquals(EntityPose.CROUCHING, lean.compose(base, 5L).pose());
    }
}
