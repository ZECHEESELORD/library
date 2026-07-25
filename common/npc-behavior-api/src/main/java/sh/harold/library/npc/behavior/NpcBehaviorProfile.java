package sh.harold.library.npc.behavior;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class NpcBehaviorProfile {
    private final NpcPersonalityPreset personality;
    private final NpcPersonalityTuning tuning;
    private final NpcAttentionSpec attention;
    private final NpcVoiceProfile voice;
    private final List<NpcIdleEntry> idleEntries;
    private final List<Component> interactionLines;
    private final List<Component> propCompletionLines;
    private final List<Component> conversationInterruptionLines;

    private NpcBehaviorProfile(Builder builder) {
        this.personality = builder.personality;
        this.tuning = builder.tuning;
        this.attention = builder.attention;
        this.voice = builder.voice;
        this.idleEntries = List.copyOf(builder.idleEntries);
        this.interactionLines = List.copyOf(builder.interactionLines);
        this.propCompletionLines = List.copyOf(builder.propCompletionLines);
        this.conversationInterruptionLines = List.copyOf(builder.conversationInterruptionLines);
    }

    public NpcPersonalityPreset personality() {
        return personality;
    }

    public NpcPersonalityTuning tuning() {
        return tuning;
    }

    public NpcAttentionSpec attention() {
        return attention;
    }

    public NpcVoiceProfile voice() {
        return voice;
    }

    public List<NpcIdleEntry> idleEntries() {
        return idleEntries;
    }

    public List<Component> interactionLines() {
        return interactionLines;
    }

    public List<Component> propCompletionLines() {
        return propCompletionLines;
    }

    public List<Component> conversationInterruptionLines() {
        return conversationInterruptionLines;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(NpcPersonalityPreset personality) {
        return new Builder().personality(personality);
    }

    public static final class Builder {
        private NpcPersonalityPreset personality = NpcPersonalityPreset.NEUTRAL;
        private NpcPersonalityTuning tuning = NpcPersonalityTuning.DEFAULT;
        private NpcAttentionSpec attention = NpcAttentionSpec.defaults();
        private NpcVoiceProfile voice = NpcVoiceProfiles.SILENT;
        private final List<NpcIdleEntry> idleEntries = new ArrayList<>();
        private final List<Component> interactionLines = new ArrayList<>();
        private final List<Component> propCompletionLines = new ArrayList<>();
        private final List<Component> conversationInterruptionLines = new ArrayList<>();

        private Builder() {
        }

        public Builder personality(NpcPersonalityPreset personality) {
            this.personality = Objects.requireNonNull(personality, "personality");
            return this;
        }

        public Builder tuning(NpcPersonalityTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
            return this;
        }

        public Builder attention(NpcAttentionSpec attention) {
            this.attention = Objects.requireNonNull(attention, "attention");
            return this;
        }

        public Builder voice(NpcVoiceProfile voice) {
            this.voice = Objects.requireNonNull(voice, "voice");
            return this;
        }

        public Builder idle(NpcIdleEntry idleEntry) {
            idleEntries.add(Objects.requireNonNull(idleEntry, "idleEntry"));
            return this;
        }

        public Builder idle(NpcRoutine routine, int weight, NpcCooldownRange cooldown) {
            return idle(new NpcIdleEntry(routine, weight, cooldown));
        }

        public Builder idleEntries(Collection<NpcIdleEntry> entries) {
            addAllNonNull(idleEntries, entries, "entries");
            return this;
        }

        public Builder interactionLine(Component line) {
            interactionLines.add(Objects.requireNonNull(line, "line"));
            return this;
        }

        public Builder interactionLines(Collection<? extends Component> lines) {
            addAllNonNull(interactionLines, lines, "lines");
            return this;
        }

        public Builder propCompletionLine(Component line) {
            propCompletionLines.add(Objects.requireNonNull(line, "line"));
            return this;
        }

        public Builder propCompletionLines(Collection<? extends Component> lines) {
            addAllNonNull(propCompletionLines, lines, "lines");
            return this;
        }

        public Builder conversationInterruptionLine(Component line) {
            conversationInterruptionLines.add(Objects.requireNonNull(line, "line"));
            return this;
        }

        public Builder conversationInterruptionLines(Collection<? extends Component> lines) {
            addAllNonNull(conversationInterruptionLines, lines, "lines");
            return this;
        }

        public NpcBehaviorProfile build() {
            return new NpcBehaviorProfile(this);
        }

        private static <T> void addAllNonNull(List<T> destination, Collection<? extends T> values, String name) {
            Objects.requireNonNull(values, name);
            for (T value : values) {
                destination.add(Objects.requireNonNull(value, name + " contains null"));
            }
        }
    }
}
