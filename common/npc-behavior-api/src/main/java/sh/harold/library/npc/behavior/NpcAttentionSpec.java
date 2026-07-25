package sh.harold.library.npc.behavior;

import java.util.Objects;

public final class NpcAttentionSpec {
    public static final double DEFAULT_ENTER_RADIUS = 6.0;
    public static final double DEFAULT_EXIT_RADIUS = 8.0;
    public static final double DEFAULT_MAXIMUM_VERTICAL_DIFFERENCE = 4.0;
    public static final int DEFAULT_LOS_PROBE_INTERVAL_TICKS = 4;
    public static final int DEFAULT_LOS_FAILURES_BEFORE_RELEASE = 3;

    private final double enterRadius;
    private final double exitRadius;
    private final double maximumVerticalDifference;
    private final boolean sameSpaceRequired;
    private final boolean lineOfSightRequired;
    private final int lineOfSightProbeIntervalTicks;
    private final int lineOfSightFailuresBeforeRelease;
    private final NpcAttentionResponse idleResponse;
    private final NpcAttentionResponse routineResponse;
    private final NpcAttentionResponse conversationResponse;

    private NpcAttentionSpec(Builder builder) {
        this.enterRadius = builder.enterRadius;
        this.exitRadius = builder.exitRadius;
        this.maximumVerticalDifference = builder.maximumVerticalDifference;
        this.sameSpaceRequired = builder.sameSpaceRequired;
        this.lineOfSightRequired = builder.lineOfSightRequired;
        this.lineOfSightProbeIntervalTicks = builder.lineOfSightProbeIntervalTicks;
        this.lineOfSightFailuresBeforeRelease = builder.lineOfSightFailuresBeforeRelease;
        this.idleResponse = builder.idleResponse;
        this.routineResponse = builder.routineResponse;
        this.conversationResponse = builder.conversationResponse;
        validate();
    }

    public static NpcAttentionSpec defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public double enterRadius() {
        return enterRadius;
    }

    public double exitRadius() {
        return exitRadius;
    }

    public double maximumVerticalDifference() {
        return maximumVerticalDifference;
    }

    public boolean sameSpaceRequired() {
        return sameSpaceRequired;
    }

    public boolean lineOfSightRequired() {
        return lineOfSightRequired;
    }

    public int lineOfSightProbeIntervalTicks() {
        return lineOfSightProbeIntervalTicks;
    }

    public int lineOfSightFailuresBeforeRelease() {
        return lineOfSightFailuresBeforeRelease;
    }

    public NpcAttentionResponse idleResponse() {
        return idleResponse;
    }

    public NpcAttentionResponse routineResponse() {
        return routineResponse;
    }

    public NpcAttentionResponse conversationResponse() {
        return conversationResponse;
    }

    public NpcAttentionResponse responseFor(NpcAttentionActivity activity) {
        return switch (Objects.requireNonNull(activity, "activity")) {
            case IDLE -> idleResponse;
            case ROUTINE -> routineResponse;
            case CONVERSATION -> conversationResponse;
        };
    }

    private void validate() {
        requirePositive(enterRadius, "enterRadius");
        requirePositive(exitRadius, "exitRadius");
        if (exitRadius < enterRadius) {
            throw new IllegalArgumentException("exitRadius cannot be smaller than enterRadius");
        }
        requireNonNegative(maximumVerticalDifference, "maximumVerticalDifference");
        if (lineOfSightProbeIntervalTicks < 1) {
            throw new IllegalArgumentException("lineOfSightProbeIntervalTicks must be positive");
        }
        if (lineOfSightFailuresBeforeRelease < 1) {
            throw new IllegalArgumentException("lineOfSightFailuresBeforeRelease must be positive");
        }
        Objects.requireNonNull(idleResponse, "idleResponse");
        Objects.requireNonNull(routineResponse, "routineResponse");
        Objects.requireNonNull(conversationResponse, "conversationResponse");
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    public static final class Builder {
        private static final NpcAcknowledgementSpec DEFAULT_ACKNOWLEDGEMENT = NpcAcknowledgementSpec.gestures(
                NpcGesturePreset.NOD,
                NpcGesturePreset.HEAD_FLICK_UP,
                NpcGesturePreset.HEAD_FLICK_DOWN
        );

        private double enterRadius = DEFAULT_ENTER_RADIUS;
        private double exitRadius = DEFAULT_EXIT_RADIUS;
        private double maximumVerticalDifference = DEFAULT_MAXIMUM_VERTICAL_DIFFERENCE;
        private boolean sameSpaceRequired = true;
        private boolean lineOfSightRequired = true;
        private int lineOfSightProbeIntervalTicks = DEFAULT_LOS_PROBE_INTERVAL_TICKS;
        private int lineOfSightFailuresBeforeRelease = DEFAULT_LOS_FAILURES_BEFORE_RELEASE;
        private NpcAttentionResponse idleResponse = NpcAttentionResponse.sustain(NpcSustainMode.NATURAL);
        private NpcAttentionResponse routineResponse = NpcAttentionResponse.acknowledge(DEFAULT_ACKNOWLEDGEMENT);
        private NpcAttentionResponse conversationResponse = NpcAttentionResponse.ignore();

        private Builder() {
        }

        public Builder enterRadius(double enterRadius) {
            this.enterRadius = enterRadius;
            return this;
        }

        public Builder exitRadius(double exitRadius) {
            this.exitRadius = exitRadius;
            return this;
        }

        public Builder maximumVerticalDifference(double maximumVerticalDifference) {
            this.maximumVerticalDifference = maximumVerticalDifference;
            return this;
        }

        public Builder sameSpaceRequired(boolean sameSpaceRequired) {
            this.sameSpaceRequired = sameSpaceRequired;
            return this;
        }

        public Builder lineOfSightRequired(boolean lineOfSightRequired) {
            this.lineOfSightRequired = lineOfSightRequired;
            return this;
        }

        public Builder lineOfSightProbeIntervalTicks(int lineOfSightProbeIntervalTicks) {
            this.lineOfSightProbeIntervalTicks = lineOfSightProbeIntervalTicks;
            return this;
        }

        public Builder lineOfSightFailuresBeforeRelease(int failures) {
            this.lineOfSightFailuresBeforeRelease = failures;
            return this;
        }

        public Builder idleResponse(NpcAttentionResponse idleResponse) {
            this.idleResponse = Objects.requireNonNull(idleResponse, "idleResponse");
            return this;
        }

        public Builder routineResponse(NpcAttentionResponse routineResponse) {
            this.routineResponse = Objects.requireNonNull(routineResponse, "routineResponse");
            return this;
        }

        public Builder conversationResponse(NpcAttentionResponse conversationResponse) {
            this.conversationResponse = Objects.requireNonNull(conversationResponse, "conversationResponse");
            return this;
        }

        public NpcAttentionSpec build() {
            return new NpcAttentionSpec(this);
        }
    }
}
