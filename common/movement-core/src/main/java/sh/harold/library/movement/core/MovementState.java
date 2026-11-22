package sh.harold.library.movement.core;

import net.kyori.adventure.key.Key;
import sh.harold.library.movement.InputFrame;
import sh.harold.library.movement.MoveInstance;
import sh.harold.library.spatial.Vec3;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Everything the engine remembers between ticks for one body: input edges and the buffer, ground and fall tracking,
 * launches, cooldowns, vetoes and the live moves. {@link #copy()} is a deep copy, so a prediction history can keep one
 * per tick and replay from any of them.
 */
public final class MovementState {

    static final long NEVER = Long.MIN_VALUE / 4;

    long tick = NEVER;
    boolean initialized;
    InputFrame previousInput = InputFrame.EMPTY;
    long lastJumpPress = NEVER;
    boolean jumpConsumed = true;
    long lastSneakPress = NEVER;
    long lastSneakRelease = NEVER;
    boolean previousOnGround = true;
    double previousY;
    double previousFall;
    boolean previousSprinting;
    int ticksSinceGround;
    long landingTick = NEVER;
    double landingFall;
    Key launchBy;
    long launchTick = NEVER;
    Vec3 launchPosition = Vec3.ZERO;
    final Map<Key, Long> cooldownUntil = new HashMap<>();
    final Map<Key, Long> vetoUntil = new HashMap<>();
    Vec3 previousPosition;
    Vec3 lastMotion = Vec3.ZERO;
    final Map<Key, Object> memory = new HashMap<>();
    MoveInstance active;
    final Map<Key, MoveInstance> passives = new LinkedHashMap<>();

    public MovementState() {
    }

    public MovementState copy() {
        MovementState copy = new MovementState();
        copy.tick = tick;
        copy.initialized = initialized;
        copy.previousInput = previousInput;
        copy.lastJumpPress = lastJumpPress;
        copy.jumpConsumed = jumpConsumed;
        copy.lastSneakPress = lastSneakPress;
        copy.lastSneakRelease = lastSneakRelease;
        copy.previousOnGround = previousOnGround;
        copy.previousY = previousY;
        copy.previousFall = previousFall;
        copy.previousSprinting = previousSprinting;
        copy.ticksSinceGround = ticksSinceGround;
        copy.landingTick = landingTick;
        copy.landingFall = landingFall;
        copy.launchBy = launchBy;
        copy.launchTick = launchTick;
        copy.launchPosition = launchPosition;
        copy.cooldownUntil.putAll(cooldownUntil);
        copy.vetoUntil.putAll(vetoUntil);
        copy.previousPosition = previousPosition;
        copy.lastMotion = lastMotion;
        copy.memory.putAll(memory);
        copy.active = active == null ? null : active.copy();
        passives.forEach((key, instance) -> copy.passives.put(key, instance.copy()));
        return copy;
    }

    /** The last tick stepped. */
    public long tick() {
        return tick;
    }

    /** The live exclusive move. */
    public Optional<MoveInstance> active() {
        return Optional.ofNullable(active);
    }

    public Optional<MoveInstance> passive(Key key) {
        return Optional.ofNullable(passives.get(key));
    }

    /**
     * Forbids {@code move} from starting until {@code untilTick} (exclusive). The host calls it when the server
     * rejected a claim, before replaying, so the replay does not start the move again.
     */
    public void veto(Key move, long untilTick) {
        vetoUntil.merge(move, untilTick, Math::max);
    }

    /** Ends the live exclusive move without an event (the host already knows: the server rejected it). */
    public void dropActive(Key move) {
        if (active != null && active.key().equals(move)) {
            active = null;
        }
    }

    public double landingFall() {
        return landingFall;
    }

    public long landingTick() {
        return landingTick;
    }

    public int ticksSinceGround() {
        return ticksSinceGround;
    }
}
