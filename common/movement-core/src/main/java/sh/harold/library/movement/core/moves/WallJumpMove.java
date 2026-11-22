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
import sh.harold.library.movement.MoveEvent;
import sh.harold.library.movement.MoveInstance;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.MoveOutput;
import sh.harold.library.movement.MoveSound;
import sh.harold.library.movement.core.camera.MoveCameras;
import sh.harold.library.movement.core.tuning.WallJumpTuning;
import sh.harold.library.spatial.Vec3;

import java.util.Optional;

/**
 * 02 section 5, the kick. Airborne for 3 ticks, a wall at least 1.5 tall within 0.1 of a side (or touched within the
 * last 3 ticks), and a jump press within 3 ticks. An instant move: it owns one tick, the kick; the camera runs on after.
 * The same wall face cannot be kicked twice until the body lands or kicks a face pointing another way.
 */
public final class WallJumpMove implements MoveDefinition {

    /** Memory: the last wall touched. */
    public static final Key CONTACT = Key.key(MoveKeys.NAMESPACE, "memory/wall_contact");
    /** Memory: the face of the last kick. */
    public static final Key LAST_KICK = Key.key(MoveKeys.NAMESPACE, "memory/last_kick");

    record Contact(long tick, Vec3 normal, Vec3 point) {
    }

    record Kick(Vec3 normal) {
    }

    private final WallJumpTuning tuning;

    public WallJumpMove(WallJumpTuning tuning) {
        this.tuning = tuning;
    }

    @Override
    public Key key() {
        return MoveKeys.WALL_JUMP;
    }

    @Override
    public Kind kind() {
        return Kind.EXCLUSIVE;
    }

    @Override
    public int priority() {
        return 70;
    }

    @Override
    public Optional<MoveInstance> tryStart(MoveContext context) {
        BodyState body = context.body();
        if (body.onGround()) {
            context.forget(LAST_KICK);
            context.forget(CONTACT);
        }
        Optional<Probe.Wall> wall = Probe.wall(context.world(), body, tuning.reach(), tuning.wallMinHeight());
        wall.ifPresent(value -> context.remember(CONTACT, new Contact(context.tick(), value.normal(), value.contact())));
        boolean handoff = context.handoffFrom().map(MoveKeys.MANTLE::equals).orElse(false);
        if (handoff) {
            Vec3 normal = Probe.cardinal(body.facing()).multiply(-1.0);
            Vec3 point = body.position().add(normal.multiply(-0.4)).add(new Vec3(0, 1.0, 0));
            return Optional.of(new Instance(tuning, context.tick(), normal, point));
        }
        if (body.onGround() || context.ticksSinceGround() < tuning.minAirTicks()) {
            return Optional.empty();
        }
        boolean pressed = context.jumpPressedWithin(tuning.pressBufferTicks() - 1)
                && (context.jumpBuffered() || context.jumpPressed());
        if (!pressed) {
            return Optional.empty();
        }
        Optional<Contact> contact = context.recall(CONTACT, Contact.class)
                .filter(value -> context.tick() - value.tick() <= tuning.afterContactTicks());
        if (contact.isEmpty()) {
            return Optional.empty();
        }
        Vec3 normal = contact.get().normal();
        boolean sameWall = context.recall(LAST_KICK, Kick.class)
                .map(kick -> kick.normal().dot(normal) > 0.99).orElse(false);
        if (sameWall) {
            return Optional.empty();
        }
        return Optional.of(new Instance(tuning, context.tick(), normal, contact.get().point()));
    }

    @Override
    public ClaimCheck check(MoveClaim claim, ClaimContext context) {
        Vec3 normal = claim.normal();
        if (normal.lengthSquared() < 0.5) {
            return ClaimCheck.reject("no wall normal");
        }
        if (context.lastKickNormal().map(last -> last.dot(normal) > 0.99).orElse(false)) {
            return ClaimCheck.reject("same wall");
        }
        BodyState probe = context.body().withPosition(claim.position());
        Optional<Probe.Wall> wall = Probe.wall(context.world(), probe, tuning.reach() + 0.6,
                tuning.wallMinHeight() - 0.3);
        boolean found = wall.isPresent() && wall.get().normal().dot(normal) > 0.99;
        if (!found) {
            for (Vec3 direction : Probe.CARDINALS) {
                if (direction.dot(normal) < -0.99 && !context.world().free(
                        Probe.front(probe.box(), direction, tuning.reach() + 0.6, probe.box().minY(),
                                probe.box().maxY()))) {
                    found = true;
                }
            }
        }
        return found ? ClaimCheck.ACCEPT : ClaimCheck.reject("no wall");
    }

    static final class Instance implements MoveInstance {

        private final WallJumpTuning tuning;
        private final long startTick;
        private final Vec3 normal;
        private final Vec3 point;

        Instance(WallJumpTuning tuning, long startTick, Vec3 normal, Vec3 point) {
            this.tuning = tuning;
            this.startTick = startTick;
            this.normal = normal;
            this.point = point;
        }

        @Override
        public Key key() {
            return MoveKeys.WALL_JUMP;
        }

        @Override
        public long startTick() {
            return startTick;
        }

        @Override
        public String phase() {
            return "kick";
        }

        @Override
        public MoveClaim claim(MoveClaim base) {
            return base.withNormal(normal);
        }

        @Override
        public Step tick(MoveContext context, MoveOutput.Builder out) {
            BodyState body = context.body();
            Vec3 keys = Probe.inputDirection(body, context.input());
            Vec3 horizontal = normal.multiply(tuning.kickAway()).add(keys.multiply(tuning.kickInput()));
            out.velocity(new Vec3(horizontal.x(), tuning.kickUp(), horizontal.z()));
            context.consumeJump();
            context.markLaunch(MoveKeys.WALL_JUMP, tuning.kickUp());
            context.remember(LAST_KICK, new Kick(normal));
            context.forget(CONTACT);
            out.event(new MoveEvent.FallDistanceReset());
            double side = normal.dot(body.right());
            double away = side == 0.0 ? 1.0 : Math.signum(side);
            out.camera(new CameraCommand.Start(MoveCameras.wallKick(tuning.camera(), away)));
            out.sound(new MoveSound(MoveSound.Source.BLOCK_HIT, "minecraft:block.stone.hit", point, 1.0f,
                    (float) tuning.hitPitch()));
            return Step.end(MoveEnd.EndReason.COMPLETED);
        }

        @Override
        public MoveInstance copy() {
            return new Instance(tuning, startTick, normal, point);
        }
    }
}
