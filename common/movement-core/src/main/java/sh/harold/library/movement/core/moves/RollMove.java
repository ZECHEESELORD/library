package sh.harold.library.movement.core.moves;

import net.kyori.adventure.key.Key;
import sh.harold.library.movement.BodyOverride;
import sh.harold.library.movement.BodyState;
import sh.harold.library.movement.CameraCommand;
import sh.harold.library.movement.ClaimCheck;
import sh.harold.library.movement.ClaimContext;
import sh.harold.library.movement.MoveClaim;
import sh.harold.library.movement.MoveContext;
import sh.harold.library.movement.MoveDefinition;
import sh.harold.library.movement.MoveEnd;
import sh.harold.library.movement.MoveEvent;
import sh.harold.library.movement.MoveInstance;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.MoveOutput;
import sh.harold.library.movement.MoveSound;
import sh.harold.library.movement.core.camera.MoveCameras;
import sh.harold.library.movement.core.tuning.RollTuning;
import sh.harold.library.spatial.Vec3;

import java.util.Optional;

/**
 * 02 section 3. A fall of 3 blocks or more and a sneak press from 6 ticks before touchdown to 2 after. {@code T0} is
 * the touchdown tick (the tick the core sees the body on the ground), or the press tick for a late press. Fall damage is
 * reduced by 10 blocks once the server accepts the claim.
 */
public final class RollMove implements MoveDefinition {

    private final RollTuning tuning;
    private final int graceTicks;

    public RollMove(RollTuning tuning, int graceTicks) {
        this.tuning = tuning;
        this.graceTicks = graceTicks;
    }

    @Override
    public Key key() {
        return MoveKeys.ROLL;
    }

    @Override
    public Kind kind() {
        return Kind.EXCLUSIVE;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public Optional<MoveInstance> tryStart(MoveContext context) {
        BodyState body = context.body();
        if (!body.onGround() || context.landingFall() + 1.0e-9 < tuning.minFall()) {
            return Optional.empty();
        }
        long sinceLanding = context.tick() - context.landingTick();
        int offset;
        if (context.landed()) {
            if (!context.sneakPressedWithin(tuning.windowBefore())) {
                return Optional.empty();
            }
            offset = (int) (context.lastSneakPress() - context.tick());
        } else if (sinceLanding > 0 && sinceLanding <= tuning.windowAfter() && context.sneakPressed()) {
            offset = (int) sinceLanding;
        } else {
            return Optional.empty();
        }
        Vec3 motion = context.lastMotion().withY(0.0);
        Vec3 direction = motion.lengthSquared() > 1.0e-6 ? motion.normalize() : body.facing();
        double speed = Math.max(motion.length(), tuning.minSpeed()) * tuning.boost();
        return Optional.of(new Instance(tuning, context.tick(), direction, speed, context.landingFall(), offset));
    }

    @Override
    public ClaimCheck check(MoveClaim claim, ClaimContext context) {
        double slack = 0.75;
        if (claim.fallDistance() + slack < tuning.minFall()) {
            return ClaimCheck.reject("fall too short");
        }
        long window = tuning.windowAfter() + graceTicks + context.waitedTicks();
        if (context.lastLandingFall() + slack < tuning.minFall()) {
            return context.waitedTicks() < 2 ? ClaimCheck.defer("no landing yet") : ClaimCheck.reject("no fall seen");
        }
        if (context.ticksSinceLanding() > window + tuning.windowBefore()) {
            return ClaimCheck.reject("landing too long ago");
        }
        if (context.ticksSinceSneakPress() > tuning.windowBefore() + tuning.windowAfter() + graceTicks
                + context.waitedTicks()) {
            return ClaimCheck.reject("no sneak press");
        }
        return ClaimCheck.ACCEPT;
    }

    static final class Instance implements MoveInstance {

        private final RollTuning tuning;
        private final long startTick;
        private final Vec3 direction;
        private final double speed;
        private final double fall;
        private final int pressOffset;
        private int k = -1;
        private boolean jumpQueued;

        Instance(RollTuning tuning, long startTick, Vec3 direction, double speed, double fall, int pressOffset) {
            this.tuning = tuning;
            this.startTick = startTick;
            this.direction = direction;
            this.speed = speed;
            this.fall = fall;
            this.pressOffset = pressOffset;
        }

        @Override
        public Key key() {
            return MoveKeys.ROLL;
        }

        @Override
        public long startTick() {
            return startTick;
        }

        @Override
        public String phase() {
            if (k < 1) {
                return "touchdown";
            } else if (k < 3) {
                return "tuck";
            } else if (k < 7) {
                return "roll";
            } else if (k < tuning.ticks()) {
                return "rise";
            }
            return "recover";
        }

        @Override
        public MoveClaim claim(MoveClaim base) {
            return base.withFallDistance(fall).withPressTickOffset(pressOffset);
        }

        /** The locked speed for T0 to T9. */
        double speed() {
            return speed;
        }

        @Override
        public Step tick(MoveContext context, MoveOutput.Builder out) {
            k++;
            BodyState body = context.body();
            int ticks = tuning.ticks();
            if (k == 0) {
                out.event(new MoveEvent.FallDamageModifier(tuning.damageReduction()));
                out.camera(new CameraCommand.Start(MoveCameras.roll(tuning)));
                out.sound(MoveSound.event(tuning.sound().start(), body.position(), 1.0, tuning.sound().startPitch()));
            }
            if (tuning.sound().stepTicks().contains(k)) {
                out.sound(new MoveSound(MoveSound.Source.BLOCK_STEP, "minecraft:block.stone.step",
                        body.position().add(new Vec3(0, -0.2, 0)), 0.6f, 1.0f));
            }
            if (k < ticks) {
                if (k >= tuning.jumpBufferFrom() && context.jumpPressed()) {
                    jumpQueued = true;
                }
                out.horizontal(direction.x() * speed, direction.z() * speed)
                        .discardFriction()
                        .inputScale(0.0)
                        .lookScale(tuning.lookScale())
                        .body(new BodyOverride(tuning.height(), tuning.eyeHeight(), BodyOverride.RenderPose.CROUCHING));
                return Step.CONTINUE;
            }
            boolean standing = Probe.fits(context.world(), body, BodyState.PLAYER_HEIGHT);
            if (!standing) {
                out.body(new BodyOverride(tuning.height(), tuning.eyeHeight(), BodyOverride.RenderPose.CROUCHING));
            }
            if (k == ticks && standing && body.onGround() && (jumpQueued || context.jumpBuffered())) {
                context.consumeJump();
                out.jump().horizontal(direction.x() * speed, direction.z() * speed);
                context.markLaunch(MoveKeys.ROLL, 0.42);
                return Step.end(MoveEnd.EndReason.COMPLETED);
            }
            int decay = tuning.decayTicks();
            int into = k - ticks + 1;
            if (into > decay) {
                return standing ? Step.end(MoveEnd.EndReason.COMPLETED) : Step.CONTINUE;
            }
            double base = speed / tuning.boost();
            double floor = base + (speed - base) * (1.0 - (double) into / (decay + 1));
            Vec3 velocity = body.velocity().withY(0.0);
            double current = velocity.length();
            if (current < floor) {
                Vec3 heading = current > 1.0e-4 ? velocity.normalize() : direction;
                out.horizontal(heading.x() * floor, heading.z() * floor);
            }
            return Step.CONTINUE;
        }

        @Override
        public MoveInstance copy() {
            Instance copy = new Instance(tuning, startTick, direction, speed, fall, pressOffset);
            copy.k = k;
            copy.jumpQueued = jumpQueued;
            return copy;
        }
    }
}
