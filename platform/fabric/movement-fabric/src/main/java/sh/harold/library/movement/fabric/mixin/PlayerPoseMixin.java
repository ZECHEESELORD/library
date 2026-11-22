package sh.harold.library.movement.fabric.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sh.harold.library.movement.BodyOverride;
import sh.harold.library.movement.fabric.BodyOverrides;

/** While a body override is live, its render pose replaces vanilla's pose choice (crouch, crawl, stand). */
@Mixin(Player.class)
abstract class PlayerPoseMixin {

    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void creativeLibraryMovement$overridePose(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        BodyOverride override = BodyOverrides.get(self);
        if (override != null) {
            self.setPose(BodyOverrides.pose(override.pose()));
            ci.cancel();
        }
    }
}
