package sh.harold.library.movement.core.moves;

import net.kyori.adventure.key.Key;
import sh.harold.library.movement.BodyState;
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
import sh.harold.library.movement.VelocityRule;
import sh.harold.library.movement.core.ReferenceIntegrator;
import sh.harold.library.movement.core.tuning.WallJumpTuning;
import sh.harold.library.spatial.Vec3;

import java.util.Optional;

/**
 * 02 section 5 with rulings W1 and W2, the wall slide: while falling and pushing into a wall, fall speed is braked (at
 * most {@code maxBrake} per tick) toward a cap that ramps from 0.12 to 0.40 over 24 ticks, with dust and a step sound
 * every 4 and a small roll away from the wall. A passive move with its own claim. Fall distance is never reset: each
 * slide tick caps it at the height a fall from rest needs to reach the current fall speed, so a slide that really
 * slowed the body forgives the fall before it and a brush that did not keeps it.
 */
public final class WallSlideMove implements MoveDefinition {

    private final WallJumpTuning tuning;

    public WallSlideMove(WallJumpTuning tuning) {
        this.tuning = tuning;
    }

    @Override
    public Key key() {
        return MoveKeys.WALL_SLIDE;
    }

    @Override
    public Kind kind() {
        return Kind.PASSIVE;
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public Optional<MoveInstance> tryStart(MoveContext context) {
        return Optional.of(new Instance(tuning, context.tick()));
    }

    @Override
    public ClaimCheck check(MoveClaim claim, ClaimContext context) {
        BodyState probe = context.body().withPosition(claim.position());
        return Probe.wall(context.world(), probe, tuning.reach() + 0.6, tuning.wallMinHeight() - 0.3).isPresent()
                ? ClaimCheck.ACCEPT
                : ClaimCheck.reject("no wall");
    }

    static final class Instance implements MoveInstance {

        private final WallJumpTuning tuning;
        private long startTick;
        private boolean sliding;
        private int used;
        private int k;

        Instance(WallJumpTuning tuning, long startTick) {
            this.tuning = tuning;
            this.startTick = startTick;
        }

        @Override
        public Key key() {
            return MoveKeys.WALL_SLIDE;
        }

        @Override
        public long startTick() {
            return startTick;
        }

        @Override
        public String phase() {
            return sliding ? "slide" : "idle";
        }

        @Override
        public Step tick(MoveContext context, MoveOutput.Builder out) {
            BodyState body = context.body();
            boolean launchedNow = context.lastLaunchTick() == context.tick();
            Optional<Long> refused = context.recall(MantleRefusal.REFUSED, Long.class);
            if (body.onGround() && refused.isPresent() && context.tick() > refused.get()) {
                context.forget(MantleRefusal.REFUSED);
                refused = Optional.empty();
            }
            if (refused.isPresent() && !body.onGround()) {
                stop(context, out, MoveEnd.EndReason.CONDITION);
                return Step.CONTINUE;
            }
            if (body.onGround() || launchedNow) {
                stop(context, out, body.onGround() ? MoveEnd.EndReason.COMPLETED : MoveEnd.EndReason.INTERRUPTED);
                used = 0;
                return Step.CONTINUE;
            }
            WallJumpTuning.Slide slide = tuning.slide();
            Optional<Probe.Wall> wall = Probe.wall(context.world(), body, tuning.reach(), tuning.wallMinHeight());
            Vec3 keys = Probe.inputDirection(body, context.input());
            boolean pushing = wall.isPresent() && keys.dot(wall.get().normal().multiply(-1.0)) > slide.pushThreshold();
            boolean falling = body.velocity().y() < 0.0;
            if (!pushing || !falling || used >= slide.maxTicks()) {
                stop(context, out, used >= slide.maxTicks() ? MoveEnd.EndReason.COMPLETED : MoveEnd.EndReason.CONDITION);
                return Step.CONTINUE;
            }
            if (!sliding) {
                sliding = true;
                startTick = context.tick();
                k = 0;
                context.claim(MoveClaim.of(MoveKeys.WALL_SLIDE, context.tick(), body.position(), body.velocity())
                        .withNormal(wall.get().normal()));
            } else {
                k++;
            }
            double cap = slide.cap(used);
            used++;
            double fall = body.velocity().y();
            double speed;
            if (fall < -cap) {
                double braked = Math.min(-cap, fall + slide.maxBrake());
                out.velocityY(VelocityRule.set(braked));
                speed = -braked;
            } else {
                out.velocityY(VelocityRule.capFall(cap));
                speed = -fall;
            }
            out.event(new MoveEvent.FallDistanceCap(ReferenceIntegrator.equivalentFallHeight(speed)));
            double side = wall.get().normal().dot(body.right());
            double away = side == 0.0 ? 1.0 : Math.signum(side);
            out.cameraSignals(new MoveOutput.CameraSignals(0.0, slide.roll() * away, 0.0));
            if (k % slide.effectEveryTicks() == 0) {
                out.event(new MoveEvent.Particles("wall_dust", body.position()));
                out.sound(MoveSound.event(slide.sound(), body.position(), 0.6, slide.pitch()));
            }
            return Step.CONTINUE;
        }

        private void stop(MoveContext context, MoveOutput.Builder out, MoveEnd.EndReason reason) {
            if (sliding) {
                sliding = false;
                context.endClaim(MoveKeys.WALL_SLIDE, reason);
            }
        }

        @Override
        public MoveInstance copy() {
            Instance copy = new Instance(tuning, startTick);
            copy.sliding = sliding;
            copy.used = used;
            copy.k = k;
            return copy;
        }
    }
}
