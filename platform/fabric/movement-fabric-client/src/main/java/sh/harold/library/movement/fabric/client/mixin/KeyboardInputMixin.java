package sh.harold.library.movement.fabric.client.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sh.harold.library.movement.InputFrame;
import sh.harold.library.movement.fabric.MinecraftBodies;
import sh.harold.library.movement.fabric.client.ClientMovement;

/** The test hook: while a simulated input script plays, its frame replaces the keyboard for that tick. */
@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin extends ClientInput {

    @Inject(method = "tick", at = @At("TAIL"))
    private void creativeLibraryMovement$simulatedInput(CallbackInfo ci) {
        InputFrame frame = ClientMovement.nextSimulatedFrame();
        if (frame != null) {
            this.keyPresses = MinecraftBodies.input(frame);
            this.moveVector = new Vec2((float) frame.strafeImpulse(), (float) frame.forwardImpulse()).normalized();
        }
    }
}
