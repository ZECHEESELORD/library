package sh.harold.library.movement;

import sh.harold.library.spatial.Vec3;

import java.util.Optional;

/** The server's view when it checks a claim. */
public interface ClaimContext {

    /** The server's body now (its last accepted position). */
    BodyState body();

    MovementWorld world();

    long serverTick();

    /** Ticks the claim has waited (0 on arrival). */
    int waitedTicks();

    /** Ticks since the body last stood on the ground, by the server's reckoning. */
    int ticksSinceGround();

    /** The distance of the last fall the server saw end. */
    double lastLandingFall();

    /** Ticks since that fall ended. */
    long ticksSinceLanding();

    /** Ticks since the client's sneak key went down, by the inputs it reported. */
    long ticksSinceSneakPress();

    /** The wall normal of the last accepted kick, until the body touches the ground. */
    Optional<Vec3> lastKickNormal();

    /** How far the claimed position may sit from the server's before it counts as a desync. */
    double positionTolerance();
}
