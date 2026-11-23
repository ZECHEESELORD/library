package sh.harold.library.movement.core.moves;

import net.kyori.adventure.key.Key;
import sh.harold.library.movement.Aabb;
import sh.harold.library.movement.BodyState;
import sh.harold.library.movement.CameraCommand;
import sh.harold.library.movement.JumpPreset;
import sh.harold.library.movement.MoveContext;
import sh.harold.library.movement.MoveDefinition;
import sh.harold.library.movement.MoveInstance;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.MoveOutput;
import sh.harold.library.movement.MoveSound;
import sh.harold.library.movement.VelocityRule;
import sh.harold.library.movement.core.ReferenceIntegrator;
import sh.harold.library.movement.core.camera.MoveCameras;
import sh.harold.library.movement.core.tuning.JumpTuning;
import sh.harold.library.spatial.Vec3;

import java.util.Optional;

/**
 * 02 section 1, the variable jump, as a passive move. Enabling it hands ground jumps to the core: the host suppresses
 * its own and performs the impulse when the output asks. Presets A and C run the same air code with different numbers;
 * B adds the squat on the ground. Also owns the landing camera.
 *
 * <p>Air tick 1 is the takeoff tick (the tick the impulse is applied), matching {@code design/sims/jump_curves.py}.
 */
public final class JumpMove implements MoveDefinition {

    /** Vanilla's jump cooldown while the key stays held. */
    public static final int VANILLA_JUMP_DELAY = 10;
    public static final double VANILLA_JUMP_POWER = 0.42;

    private final JumpTuning tuning;
    private final int coyoteTicks;
    private final int bufferTicks;

    public JumpMove(JumpTuning tuning, int coyoteTicks) {
        this(tuning, coyoteTicks, 4);
    }

    /**
     * @param bufferTicks the input buffer: a jump launched by a press this recent is fresh and may boost (J2); one
     *                    auto-repeated by holding the key through a landing is vanilla
     */
    public JumpMove(JumpTuning tuning, int coyoteTicks, int bufferTicks) {
        this.tuning = tuning;
        this.coyoteTicks = coyoteTicks;
        this.bufferTicks = Math.max(1, bufferTicks);
    }

    @Override
    public Key key() {
        return MoveKeys.JUMP;
    }

    @Override
    public Kind kind() {
        return Kind.PASSIVE;
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public Optional<MoveInstance> tryStart(MoveContext context) {
        return Optional.of(new Instance(tuning, context.tick(), bufferTicks));
    }

    static final class Instance implements MoveInstance {

        private final JumpTuning tuning;
        private final int bufferTicks;
        private long startTick;
        private boolean fresh;
        private int stumbleLeft;
        private long lastTick = Long.MIN_VALUE;
        private int noJumpDelay;
        private boolean flying;
        private int airTick;
        private boolean held;
        private boolean cutDone;
        private boolean boostCue;
        private boolean charging;
        private int squatTicks;
        private String phase = "idle";

        Instance(JumpTuning tuning, long startTick, int bufferTicks) {
            this.tuning = tuning;
            this.startTick = startTick;
            this.bufferTicks = bufferTicks;
        }

        @Override
        public Key key() {
            return MoveKeys.JUMP;
        }

        @Override
        public long startTick() {
            return startTick;
        }

        @Override
        public String phase() {
            return phase;
        }

        @Override
        public Step tick(MoveContext context, MoveOutput.Builder out) {
            BodyState body = context.body();
            long tick = context.tick();
            if (lastTick != Long.MIN_VALUE && tick != lastTick + 1) {
                stumbleLeft = 0;
            }
            lastTick = tick;
            if (noJumpDelay > 0) {
                noJumpDelay--;
            }
            if (!context.input().jump()) {
                noJumpDelay = 0;
            }
            if (context.landed()) {
                landing(context, out);
            }
            if (flying && (body.onGround() && airTick > 0 && tick > startTick
                    || !context.lastLaunchBy().map(MoveKeys.JUMP::equals).orElse(false))) {
                endFlight(out);
            }
            boolean grounded = body.onGround() || context.coyote();
            JumpPreset preset = tuning.preset();
            if (!flying) {
                if (preset == JumpPreset.B) {
                    charge(context, out, grounded);
                } else if (grounded && !out.jumping()
                        && (context.input().jump() && noJumpDelay == 0 || context.jumpBuffered())) {
                    launch(context, out, VANILLA_JUMP_POWER);
                }
            }
            if (flying) {
                air(context, out);
            } else if (!charging) {
                phase = "idle";
            }
            if (stumbleLeft > 0) {
                stumbleLeft--;
                out.cancelSprint();
                if (!flying && !charging) {
                    phase = "stumble";
                    clampToWalk(context, out);
                }
            }
            return Step.CONTINUE;
        }

        /**
         * J5: while stumbling on the ground, the tick's horizontal speed is at most vanilla's steady walking speed on
         * that ground. When the body's velocity plus this tick's walking input push would exceed it, the velocity is
         * set to that sum scaled down to walking speed and the input push is dropped, so the tick moves exactly at
         * walking speed in the direction it would have moved.
         */
        private void clampToWalk(MoveContext context, MoveOutput.Builder out) {
            BodyState body = context.body();
            if (!body.onGround() || out.jumping()) {
                return;
            }
            double friction = context.world().friction(Aabb.ofBody(body.position(), body.width(), body.height()));
            double walk = ReferenceIntegrator.groundSpeed(friction, false);
            Vec3 next = body.velocity().withY(0.0)
                    .add(ReferenceIntegrator.inputAcceleration(body, context.input(), context.world(), false));
            double speed = next.length();
            if (speed > walk) {
                Vec3 clamped = next.multiply(walk / speed);
                out.horizontal(clamped.x(), clamped.z());
                out.inputScale(0.0);
            }
        }

        private void charge(MoveContext context, MoveOutput.Builder out, boolean grounded) {
            JumpTuning.Charge charge = tuning.charge();
            if (charging) {
                if (!context.body().onGround()) {
                    charging = false;
                    phase = "idle";
                    return;
                }
                if (context.input().jump()) {
                    squatTicks = Math.min(squatTicks + 1, charge.maxTicks());
                    out.inputScale(charge.moveScale());
                    phase = "squat";
                    return;
                }
                charging = false;
                launch(context, out, charge.launchSpeed(squatTicks));
                return;
            }
            boolean wants = context.input().jump() && noJumpDelay == 0 || context.jumpBuffered();
            if (!wants || out.jumping()) {
                return;
            }
            if (context.body().onGround()) {
                if (context.input().jump()) {
                    charging = true;
                    squatTicks = 1;
                    context.consumeJump();
                    out.inputScale(charge.moveScale());
                    phase = "squat";
                } else {
                    launch(context, out, charge.launchSpeed(1));
                }
            } else if (grounded) {
                launch(context, out, charge.launchSpeed(1));
            }
        }

        private void launch(MoveContext context, MoveOutput.Builder out, double speed) {
            out.jump();
            if (speed != VANILLA_JUMP_POWER) {
                out.velocityY(VelocityRule.add(speed - VANILLA_JUMP_POWER));
            }
            fresh = context.jumpPressedWithin(bufferTicks - 1);
            context.consumeJump();
            context.markLaunch(MoveKeys.JUMP, speed);
            noJumpDelay = VANILLA_JUMP_DELAY;
            flying = true;
            airTick = 0;
            startTick = context.tick();
            held = context.input().jump();
            cutDone = false;
            boostCue = false;
        }

        private void air(MoveContext context, MoveOutput.Builder out) {
            airTick++;
            boolean keyDown = context.input().jump();
            if (!keyDown) {
                held = false;
            }
            boolean boosting = held && fresh;
            double rising = airTick == 1 ? VANILLA_JUMP_POWER : context.body().velocity().y();
            phase = rising > 0.0 ? "rise" : "fall";
            switch (tuning.preset()) {
                case A -> {
                    JumpTuning.Boost boost = tuning.boost();
                    if (boosting && rising > 0.0 && airTick > boost.freeTicks() && airTick <= boost.sustainToTick()) {
                        out.gravity(boost.gravity());
                        phase = "boost";
                    }
                    if (boosting && airTick == tuning.camera().boostFovMinHoldTicks() && rising > 0.0) {
                        out.camera(new CameraCommand.Start(MoveCameras.jumpBoost(tuning.camera())));
                        boostCue = true;
                    }
                    if (boosting && airTick == tuning.sound().boostTick()) {
                        out.sound(MoveSound.event(tuning.sound().boostSound(), context.body().position(),
                                tuning.sound().boostVolume(), tuning.sound().boostPitch()));
                    }
                }
                case C -> {
                    if (fresh && !held && !cutDone && rising > 0.0) {
                        cutDone = true;
                        out.gravity(context.gravity() + (1.0 - tuning.cut().factor()) * rising);
                        phase = "cut";
                    }
                }
                case B -> {
                }
            }
            if (boostCue && rising <= 0.0) {
                out.camera(new CameraCommand.Release(MoveCameras.JUMP_BOOST));
                boostCue = false;
            }
        }

        private void endFlight(MoveOutput.Builder out) {
            if (boostCue) {
                out.camera(new CameraCommand.Release(MoveCameras.JUMP_BOOST));
                boostCue = false;
            }
            flying = false;
            airTick = 0;
            held = false;
        }

        private void landing(MoveContext context, MoveOutput.Builder out) {
            JumpTuning.Camera camera = tuning.camera();
            double fall = context.landingFall();
            if (camera.landingDip(fall) > 0.0) {
                out.camera(new CameraCommand.Start(MoveCameras.landing(camera, fall)));
            }
            if (fall + 1.0e-9 >= tuning.stumble().fall() && tuning.stumble().ticks() > 0) {
                stumbleLeft = tuning.stumble().ticks();
            }
        }

        /** The air tick of the live jump, 0 when not in one. */
        int airTick() {
            return flying ? airTick : 0;
        }

        @Override
        public MoveInstance copy() {
            Instance copy = new Instance(tuning, startTick, bufferTicks);
            copy.fresh = fresh;
            copy.stumbleLeft = stumbleLeft;
            copy.lastTick = lastTick;
            copy.noJumpDelay = noJumpDelay;
            copy.flying = flying;
            copy.airTick = airTick;
            copy.held = held;
            copy.cutDone = cutDone;
            copy.boostCue = boostCue;
            copy.charging = charging;
            copy.squatTicks = squatTicks;
            copy.phase = phase;
            return copy;
        }
    }
}
