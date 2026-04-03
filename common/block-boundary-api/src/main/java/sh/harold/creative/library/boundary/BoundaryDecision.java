package sh.harold.creative.library.boundary;

import java.util.Objects;
import java.util.Optional;

public record BoundaryDecision(BoundaryDecisionReason reason, BoundarySnapshot matchedBoundary, boolean exact) {

    public BoundaryDecision {
        reason = Objects.requireNonNull(reason, "reason");
    }

    public boolean allowed() {
        return reason.allowed();
    }

    public Optional<BoundarySnapshot> optionalMatchedBoundary() {
        return Optional.ofNullable(matchedBoundary);
    }
}
