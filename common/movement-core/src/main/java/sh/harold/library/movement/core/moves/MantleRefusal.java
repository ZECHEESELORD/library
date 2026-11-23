package sh.harold.library.movement.core.moves;

import net.kyori.adventure.key.Key;
import sh.harold.library.movement.CameraCommand;
import sh.harold.library.movement.MoveContext;
import sh.harold.library.movement.MoveDefinition;
import sh.harold.library.movement.MoveInstance;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.MoveOutput;
import sh.harold.library.movement.MoveSound;
import sh.harold.library.movement.core.camera.MoveCameras;
import sh.harold.library.movement.core.tuning.MantleTuning;
import sh.harold.library.spatial.Vec3;

import java.util.Optional;

/**
 * MA12: a mantle that would be tried but finds its ledge blocked (no headroom, no depth on top, no room to rise) shows
 * it: once per jump press, the arms slap the wall for {@code refusal.ticks} and the block's hit sound plays. A passive
 * with no motion fields, so the body moves exactly as it would without it.
 */
public final class MantleRefusal implements MoveDefinition {

    /**
     * The tick of the last refusal, remembered until the body next stands on the ground: the wall slide does not start
     * while it is set (ruling on refused jumps: no wall slide for the rest of that airtime; a wall kick still can).
     */
    public static final Key REFUSED = Key.key(MoveKeys.NAMESPACE, "mantle/refused");


    private final MantleTuning tuning;
    private final int bufferTicks;

    public MantleRefusal(MantleTuning tuning, int bufferTicks) {
        this.tuning = tuning;
        this.bufferTicks = bufferTicks;
    }

    @Override
    public Key key() {
        return MoveKeys.MANTLE_REFUSAL;
    }

    @Override
    public Kind kind() {
        return Kind.PASSIVE;
    }

    @Override
    public int priority() {
        return 15;
    }

    @Override
    public Optional<MoveInstance> tryStart(MoveContext context) {
        return Optional.of(new Instance(tuning, bufferTicks, context.tick()));
    }

    static final class Instance implements MoveInstance {

        private final MantleTuning tuning;
        private final int bufferTicks;
        private long startTick;
        private long slappedPress = Long.MIN_VALUE;
        private int left;

        Instance(MantleTuning tuning, int bufferTicks, long startTick) {
            this.tuning = tuning;
            this.bufferTicks = bufferTicks;
            this.startTick = startTick;
        }

        @Override
        public Key key() {
            return MoveKeys.MANTLE_REFUSAL;
        }

        @Override
        public long startTick() {
            return startTick;
        }

        @Override
        public String phase() {
            return left > 0 ? "slap" : "idle";
        }

        @Override
        public Step tick(MoveContext context, MoveOutput.Builder out) {
            if (left > 0) {
                left--;
            }
            long press = context.lastJumpPress();
            if (left == 0 && press != slappedPress && context.jumpPressedWithin(bufferTicks - 1)) {
                MantleMove.Attempt attempt = MantleMove.attempt(context, tuning);
                if (attempt != null && attempt.check().blocked()) {
                    slappedPress = press;
                    startTick = context.tick();
                    context.remember(REFUSED, context.tick());
                    left = tuning.refusal().ticks();
                    Probe.LedgeCheck check = attempt.check();
                    Vec3 wall = context.body().position().add(attempt.direction().multiply(0.3 + check.gap() + 0.05))
                            .withY(check.top() - 0.5);
                    out.sound(new MoveSound(MoveSound.Source.BLOCK_HIT, "minecraft:block.stone.hit", wall, 1.0f,
                            (float) tuning.refusal().hitPitch()));
                    out.camera(new CameraCommand.Start(MoveCameras.mantleRefusal(tuning.refusal())));
                }
            }
            return Step.CONTINUE;
        }

        @Override
        public MoveInstance copy() {
            Instance copy = new Instance(tuning, bufferTicks, startTick);
            copy.slappedPress = slappedPress;
            copy.left = left;
            return copy;
        }
    }
}
