package sh.harold.library.camera.fabric.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sh.harold.library.camera.fabric.client.CameraFrameHook;
import sh.harold.library.camera.rig.CameraState;
import sh.harold.library.camera.rig.LayerState;

/**
 * Applies the rig in {@code Camera.update} right after vanilla stores this frame's FOV: rotation, position and FOV are
 * final, and the view matrix, cull frustum and projection are not built yet, so every channel lands in one place.
 * Additive pitch and roll rotate the camera's own quaternion locally; the entity's rotation and the camera's
 * {@code xRot}/{@code yRot} stay vanilla. Each channel is written only when its offset is non-zero, so an idle rig
 * leaves the camera bit for bit as vanilla built it. The rig gets vanilla's eased eye height for the frame, so an
 * absolute eye-height curve lands exactly on its value.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow @Final private static Vector3fc FORWARDS;
    @Shadow @Final private static Vector3fc UP;
    @Shadow @Final private static Vector3fc LEFT;

    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow private int matrixPropertiesDirty;
    @Shadow private float fov;
    @Shadow private float xRot;
    @Shadow private float yRot;
    @Shadow private Vec3 position;
    @Shadow private boolean detached;
    @Shadow private boolean isPanoramicMode;
    @Shadow private Entity entity;
    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    public abstract float getCameraEntityPartialTicks(DeltaTracker deltaTracker);

    /**
     * While the rig holds an absolute eye height, vanilla's own eye-height easing is held at the body's eye instead of
     * chasing it, so when the target ends the camera is already where the body is and nothing eases in after it. The
     * drawn eye is the rig's target either way: the frame offset is measured from whatever this leaves.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void creativeLibrary$holdEyeHeight(CallbackInfo ci) {
        if (entity instanceof LocalPlayer player && !detached && CameraFrameHook.holdsEyeHeight()) {
            eyeHeight = player.getEyeHeight();
            eyeHeightOld = eyeHeight;
        }
    }

    @Inject(method = "update", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Camera;fov:F",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void creativeLibrary$applyCameraRig(DeltaTracker deltaTracker, CallbackInfo ci) {
        boolean drivable = !detached && !isPanoramicMode && entity instanceof LocalPlayer && position != null;
        Vec3 eye = position;
        float partial = getCameraEntityPartialTicks(deltaTracker);
        CameraState state = CameraFrameHook.frame(drivable,
                eye == null ? 0.0 : eye.x, eye == null ? 0.0 : eye.y, eye == null ? 0.0 : eye.z,
                yRot, xRot, fov, Mth.lerp(partial, eyeHeightOld, eyeHeight), partial);
        if (state == null) {
            return;
        }
        LayerState layers = state.layers();
        double pitchOffset = layers.pitchAdd() + layers.shakePitch();
        double rollOffset = layers.roll() + layers.shakeRoll();
        if (pitchOffset != 0.0 || rollOffset != 0.0) {
            // Local rotations after vanilla's rotationYXZ(PI - yaw, -pitch, 0): the same composition as CameraState.
            if (pitchOffset != 0.0) {
                rotation.rotateX((float) Math.toRadians(-pitchOffset));
            }
            if (rollOffset != 0.0) {
                rotation.rotateZ((float) Math.toRadians(-rollOffset));
            }
            rotation.normalize();
            FORWARDS.rotate(rotation, forwards);
            UP.rotate(rotation, up);
            LEFT.rotate(rotation, left);
            matrixPropertiesDirty |= 3;
        }
        if (layers.eyeHeight() != 0.0) {
            setPosition(eye.add(0.0, layers.eyeHeight(), 0.0));
        }
        if (layers.fovAdd() != 0.0) {
            fov = (float) state.fovDegrees();
        }
    }
}
