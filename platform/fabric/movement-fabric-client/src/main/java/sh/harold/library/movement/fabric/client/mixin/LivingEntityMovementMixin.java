package sh.harold.library.movement.fabric.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sh.harold.library.movement.fabric.client.ClientMovement;

/**
 * Applies the movement core's output to the local player inside vanilla's own tick, in the order the reference
 * integrator models: the engine runs at the head of {@code aiStep} (after the input is read), vanilla's jump is
 * suppressed while the core owns jumping, and just before {@code travel} the core's jump, velocity rules, friction,
 * gravity and input scale apply. Every hook is a no-op for any other entity and while no moveset is enabled.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMovementMixin {

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void creativeLibraryMovement$step(CallbackInfo ci) {
        ClientMovement.beforeAiStep((LivingEntity) (Object) this);
    }

    @WrapWithCondition(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;jumpFromGround()V"))
    private boolean creativeLibraryMovement$vanillaJump(LivingEntity self) {
        return !ClientMovement.ownsJump(self);
    }

    @Inject(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;travel(Lnet/minecraft/world/phys/Vec3;)V"))
    private void creativeLibraryMovement$beforeTravel(CallbackInfo ci) {
        ClientMovement.beforeTravel((LivingEntity) (Object) this);
    }

    @ModifyArg(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;travel(Lnet/minecraft/world/phys/Vec3;)V"))
    private Vec3 creativeLibraryMovement$inputScale(Vec3 input) {
        return ClientMovement.travelInput((LivingEntity) (Object) this, input);
    }

    @Inject(method = "aiStep", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/world/entity/LivingEntity;travel(Lnet/minecraft/world/phys/Vec3;)V"))
    private void creativeLibraryMovement$afterTravel(CallbackInfo ci) {
        ClientMovement.afterTravel((LivingEntity) (Object) this);
    }

    @Inject(method = "getEffectiveGravity", at = @At("HEAD"), cancellable = true)
    private void creativeLibraryMovement$gravity(CallbackInfoReturnable<Double> cir) {
        Double gravity = ClientMovement.gravity((LivingEntity) (Object) this);
        if (gravity != null) {
            cir.setReturnValue(gravity);
        }
    }
}
