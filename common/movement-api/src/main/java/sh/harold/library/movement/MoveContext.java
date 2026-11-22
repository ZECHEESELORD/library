package sh.harold.library.movement;

import net.kyori.adventure.key.Key;
import sh.harold.library.spatial.Vec3;

import java.util.Optional;

/**
 * What a move sees on one tick: the body and input at the start of the tick, the world, and the shared rules from 02
 * ("Rules shared by every move"): the 4-tick input buffer, 3-tick coyote time and the chord detector.
 */
public interface MoveContext {

    long tick();

    BodyState body();

    InputFrame input();

    InputFrame previousInput();

    MovementWorld world();

    /** Vanilla gravity for this body (0.08). */
    double gravity();

    /** How far the body actually moved during the previous tick: its real speed, before this tick's friction. */
    Vec3 lastMotion();

    /**
     * Per-body memory a move keeps between ticks while it is not live (the last wall touched, the last kick). Values
     * must be immutable: prediction snapshots copy the map, not the values.
     */
    <T> Optional<T> recall(Key key, Class<T> type);

    void remember(Key key, Object value);

    void forget(Key key);

    boolean jumpPressed();

    boolean sneakPressed();

    boolean sneakReleased();

    /** A jump press within the buffer window that no move has consumed yet. */
    boolean jumpBuffered();

    /** A jump press within the last {@code ticks} ticks (0 is this tick), consumed or not. */
    boolean jumpPressedWithin(int ticks);

    /** A sneak press within the last {@code ticks} ticks (0 is this tick). */
    boolean sneakPressedWithin(int ticks);

    void consumeJump();

    /** The tick of the last jump press, or {@code Long.MIN_VALUE / 2}. */
    long lastJumpPress();

    long lastSneakPress();

    /** Sneak and jump pressed within the chord window of each other, the later of the two on this tick. */
    boolean chord();

    /** Ticks since the body last stood on the ground (0 while standing). */
    int ticksSinceGround();

    /** Off the ground for no more than the coyote window, and not by a launch of its own. */
    boolean coyote();

    /** The body touched down at the start of this tick. */
    boolean landed();

    /** Distance fallen before the last touchdown. */
    double landingFall();

    long landingTick();

    /** The body was sprinting this tick or the one before (vanilla drops sprint on some inputs a tick early). */
    boolean sprintingRecently();

    /** Records that {@code by} launched the body this tick with upward speed {@code verticalSpeed}. */
    void markLaunch(Key by, double verticalSpeed);

    Optional<Key> lastLaunchBy();

    long lastLaunchTick();

    Vec3 lastLaunchPosition();

    boolean coolingDown(Key move);

    void cooldown(Key move, int ticks);

    /** Whether the host rejected a claim for {@code move} recently, so the move must not start again on replay. */
    boolean vetoed(Key move);

    /** The move handing off to the one being asked to start, if any. */
    Optional<Key> handoffFrom();

    /** Fires a passive move's own claim (the wall slide), which the host sends like an exclusive start. */
    void claim(MoveClaim claim);

    /** Ends a passive move's claim. */
    void endClaim(Key move, MoveEnd.EndReason reason);
}
