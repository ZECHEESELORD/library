package sh.harold.library.devtools.fabric.client.mixin;

import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sh.harold.library.devtools.fabric.client.CaptureClock;

/**
 * While a capture clock runs, replaces the wall-clock game-time step: vanilla sets {@code deltaTicks} from elapsed
 * milliseconds, adds it to {@code deltaTickResidual} and returns the whole ticks to run, keeping the fraction. The
 * clock sets the same fields from its exact frame times instead.
 */
@Mixin(DeltaTracker.Timer.class)
public abstract class DeltaTrackerTimerMixin {

    @Shadow private float deltaTicks;
    @Shadow private float deltaTickResidual;
    @Shadow private long lastMs;

    @Inject(method = "advanceGameTime(J)I", at = @At("HEAD"), cancellable = true)
    private void creativeLibrary$captureClock(long timeMs, CallbackInfoReturnable<Integer> cir) {
        CaptureClock.Step step = CaptureClock.nextFrame();
        if (step == null) {
            return;
        }
        deltaTicks = step.deltaTicks();
        deltaTickResidual = step.partialTick();
        lastMs = timeMs;
        cir.setReturnValue(step.ticks());
    }
}
