package sh.harold.library.movement.core.moves;

import net.kyori.adventure.key.Key;
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
import sh.harold.library.movement.VelocityRule;
import sh.harold.library.movement.core.camera.MoveCameras;
import sh.harold.library.movement.core.tuning.MantleTuning;
import sh.harold.library.spatial.Vec3;
import sh.harold.library.tween.Easing;

import java.util.Optional;

/**
 * 02 section 2 with the 2026-11-23 rulings. Movement input within {@code approachDegrees} of a wall (MA4), jump held or
 * pressed within the buffer, and a ledge in reach: a vault when sprinting with the top 0.8 to 1.3 above the feet
 * (5 ticks, speed kept), otherwise a climb (0.9 to 1.6, 9 ticks). From the ground a ledge 1.25 or lower mantles only
 * when sprinting, so a walking tap at a step stays vanilla (MA5). "Sprinting" is the sprint state with horizontal speed
 * of at least {@code sprintSpeed}. The body follows a designed path as kinematic targets, so vanilla collision and the
 * server's movement checks still see ordinary moves.
 */
public final class MantleMove implements MoveDefinition {

    /** The vault's exit velocity, recalled by the slide when the vault hands off to it (MA11). */
    public static final Key SLIDE_ENTRY = Key.key(MoveKeys.NAMESPACE, "mantle/slide_entry");

    private final MantleTuning tuning;
    private final int bufferTicks;
    private final boolean slideEnabled;

    public MantleMove(MantleTuning tuning, int bufferTicks) {
        this(tuning, bufferTicks, false);
    }

    /** @param slideEnabled whether the vault and the climb may hand off to the slide (MA11, MA9) */
    public MantleMove(MantleTuning tuning, int bufferTicks, boolean slideEnabled) {
        this.tuning = tuning;
        this.bufferTicks = bufferTicks;
        this.slideEnabled = slideEnabled;
    }

    @Override
    public Key key() {
        return MoveKeys.MANTLE;
    }

    @Override
    public Kind kind() {
        return Kind.EXCLUSIVE;
    }

    @Override
    public int priority() {
        return 80;
    }

    @Override
    public Optional<MoveInstance> tryStart(MoveContext context) {
        if (!context.input().jump() && !context.jumpPressedWithin(bufferTicks - 1)) {
            return Optional.empty();
        }
        Attempt attempt = attempt(context, tuning);
        if (attempt == null || !attempt.check().found()) {
            return Optional.empty();
        }
        return Optional.of(new Instance(tuning, context.tick(), context.body(), attempt, slideEnabled));
    }

    /**
     * What a mantle attempt this tick would meet, or {@code null} when none would be tried: no movement input toward a
     * wall, a fall too long to catch, no ledge top in the window, or a low ledge from the ground without sprinting
     * (the vanilla jump takes it).
     */
    static Attempt attempt(MoveContext context, MantleTuning tuning) {
        BodyState body = context.body();
        Vec3 keys = Probe.inputDirection(body, context.input());
        if (keys.lengthSquared() < 0.5) {
            return null;
        }
        Vec3 direction = Probe.cardinal(keys);
        if (keys.dot(direction) < Math.cos(Math.toRadians(tuning.approachDegrees())) - 1.0e-9) {
            return null;
        }
        if (!body.onGround() && body.fallDistance() > tuning.maxFall()) {
            return null;
        }
        Vec3 motion = context.lastMotion().withY(0.0);
        boolean sprinting = context.sprintingRecently() && motion.length() + 1.0e-9 >= tuning.sprintSpeed();
        double min = sprinting ? tuning.vaultMinHeight() : tuning.minHeight();
        Probe.LedgeCheck check = Probe.ledgeCheck(context.world(), body, direction, tuning.reach(), min,
                tuning.maxHeight(), tuning.headroom(), tuning.depth());
        if (!check.found() && !check.blocked()) {
            return null;
        }
        boolean vault = sprinting && check.height() <= tuning.vaultMaxHeight() + 1.0e-6;
        if (!vault && body.onGround() && check.height() <= tuning.groundMaxHeight() + 1.0e-6) {
            return null;
        }
        return new Attempt(direction, check, vault, Math.max(0.0, motion.dot(direction)));
    }

    /** A mantle attempt: the wall direction, what the probe found, vault or climb, and the entry speed toward it. */
    record Attempt(Vec3 direction, Probe.LedgeCheck check, boolean vault, double entrySpeed) {
    }

    @Override
    public ClaimCheck check(MoveClaim claim, ClaimContext context) {
        if (claim.normal().lengthSquared() < 0.5) {
            return ClaimCheck.reject("no wall direction");
        }
        BodyState probe = context.body().withPosition(claim.position());
        Vec3 direction = claim.normal().multiply(-1.0);
        Optional<Probe.Ledge> ledge = Probe.ledge(context.world(), probe, direction, tuning.reach() + 0.3,
                tuning.lowestHeight() - 0.3, tuning.maxHeight() + 0.3, tuning.headroom() - 0.05, tuning.depth() - 0.05);
        if (ledge.isEmpty()) {
            return ClaimCheck.reject("no ledge");
        }
        if (Math.abs(ledge.get().top() - claim.ledgeTop()) > 0.25) {
            return ClaimCheck.reject("ledge top mismatch");
        }
        return ClaimCheck.ACCEPT;
    }

    static final class Instance implements MoveInstance {

        private final MantleTuning tuning;
        private final long startTick;
        private final boolean vault;
        private final boolean slideEnabled;
        private final Vec3 start;
        private final Vec3 direction;
        private final double top;
        private final double gap;
        private final double entrySpeed;
        private final double handSign;
        private int k = -1;
        private boolean sprintSeen;
        private double lateral;
        private double exitSpeed;

        Instance(MantleTuning tuning, long startTick, BodyState body, Attempt attempt, boolean slideEnabled) {
            Probe.Ledge ledge = attempt.check().ledge().orElseThrow();
            this.tuning = tuning;
            this.startTick = startTick;
            this.vault = attempt.vault();
            this.slideEnabled = slideEnabled;
            this.start = body.position();
            this.direction = ledge.direction();
            this.top = ledge.top();
            this.gap = ledge.gap();
            this.entrySpeed = attempt.entrySpeed();
            Vec3 right = body.right();
            this.handSign = right.dot(direction) >= 0 ? 1.0 : -1.0;
        }

        private Instance(Instance other) {
            this.tuning = other.tuning;
            this.startTick = other.startTick;
            this.vault = other.vault;
            this.slideEnabled = other.slideEnabled;
            this.start = other.start;
            this.direction = other.direction;
            this.top = other.top;
            this.gap = other.gap;
            this.entrySpeed = other.entrySpeed;
            this.handSign = other.handSign;
            this.k = other.k;
            this.sprintSeen = other.sprintSeen;
            this.lateral = other.lateral;
            this.exitSpeed = other.exitSpeed;
        }

        @Override
        public Key key() {
            return MoveKeys.MANTLE;
        }

        @Override
        public long startTick() {
            return startTick;
        }

        @Override
        public String phase() {
            if (vault) {
                return k <= tuning.vault().swingTick() ? "vault_swing" : "vault_rise";
            }
            MantleTuning.Climb climb = tuning.climb();
            if (k <= climb.settleTick()) {
                return "climb_hang";
            }
            return k < climb.inputTick() ? "climb_pull" : "climb_stand";
        }

        @Override
        public boolean allowsStart(Key other) {
            return false;
        }

        @Override
        public MoveClaim claim(MoveClaim base) {
            return base.withNormal(direction.multiply(-1.0)).withLedgeTop(top);
        }

        public boolean vault() {
            return vault;
        }

        @Override
        public Step tick(MoveContext context, MoveOutput.Builder out) {
            k++;
            if (k == 0) {
                out.sound(new MoveSound(MoveSound.Source.BLOCK_HIT, "minecraft:block.wood.hit",
                        start.add(direction.multiply(0.3 + gap + 0.05)).withY(top - 0.5), 1.0f,
                        (float) tuning.hitPitch()));
            }
            return vault ? vaultTick(context, out) : climbTick(context, out);
        }

        private boolean sprintHeld(MoveContext context) {
            return context.input().sprint() || context.sprintingRecently();
        }

        /** Ticks since T0 of a press, or -1 when it came before this mantle. */
        private long sinceStart(long press) {
            return press >= startTick ? press - startTick : -1;
        }

        private Step vaultTick(MoveContext context, MoveOutput.Builder out) {
            MantleTuning.Vault v = tuning.vault();
            if (k == 0) {
                out.camera(new CameraCommand.Start(MoveCameras.vault(v)));
            }
            if (k >= v.ticks()) {
                double speed = Math.max(entrySpeed, sprintHeld(context) ? v.sprintExitSpeed() : 0.0);
                Vec3 exit = direction.multiply(speed);
                out.horizontal(exit.x(), exit.z());
                out.sound(new MoveSound(MoveSound.Source.BLOCK_STEP, "minecraft:block.stone.step",
                        context.body().position().add(new Vec3(0, -0.2, 0)), 0.5f, 1.0f));
                long press = sinceStart(context.lastJumpPress());
                if (press >= v.hopFromTick() && press <= v.hopToTick()) {
                    out.jump();
                    context.consumeJump();
                    context.markLaunch(MoveKeys.MANTLE, 0.42);
                    return Step.end(MoveEnd.EndReason.COMPLETED);
                }
                if (slideEnabled && context.input().sneak() && speed + 1.0e-9 >= v.slideMinSpeed()) {
                    context.remember(SLIDE_ENTRY, exit);
                    return Step.handoff(MoveKeys.SLIDE);
                }
                return Step.end(MoveEnd.EndReason.COMPLETED);
            }
            double swingFeet = Math.max(start.y(), top + v.swingEyeAboveLedge() - BodyState.PLAYER_EYE_HEIGHT);
            double y;
            if (k == 0) {
                y = start.y();
            } else if (k <= v.swingTick()) {
                y = lerp(start.y(), swingFeet, Easing.QUAD_OUT.apply((double) k / v.swingTick()));
            } else if (k <= v.riseTick()) {
                double u = (double) (k - v.swingTick()) / Math.max(1, v.riseTick() - v.swingTick());
                y = lerp(swingFeet, top + 1.0e-3, Easing.QUAD_OUT.apply(u));
            } else {
                y = top + 1.0e-3;
            }
            double forward = 0.0;
            if (k > v.swingTick()) {
                double u = Math.min(1.0, (double) (k - v.swingTick()) / Math.max(1, v.riseTick() - v.swingTick()));
                forward = (gap + v.forward()) * Easing.QUAD_IN_OUT.apply(u);
            }
            out.kinematicTarget(start.add(direction.multiply(forward)).withY(y));
            return Step.CONTINUE;
        }

        private Step climbTick(MoveContext context, MoveOutput.Builder out) {
            MantleTuning.Climb c = tuning.climb();
            if (k == 0) {
                out.camera(new CameraCommand.Start(MoveCameras.climb(c, handSign)));
            }
            if (k > 0 && k <= c.cancelWindowTicks()) {
                if (context.sneakPressed()) {
                    out.velocity(Vec3.ZERO);
                    out.camera(new CameraCommand.Release(MoveCameras.MANTLE));
                    return Step.end(MoveEnd.EndReason.CANCELLED);
                }
                if (context.jumpPressed()) {
                    out.camera(new CameraCommand.Release(MoveCameras.MANTLE));
                    return Step.handoff(MoveKeys.WALL_JUMP);
                }
            }
            if (k >= c.bufferFromTick() && context.input().sprint() && !context.previousInput().sprint()) {
                sprintSeen = true;
            }
            if (k >= c.ticks()) {
                return Step.end(MoveEnd.EndReason.COMPLETED);
            }
            if (k >= c.inputTick()) {
                long press = sinceStart(context.lastJumpPress());
                if (k == c.inputTick()) {
                    boolean sprint = context.input().sprint() || sprintSeen;
                    exitSpeed = context.input().forward() ? (sprint ? c.sprintExitSpeed() : c.walkExitSpeed()) : 0.0;
                    Vec3 exit = direction.multiply(exitSpeed);
                    out.horizontal(exit.x(), exit.z());
                    out.velocityY(VelocityRule.set(0.0));
                    if (press >= c.hopFromTick() && press <= c.hopToTick()) {
                        return jump(context, out, c, exit.add(direction.multiply(c.hopSpeed())), true);
                    }
                    if (press >= c.bufferFromTick() && press < c.hopFromTick()) {
                        return jump(context, out, c, exit, false);
                    }
                    long sneak = sinceStart(context.lastSneakPress());
                    if (slideEnabled && context.input().sneak() && sneak >= c.bufferFromTick()
                            && exitSpeed + 1.0e-9 >= c.slideMinSpeed()) {
                        context.remember(SLIDE_ENTRY, exit);
                        return Step.handoff(MoveKeys.SLIDE);
                    }
                } else if (context.jumpPressed() && k <= c.hopToTick()) {
                    Vec3 velocity = context.body().velocity().withY(0.0);
                    return jump(context, out, c, velocity.add(direction.multiply(c.hopSpeed())), true);
                }
                if (k == c.ticks() - 1) {
                    out.sound(new MoveSound(MoveSound.Source.BLOCK_STEP, "minecraft:block.stone.step",
                            context.body().position().add(new Vec3(0, -0.2, 0)), 0.5f, 1.0f));
                }
                return Step.CONTINUE;
            }
            double y;
            if (k <= c.settleTick()) {
                y = start.y();
            } else {
                double u = Math.min(1.0, (double) (k - c.settleTick()) / Math.max(1, c.riseTick() - c.settleTick()));
                y = lerp(start.y(), top + 1.0e-3, Easing.QUAD_IN_OUT.apply(u));
            }
            double forward = 0.0;
            if (k > c.forwardFromTick()) {
                double u = Math.min(1.0,
                        (double) (k - c.forwardFromTick()) / Math.max(1, c.forwardToTick() - c.forwardFromTick()));
                forward = (gap + c.forward()) * Easing.QUAD_IN_OUT.apply(u);
            }
            Vec3 side = new Vec3(-direction.z(), 0.0, direction.x());
            if (k >= c.strafeFromTick()) {
                double toward = Probe.inputDirection(context.body(), context.input()).dot(side);
                if (Math.abs(toward) > 1.0e-3) {
                    lateral += Math.signum(toward) * c.strafeSpeed();
                }
            }
            out.kinematicTarget(start.add(direction.multiply(forward)).add(side.multiply(lateral)).withY(y));
            return Step.CONTINUE;
        }

        /**
         * A jump out of the climb: buffered from {@code bufferFromTick} (plain) or a mantle hop (MA10), with the given
         * horizontal velocity. The climb camera finishes on its own; the hop adds its FOV kick.
         */
        private Step jump(MoveContext context, MoveOutput.Builder out, MantleTuning.Climb c, Vec3 horizontal,
                          boolean hop) {
            out.jump();
            out.horizontal(horizontal.x(), horizontal.z());
            out.velocityY(VelocityRule.KEEP);
            context.consumeJump();
            context.markLaunch(MoveKeys.MANTLE, 0.42);
            if (hop && c.camera().hopFov() != 0.0) {
                out.camera(new CameraCommand.Start(MoveCameras.mantleHop(c.camera())));
            }
            return Step.end(MoveEnd.EndReason.COMPLETED);
        }

        private static double lerp(double a, double b, double u) {
            return a + (b - a) * u;
        }

        @Override
        public MoveInstance copy() {
            return new Instance(this);
        }
    }
}
