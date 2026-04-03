package sh.harold.creative.library.boundary;

public enum BoundaryDecisionReason {
    ALLOWED,
    NO_BOUNDARY,
    OUTSIDE_BOUNDARY,
    NO_ACCESS,
    UNSUPPORTED_ACTION,
    UNSUPPORTED_EXACT_BOUNDS,
    ADAPTER_DEGRADED;

    public boolean allowed() {
        return this == ALLOWED;
    }
}
