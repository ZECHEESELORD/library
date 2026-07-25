package sh.harold.library.npc.behavior;

import java.util.Objects;

public record NpcVoiceProfile(NpcSoundProfile sounds, NpcVoiceDeliveryStyle deliveryStyle) {

    public NpcVoiceProfile {
        Objects.requireNonNull(sounds, "sounds");
        Objects.requireNonNull(deliveryStyle, "deliveryStyle");
    }
}
