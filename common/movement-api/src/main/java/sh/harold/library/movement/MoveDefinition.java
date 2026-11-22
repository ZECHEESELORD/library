package sh.harold.library.movement;

import net.kyori.adventure.key.Key;

import java.util.Optional;

/**
 * A move as a pluggable ability (architecture section 5). The core asks each enabled definition, in priority order,
 * whether it starts this tick; a live exclusive move then owns the body until it ends. Passive definitions (the jump,
 * the wall slide) are created once per body and ticked whenever no exclusive move is live.
 */
public interface MoveDefinition {

    Key key();

    Kind kind();

    /** Higher runs first when two exclusive moves could start on the same tick. */
    int priority();

    /**
     * For an exclusive move: the instance if the move starts this tick. For a passive move: called once, on the first
     * tick, for the instance that then lives as long as the body.
     */
    Optional<MoveInstance> tryStart(MoveContext context);

    /** Server-side check of a client's claim that this move started. The default accepts. */
    default ClaimCheck check(MoveClaim claim, ClaimContext context) {
        return ClaimCheck.ACCEPT;
    }

    enum Kind {
        EXCLUSIVE,
        PASSIVE
    }
}
