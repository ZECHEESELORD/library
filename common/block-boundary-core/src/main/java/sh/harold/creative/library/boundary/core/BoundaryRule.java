package sh.harold.creative.library.boundary.core;

import sh.harold.creative.library.boundary.BoundaryDecisionQuery;
import sh.harold.creative.library.boundary.BoundaryDecisionReason;

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
