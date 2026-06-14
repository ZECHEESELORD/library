package sh.harold.library.boundary.core;

import sh.harold.library.boundary.BoundaryDecisionQuery;
import sh.harold.library.boundary.BoundaryDecisionReason;

import java.util.Objects;

@FunctionalInterface
public interface BoundaryRule {

    BoundaryDecisionReason decide(BoundaryDecisionQuery query);

    static BoundaryRule allowAll() {
        return ignored -> BoundaryDecisionReason.ALLOWED;
    }

    static BoundaryRule fixed(BoundaryDecisionReason reason) {
        Objects.requireNonNull(reason, "reason");
        return ignored -> reason;
    }
}
