package sh.harold.library.movement.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import sh.harold.library.movement.fabric.ServerMovement;

/**
 * Routes a server player's landing through the movement service, which holds the damage for the roll window and
 * applies it reduced when a roll claim is accepted, or in full when none comes.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityFallMixin {

    @WrapMethod(method = "causeFallDamage")
    private boolean creativeLibraryMovement$rollWindow(double fallDistance, float multiplier, DamageSource source,
                                                       Operation<Boolean> original) {
        Boolean held = ServerMovement.interceptFall((LivingEntity) (Object) this, fallDistance, multiplier, source);
        return held != null ? held : original.call(fallDistance, multiplier, source);
    }
}
