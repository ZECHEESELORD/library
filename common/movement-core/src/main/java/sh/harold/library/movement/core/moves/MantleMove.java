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
 * 02 section 2. Forward held, jump held or pressed within the buffer, and a ledge in reach: a vault (top 0.9 to 1.1
 * above the feet, 5 ticks) or a climb (higher, 9 ticks). The body follows a designed path as kinematic targets, so
 * vanilla collision and the server's movement checks still see ordinary moves.
 */
public final class MantleMove implements MoveDefinition {

    private final MantleTuning tuning;
    private final int bufferTicks;

    public MantleMove(MantleTuning tuning, int bufferTicks) {
        this.tuning = tuning;
        this.bufferTicks = bufferTicks;
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
        BodyState body = context.body();
        if (!context.input().forward() || context.input().back()) {
            return Optional.empty();
        }
        if (!context.input().jump() && !context.jumpPressedWithin(bufferTicks - 1)) {
            return Optional.empty();
        }
        Vec3 direction = Probe.cardinal(body.facing());
        return Probe.ledge(context.world(), body, direction, tuning.reach(), tuning.minHeight(), tuning.maxHeight(),
                        tuning.headroom(), tuning.depth())
                .map(ledge -> new Instance(tuning, context.tick(), body, ledge));
    }

    @Override
    public ClaimCheck check(MoveClaim claim, ClaimContext context) {
        if (claim.normal().lengthSquared() < 0.5) {
            return ClaimCheck.reject("no wall direction");
        }
        BodyState probe = context.body().withPosition(claim.position());
        Vec3 direction = claim.normal().multiply(-1.0);
        Optional<Probe.Ledge> ledge = Probe.ledge(context.world(), probe, direction, tuning.reach() + 0.3,
                tuning.minHeight() - 0.3, tuning.maxHeight() + 0.3, tuning.headroom() - 0.05, tuning.depth() - 0.05);
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
        private final Vec3 start;
        private final Vec3 direction;
        private final double top;
        private final double gap;
        private final double entrySpeed;
        private final double handSign;
        private int k = -1;
        private boolean cameraStarted;

        Instance(MantleTuning tuning, long startTick, BodyState body, Probe.Ledge ledge) {
            this.tuning = tuning;
            this.startTick = startTick;
            this.vault = ledge.height() <= tuning.vaultMaxHeight() + 1.0e-6;
            this.start = body.position();
            this.direction = ledge.direction();
            this.top = ledge.top();
            this.gap = ledge.gap();
            this.entrySpeed = Math.max(0.0, body.velocity().dot(direction));
            Vec3 right = body.right();
            this.handSign = right.dot(direction) >= 0 ? 1.0 : -1.0;
        }

        private Instance(Instance other) {
            this.tuning = other.tuning;
            this.startTick = other.startTick;
            this.vault = other.vault;
            this.start = other.start;
            this.direction = other.direction;
            this.top = other.top;
            this.gap = other.gap;
            this.entrySpeed = other.entrySpeed;
            this.handSign = other.handSign;
            this.k = other.k;
            this.cameraStarted = other.cameraStarted;
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

        private Step vaultTick(MoveContext context, MoveOutput.Builder out) {
            MantleTuning.Vault v = tuning.vault();
            if (k >= v.ticks()) {
                Vec3 exit = direction.multiply(entrySpeed * v.keepHorizontal());
                out.horizontal(exit.x(), exit.z());
                out.sound(new MoveSound(MoveSound.Source.BLOCK_STEP, "minecraft:block.stone.step",
                        context.body().position().add(new Vec3(0, -0.2, 0)), 0.5f, 1.0f));
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
            if (k == 0 && !cameraStarted) {
                cameraStarted = true;
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
            if (k >= c.ticks()) {
                return Step.end(MoveEnd.EndReason.COMPLETED);
            }
            if (k >= c.inputTick()) {
                if (k == c.inputTick()) {
                    Vec3 exit = direction.multiply(context.input().forward() ? c.exitSpeed() : 0.0);
                    out.horizontal(exit.x(), exit.z());
                    out.velocityY(VelocityRule.set(0.0));
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
            out.kinematicTarget(start.add(direction.multiply(forward)).withY(y));
            return Step.CONTINUE;
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
