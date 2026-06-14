package sh.harold.library.scoreboard;

import net.kyori.adventure.key.Key;
import sh.harold.library.tick.InstanceConflictPolicy;

import java.util.Objects;

public record TransientSectionSpec(
        Key key,
        ScoreboardSection section,
        TransientPlacement placement,
        String targetSectionId,
        long ttlTicks,
        InstanceConflictPolicy conflictPolicy
) {

    public TransientSectionSpec {
        key = Objects.requireNonNull(key, "key");
        section = Objects.requireNonNull(section, "section");
        placement = Objects.requireNonNull(placement, "placement");
        if (placement == TransientPlacement.BEFORE_SECTION
                || placement == TransientPlacement.AFTER_SECTION
                || placement == TransientPlacement.REPLACE_SECTION) {
            targetSectionId = ScoreboardValidation.requireSectionId(targetSectionId);
        } else if (targetSectionId != null) {
            targetSectionId = ScoreboardValidation.requireSectionId(targetSectionId);
        }
        if (ttlTicks <= 0L) {
            throw new IllegalArgumentException("ttlTicks must be positive");
        }
        conflictPolicy = Objects.requireNonNull(conflictPolicy, "conflictPolicy");
    }

    public static Builder builder(Key key) {
        return new Builder(key);
    }

    public static final class Builder {
        private final Key key;
        private ScoreboardSection section;
        private TransientPlacement placement = TransientPlacement.TOP;
        private String targetSectionId;
        private long ttlTicks = 20L;
        private InstanceConflictPolicy conflictPolicy = InstanceConflictPolicy.REPLACE;

        private Builder(Key key) {
            this.key = Objects.requireNonNull(key, "key");
        }

        public Builder section(ScoreboardSection section) {
            this.section = Objects.requireNonNull(section, "section");
            return this;
        }

        public Builder placement(TransientPlacement placement) {
            this.placement = Objects.requireNonNull(placement, "placement");
            return this;
        }

        public Builder targetSectionId(String targetSectionId) {
            this.targetSectionId = ScoreboardValidation.requireSectionId(targetSectionId);
            return this;
        }

        public Builder ttlTicks(long ttlTicks) {
            this.ttlTicks = ttlTicks;
            return this;
        }

        public Builder conflictPolicy(InstanceConflictPolicy conflictPolicy) {
            this.conflictPolicy = Objects.requireNonNull(conflictPolicy, "conflictPolicy");
            return this;
        }

        public TransientSectionSpec build() {
            return new TransientSectionSpec(key, section, placement, targetSectionId, ttlTicks, conflictPolicy);
        }
    }
}
