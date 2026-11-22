package sh.harold.library.movement.fabric.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sh.harold.library.movement.fabric.client.ClientMovement;

/** Draws the camera at the smoothed position while a rejected claim's correction closes. */
@Mixin(Camera.class)
abstract class CameraOffsetMixin {

    @Shadow
    public abstract Vec3 position();

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void creativeLibraryMovement$correctionOffset(float partial, CallbackInfo ci) {
        sh.harold.library.spatial.Vec3 offset = ClientMovement.cameraOffset(partial);
        if (offset != null) {
            setPosition(position().add(offset.x(), offset.y(), offset.z()));
        }
    }
}
