package sh.harold.library.npc.behavior;

import java.util.Objects;
import java.util.Optional;

public sealed interface NpcAttentionResponse permits
        NpcAttentionResponse.Sustain,
        NpcAttentionResponse.Acknowledge,
        NpcAttentionResponse.Ignore {

    static Sustain sustain(NpcSustainMode mode) {
        return new Sustain(mode, Optional.empty());
    }

    static Sustain sustain(NpcSustainMode mode, NpcAcknowledgementSpec acquisitionAct) {
        return new Sustain(mode, Optional.of(Objects.requireNonNull(acquisitionAct, "acquisitionAct")));
    }

    static Acknowledge acknowledge(NpcAcknowledgementSpec acknowledgement) {
        return new Acknowledge(acknowledgement);
    }

    static Ignore ignore() {
        return Ignore.INSTANCE;
    }

    record Sustain(NpcSustainMode mode, Optional<NpcAcknowledgementSpec> acquisitionAct)
            implements NpcAttentionResponse {
        public Sustain {
            Objects.requireNonNull(mode, "mode");
            acquisitionAct = Objects.requireNonNull(acquisitionAct, "acquisitionAct");
        }
    }

    record Acknowledge(NpcAcknowledgementSpec acknowledgement) implements NpcAttentionResponse {
        public Acknowledge {
            Objects.requireNonNull(acknowledgement, "acknowledgement");
        }
    }

    final class Ignore implements NpcAttentionResponse {
        private static final Ignore INSTANCE = new Ignore();

        private Ignore() {
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Ignore;
        }

        @Override
        public int hashCode() {
            return Ignore.class.hashCode();
        }

        @Override
        public String toString() {
            return "Ignore";
        }
    }
}
