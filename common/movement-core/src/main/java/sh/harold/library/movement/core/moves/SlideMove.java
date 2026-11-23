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
import sh.harold.library.movement.MoveInstance;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.MoveOutput;
import sh.harold.library.movement.MoveSound;
import sh.harold.library.movement.SlideVariant;
import sh.harold.library.movement.VelocityRule;
import sh.harold.library.movement.core.camera.MoveCameras;
import sh.harold.library.movement.core.tuning.SlideTuning;
import sh.harold.library.spatial.Vec3;

import java.util.Optional;

/**
 * 02 section 4. S1: sneak pressed while sprinting on the ground. S2: sneak and jump within the chord window while
 * sprinting; a jump that already fired is pulled back to the ground (the "small hitch"). The hitbox drops to 0.6, speed
 * locks along the travel direction and decays on the flat; with no headroom at the end the body stays down and crawls
 * until there is.
 */
public final class SlideMove implements MoveDefinition {

    private final SlideTuning tuning;
    private final int chordTicks;

    public SlideMove(SlideTuning tuning, int chordTicks) {
        this.tuning = tuning;
        this.chordTicks = chordTicks;
    }

    @Override
    public Key key() {
        return MoveKeys.SLIDE;
    }

    @Override
    public Kind kind() {
        return Kind.EXCLUSIVE;
    }

    @Override
    public int priority() {
        return 60;
    }

    /** The tick the last slide ended, for the boost refill (S3). */
    public static final Key ENDED = Key.key(MoveKeys.NAMESPACE, "slide/ended");

    /** The boost refill now: 1 with no earlier slide, else ramping over {@code refillTicks} from its end. */
    static double refill(MoveContext context, SlideTuning tuning) {
        return context.recall(ENDED, Long.class).map(ended -> tuning.refill(context.tick() - ended)).orElse(1.0);
    }

    @Override
    public Optional<MoveInstance> tryStart(MoveContext context) {
        BodyState body = context.body();
        if (context.handoffFrom().map(MoveKeys.MANTLE::equals).orElse(false)) {
            Optional<Vec3> exit = context.recall(MantleMove.SLIDE_ENTRY, Vec3.class);
            context.forget(MantleMove.SLIDE_ENTRY);
            if (exit.isEmpty() || exit.get().lengthSquared() < 1.0e-8) {
                return Optional.empty();
            }
            double refill = refill(context, tuning);
            double start = Math.max(exit.get().length(), tuning.sprintSpeed()) * tuning.boostAt(refill);
            return Optional.of(new Instance(tuning, context.tick(), exit.get().normalize(), start, false,
                    body.position().y(), refill));
        }
        if (!context.sprintingRecently()) {
            return Optional.empty();
        }
        Vec3 motion = context.lastMotion().withY(0.0);
        double speed = motion.length();
        boolean fromJump = false;
        if (tuning.variant() == SlideVariant.S1) {
            if (!context.sneakPressed() || !body.onGround()) {
                return Optional.empty();
            }
        } else {
            if (!context.chord()) {
                return Optional.empty();
            }
            boolean jumped = context.lastLaunchBy().map(MoveKeys.JUMP::equals).orElse(false)
                    && context.tick() - context.lastLaunchTick() <= chordTicks;
            if (!body.onGround() && !jumped) {
                return Optional.empty();
            }
            fromJump = jumped && !body.onGround();
        }
        if (speed + 1.0e-9 < tuning.minSpeed()) {
            return Optional.empty();
        }
        Vec3 direction = motion.lengthSquared() > 1.0e-8 ? motion.normalize() : body.facing();
        double refill = refill(context, tuning);
        double start = Math.max(speed, tuning.sprintSpeed()) * tuning.boostAt(refill);
        double groundY = fromJump ? context.lastLaunchPosition().y() : body.position().y();
        if (context.jumpPressed() || context.jumpBuffered()) {
            context.consumeJump();
        }
        return Optional.of(new Instance(tuning, context.tick(), direction, start, fromJump, groundY, refill));
    }

    @Override
    public ClaimCheck check(MoveClaim claim, ClaimContext context) {
        double speed = claim.velocity().withY(0.0).length();
        if (context.ticksSinceGround() > chordTicks + 2 + context.waitedTicks()) {
            return ClaimCheck.reject("not on the ground");
        }
        return speed > tuning.maxSpeed() * 2.0 ? ClaimCheck.reject("too fast") : ClaimCheck.ACCEPT;
    }

    static final class Instance implements MoveInstance {

        private final SlideTuning tuning;
        private final long startTick;
        private Vec3 direction;
        private double speed;
        private final boolean fromJump;
        private final double groundY;
        private final double refill;
        private int k = -1;
        private int airTicks;
        private boolean crawling;

        Instance(SlideTuning tuning, long startTick, Vec3 direction, double speed, boolean fromJump, double groundY,
                 double refill) {
            this.refill = refill;
            this.tuning = tuning;
            this.startTick = startTick;
            this.direction = direction;
            this.speed = speed;
            this.fromJump = fromJump;
            this.groundY = groundY;
        }

        @Override
        public Key key() {
            return MoveKeys.SLIDE;
        }

        @Override
        public long startTick() {
            return startTick;
        }

        @Override
        public String phase() {
            if (crawling) {
                return "crawl";
            }
            return k < 4 ? "drop" : "slide";
        }

        /** The slide's current speed. */
        double speed() {
            return speed;
        }

        @Override
        public Step tick(MoveContext context, MoveOutput.Builder out) {
            k++;
            BodyState body = context.body();
            BodyOverride low = new BodyOverride(tuning.height(), tuning.eyeHeight(), BodyOverride.RenderPose.CRAWLING);
            if (crawling) {
                if (Probe.fits(context.world(), body, BodyState.PLAYER_HEIGHT)) {
                    out.camera(new CameraCommand.Start(MoveCameras.slideExit(tuning, false)));
                    context.remember(ENDED, context.tick());
                    return Step.end(MoveEnd.EndReason.COMPLETED);
                }
                out.body(low);
                return Step.CONTINUE;
            }
            if (k == 0) {
                out.camera(new CameraCommand.Start(MoveCameras.slideEye(tuning)));
                if (fromJump) {
                    out.velocityY(VelocityRule.set(Math.min(0.0, groundY - body.position().y())));
                }
            } else {
                double descended = Math.max(0.0, -context.lastMotion().y());
                speed = Math.min(tuning.maxSpeed(), Math.sqrt(speed * speed + tuning.slopeGain() * descended))
                        * tuning.decay();
                if (k >= tuning.steerFromTick()) {
                    steer(body.facing());
                }
                if (k >= tuning.jumpFromTick() && (context.jumpPressed() || context.jumpBuffered())
                        && body.onGround() && Probe.fits(context.world(), body, BodyState.PLAYER_HEIGHT)) {
                    context.consumeJump();
                    double launch = speed + tuning.jumpBonus();
                    out.jump().horizontal(direction.x() * launch, direction.z() * launch)
                            .discardFriction()
                            .inputScale(0.0);
                    context.markLaunch(MoveKeys.SLIDE, 0.42);
                    out.camera(new CameraCommand.Start(MoveCameras.slideExit(tuning, false)));
                    out.cameraSignals(MoveOutput.CameraSignals.REST);
                    context.remember(ENDED, context.tick());
                    return Step.end(MoveEnd.EndReason.COMPLETED);
                }
                Step end = endCheck(context, body);
                if (end != null) {
                    return finish(context, out, body, end, low);
                }
            }
            airTicks = body.onGround() || k == 0 ? 0 : airTicks + 1;
            out.horizontal(direction.x() * speed, direction.z() * speed)
                    .discardFriction()
                    .inputScale(0.0)
                    .body(low);
            SlideTuning.Camera camera = tuning.camera();
            double kick = camera.kick(refill);
            double fov = k < camera.fovTick()
                    ? kick * (k + 1.0) / (camera.fovTick() + 1.0)
                    : kick * speed / camera.fovReferenceSpeed();
            double roll = k >= tuning.steerFromTick() ? -camera.strafeRoll() * context.input().strafeImpulse() : 0.0;
            double shake = k >= tuning.steerFromTick() && speed > camera.shakeAboveSpeed() ? camera.shake() : 0.0;
            out.cameraSignals(new MoveOutput.CameraSignals(fov, roll, shake));
            if (k % tuning.sound().everyTicks() == 0) {
                out.sound(new MoveSound(MoveSound.Source.BLOCK_STEP, "minecraft:block.stone.step",
                        body.position().add(new Vec3(0, -0.2, 0)), 0.5f, (float) tuning.sound().pitch()));
            }
            return Step.CONTINUE;
        }

        private Step endCheck(MoveContext context, BodyState body) {
            if (speed < tuning.endSpeed()) {
                return Step.end(MoveEnd.EndReason.CONDITION);
            }
            if (!context.input().sneak() && k > tuning.releaseAfterTick()) {
                return Step.end(MoveEnd.EndReason.CANCELLED);
            }
            if (body.horizontalCollision()) {
                return Step.end(MoveEnd.EndReason.BLOCKED);
            }
            if (airTicks > tuning.maxAirTicks()) {
                return Step.end(MoveEnd.EndReason.CONDITION);
            }
            return null;
        }

        private Step finish(MoveContext context, MoveOutput.Builder out, BodyState body, Step end, BodyOverride low) {
            if (!Probe.fits(context.world(), body, BodyState.PLAYER_HEIGHT)) {
                crawling = true;
                out.body(low).cameraSignals(MoveOutput.CameraSignals.REST);
                return Step.CONTINUE;
            }
            out.camera(new CameraCommand.Start(MoveCameras.slideExit(tuning, false)));
            out.cameraSignals(MoveOutput.CameraSignals.REST);
            context.remember(ENDED, context.tick());
            return end;
        }

        private void steer(Vec3 facing) {
            double limit = Math.toRadians(tuning.steerDegrees());
            double current = Math.atan2(direction.z(), direction.x());
            double target = Math.atan2(facing.z(), facing.x());
            double delta = Math.IEEEremainder(target - current, Math.PI * 2.0);
            double turned = current + Math.clamp(delta, -limit, limit);
            direction = new Vec3(Math.cos(turned), 0.0, Math.sin(turned));
        }

        @Override
        public MoveInstance copy() {
            Instance copy = new Instance(tuning, startTick, direction, speed, fromJump, groundY, refill);
            copy.k = k;
            copy.airTicks = airTicks;
            copy.crawling = crawling;
            return copy;
        }
    }
}
