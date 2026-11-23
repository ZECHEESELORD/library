package sh.harold.library.camera.fabric.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sh.harold.library.camera.fabric.client.CameraFrameHook;
import sh.harold.library.camera.rig.ArmPose;

/**
 * Applies the rig's first-person arm poses in {@code submitArmWithItem}, right after vanilla pushes the hand's pose and
 * before any of its own transforms, so the pose moves the arm and its item together about the hand's rest position
 * ({@code ITEM_POS}: 0.56 side, -0.52, -0.72). Translation and the y and z rotations are mirrored for the left-side
 * arm. An identity pose writes nothing, so with no arm cue live the hands are drawn exactly as vanilla draws them.
 *
 * <p>Vanilla draws no off arm when the off hand is empty. While the off arm's pose is non-identity this draws it, with
 * the same equip and swing inputs vanilla passed, so a two-handed grip shows both hands; the pose stack is still the
 * one vanilla pops at the end of the method.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsMixin {

    private static final float PIVOT_X = 0.56f;
    private static final float PIVOT_Y = -0.52f;
    private static final float PIVOT_Z = -0.72f;

    @Shadow
    protected abstract void renderPlayerArm(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                            float equipProgress, float swingProgress, HumanoidArm arm,
                                            PlayerRenderState playerState);

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
    private void creativeLibrary$applyArmPose(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state,
                                              float frameInterp, float xRot, InteractionHand hand,
                                              float swingProgress, ItemStack stack, float equipProgress,
                                              PoseStack poseStack, SubmitNodeCollector collector, int light,
                                              CallbackInfo ci) {
        boolean main = hand == InteractionHand.MAIN_HAND;
        ArmPose pose = CameraFrameHook.armPose(main);
        if (pose.isIdentity()) {
            return;
        }
        AvatarRenderState avatar = playerState.avatarRenderState;
        if (avatar == null) {
            return;
        }
        HumanoidArm arm = main ? avatar.mainArm : avatar.mainArm.getOpposite();
        float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        float pivotX = side * PIVOT_X;
        poseStack.translate(pivotX + side * (float) pose.x(), PIVOT_Y + (float) pose.y(),
                PIVOT_Z + (float) pose.z());
        if (pose.rotX() != 0.0) {
            poseStack.rotateDegrees(Axis.XP, (float) pose.rotX());
        }
        if (pose.rotY() != 0.0) {
            // Positive rotY turns the arm's forward end outward: toward +x on the right, -x on the left.
            poseStack.rotateDegrees(Axis.YP, -side * (float) pose.rotY());
        }
        if (pose.rotZ() != 0.0) {
            // Positive rotZ tilts the arm's top outward.
            poseStack.rotateDegrees(Axis.ZP, -side * (float) pose.rotZ());
        }
        poseStack.translate(-pivotX, -PIVOT_Y, -PIVOT_Z);
        if (!main && stack.isEmpty() && !avatar.isInvisible) {
            renderPlayerArm(poseStack, collector, light, equipProgress, swingProgress, arm, playerState);
        }
    }
}
