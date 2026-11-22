package sh.harold.library.movement.core;

import org.junit.jupiter.api.Test;
import sh.harold.library.movement.BodyState;
import sh.harold.library.movement.ClaimCheck;
import sh.harold.library.movement.ClaimContext;
import sh.harold.library.movement.MoveClaim;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.MovementWorld;
import sh.harold.library.movement.core.tuning.Moveset;
import sh.harold.library.spatial.Vec3;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClaimValidatorTest {

    record Context(BodyState body, MovementWorld world, int waitedTicks, int ticksSinceGround, double lastLandingFall,
                   long ticksSinceLanding, long ticksSinceSneakPress, Optional<Vec3> lastKickNormal)
            implements ClaimContext {

        @Override
        public long serverTick() {
            return 100;
        }

        @Override
        public double positionTolerance() {
            return 1.5;
        }

        Context waited(int ticks) {
            return new Context(body, world, ticks, ticksSinceGround, lastLandingFall, ticksSinceLanding,
                    ticksSinceSneakPress, lastKickNormal);
        }
    }

    static final ClaimValidator VALIDATOR = new ClaimValidator(MovementEngine.of(Moveset.DEFAULT));
    static final Vec3 FEET = new Vec3(0.5, 0.0, 0.5);

    static Context ground(MovementWorld world) {
        return new Context(BodyState.standing(FEET, 0.0f), world, 0, 0, 0.0, 100, 100, Optional.empty());
    }

    @Test
    void disabledMoveIsRejected() {
        ClaimValidator none = new ClaimValidator(MovementEngine.of(Moveset.DEFAULT.withEnabled(Set.of(MoveKeys.JUMP))));
        ClaimCheck check = none.check(MoveClaim.of(MoveKeys.ROLL, 1, FEET, Vec3.ZERO), ground(TestWorld.flat()));
        assertEquals(ClaimCheck.Verdict.REJECT, check.verdict());
    }

    @Test
    void desyncWaitsThenRejects() {
        MoveClaim claim = MoveClaim.of(MoveKeys.SLIDE, 1, FEET.add(new Vec3(0, 0, 3)), new Vec3(0, 0, 0.3));
        assertEquals(ClaimCheck.Verdict.DEFER, VALIDATOR.check(claim, ground(TestWorld.flat())).verdict());
        assertEquals(ClaimCheck.Verdict.REJECT, VALIDATOR.check(claim, ground(TestWorld.flat()).waited(10)).verdict());
    }

    @Test
    void rollNeedsAFallAndASneakPress() {
        MoveClaim claim = MoveClaim.of(MoveKeys.ROLL, 1, FEET, Vec3.ZERO).withFallDistance(8.0);
        Context noFall = ground(TestWorld.flat()).waited(3);
        assertEquals(ClaimCheck.Verdict.REJECT, VALIDATOR.check(claim, noFall).verdict());
        Context landed = new Context(BodyState.standing(FEET, 0.0f), TestWorld.flat(), 0, 0, 8.0, 1, 2,
                Optional.empty());
        assertEquals(ClaimCheck.Verdict.ACCEPT, VALIDATOR.check(claim, landed).verdict());
        Context noSneak = new Context(BodyState.standing(FEET, 0.0f), TestWorld.flat(), 0, 0, 8.0, 1, 50,
                Optional.empty());
        assertEquals(ClaimCheck.Verdict.REJECT, VALIDATOR.check(claim, noSneak).verdict());
    }

    @Test
    void wallJumpNeedsAWallAndANewFace() {
        TestWorld world = TestWorld.flat().fill(-1, 0, -3, -1, 10, 3);
        Vec3 feet = new Vec3(0.35, 2.0, 0.5);
        MoveClaim claim = MoveClaim.of(MoveKeys.WALL_JUMP, 1, feet, Vec3.ZERO).withNormal(new Vec3(1, 0, 0));
        Context air = new Context(BodyState.standing(feet, 0.0f), world, 0, 5, 0.0, 100, 100, Optional.empty());
        assertEquals(ClaimCheck.Verdict.ACCEPT, VALIDATOR.check(claim, air).verdict());
        Context same = new Context(BodyState.standing(feet, 0.0f), world, 0, 5, 0.0, 100, 100,
                Optional.of(new Vec3(1, 0, 0)));
        assertEquals(ClaimCheck.Verdict.REJECT, VALIDATOR.check(claim, same).verdict());
        Context open = new Context(BodyState.standing(feet, 0.0f), TestWorld.flat(), 0, 5, 0.0, 100, 100,
                Optional.empty());
        assertEquals(ClaimCheck.Verdict.REJECT, VALIDATOR.check(claim, open).verdict());
    }

    @Test
    void mantleNeedsTheLedgeItClaims() {
        TestWorld world = TestWorld.flat().fill(-3, 0, 2, 3, 0, 4);
        Vec3 feet = new Vec3(0.5, 0.0, 1.35);
        MoveClaim claim = MoveClaim.of(MoveKeys.MANTLE, 1, feet, Vec3.ZERO).withNormal(new Vec3(0, 0, -1))
                .withLedgeTop(1.0);
        Context context = new Context(BodyState.standing(feet, 0.0f), world, 0, 0, 0.0, 100, 100, Optional.empty());
        assertEquals(ClaimCheck.Verdict.ACCEPT, VALIDATOR.check(claim, context).verdict());
        Context flat = new Context(BodyState.standing(feet, 0.0f), TestWorld.flat(), 0, 0, 0.0, 100, 100,
                Optional.empty());
        assertEquals(ClaimCheck.Verdict.REJECT, VALIDATOR.check(claim, flat).verdict());
    }
}
