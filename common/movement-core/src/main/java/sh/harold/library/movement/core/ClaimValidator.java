package sh.harold.library.movement.core;

import sh.harold.library.movement.ClaimCheck;
import sh.harold.library.movement.ClaimContext;
import sh.harold.library.movement.MoveClaim;
import sh.harold.library.movement.MoveDefinition;

import java.util.Optional;

/**
 * The server half of claim acceptance (architecture section 5): a claim for a move the moveset does not enable is
 * rejected; a claim whose position sits too far from the server's body waits (the server's view lags), up to the
 * pending timeout; otherwise the move's own check decides.
 */
public final class ClaimValidator {

    private final MovementEngine engine;

    public ClaimValidator(MovementEngine engine) {
        this.engine = engine;
    }

    public ClaimCheck check(MoveClaim claim, ClaimContext context) {
        Optional<MoveDefinition> definition = engine.definition(claim.move());
        if (definition.isEmpty()) {
            return ClaimCheck.reject("move not enabled");
        }
        int timeout = engine.core().pendingClaimTimeoutTicks();
        double distance = claim.position().distance(context.body().position());
        if (distance > context.positionTolerance()) {
            return context.waitedTicks() >= timeout ? ClaimCheck.reject("position desync")
                    : ClaimCheck.defer("position desync");
        }
        ClaimCheck check = definition.get().check(claim, context);
        if (check.verdict() == ClaimCheck.Verdict.DEFER && context.waitedTicks() >= timeout) {
            return ClaimCheck.reject(check.reason());
        }
        return check;
    }
}
