package sh.harold.library.camera.fabric.client.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import sh.harold.library.camera.fabric.client.CameraFrameHook;

/**
 * Scales mouse look by the rig's look-input scale (for example 20% during a roll). {@code turnPlayer} hands the
 * frame's accumulated yaw and pitch deltas to {@code LocalPlayer.turn(DD)V} once; both are multiplied. At scale 1
 * the arguments come back unchanged.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0)
    private double creativeLibrary$scaleYawInput(double yaw) {
        double scale = CameraFrameHook.lookScale();
        return scale == 1.0 ? yaw : yaw * scale;
    }

    @ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1)
    private double creativeLibrary$scalePitchInput(double pitch) {
        double scale = CameraFrameHook.lookScale();
        return scale == 1.0 ? pitch : pitch * scale;
    }
}
