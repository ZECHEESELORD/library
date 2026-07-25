package sh.harold.library.npc.behavior;

import net.kyori.adventure.identity.Identified;
import net.kyori.adventure.text.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface HumanoidBehaviorCapable {
    Optional<NpcBehaviorProfile> profile();

    CompletionStage<Void> configure(NpcBehaviorProfile profile);

    CompletionStage<Void> disable();

    NpcPlayback speak(Component text);

    NpcPlayback speakNow(Component text);

    void clearSpeech();

    NpcPlayback perform(NpcRoutine routine);

    NpcAttentionLease attendTo(UUID viewerId);

    default NpcAttentionLease attendTo(Identified viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return attendTo(viewer.identity().uuid());
    }

    NpcBehaviorSnapshot snapshot();
}
