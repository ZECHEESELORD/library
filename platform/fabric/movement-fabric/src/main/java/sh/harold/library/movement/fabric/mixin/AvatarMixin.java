package sh.harold.library.movement.fabric.mixin;

import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sh.harold.library.movement.BodyOverride;
import sh.harold.library.movement.fabric.BodyOverrides;

/** A player with a body override (roll 0.9, slide 0.6) gets that hitbox whatever its pose, on both sides. */
@Mixin(Avatar.class)
abstract class AvatarMixin {

    @Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
    private void creativeLibraryMovement$bodyOverride(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        BodyOverride override = BodyOverrides.get((Entity) (Object) this);
        if (override != null) {
            cir.setReturnValue(BodyOverrides.dimensions(override));
        }
    }
}
