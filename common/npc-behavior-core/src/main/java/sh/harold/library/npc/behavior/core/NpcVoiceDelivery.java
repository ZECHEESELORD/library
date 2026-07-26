package sh.harold.library.npc.behavior.core;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import sh.harold.library.npc.behavior.NpcSoundProfile;
import sh.harold.library.npc.behavior.NpcVoiceDeliveryStyle;
import sh.harold.library.npc.behavior.NpcVoiceProfile;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Selects one equal-weight variant and applies the authored delivery color. */
final class NpcVoiceDelivery {
    private NpcVoiceDelivery() {
    }

    static Optional<Cue> select(NpcVoiceProfile voice, NpcBehaviorRandom random) {
        Objects.requireNonNull(voice, "voice");
        Objects.requireNonNull(random, "random");
        NpcSoundProfile sounds = voice.sounds();
        if (sounds.silent()) {
            return Optional.empty();
        }
        NpcSoundProfile.Variant variant = sounds.variants().get(random.nextInt(0, sounds.variants().size()));
        float basePitch = variant.minimumPitch()
                + (float) random.nextDouble() * (variant.maximumPitch() - variant.minimumPitch());
        Delivery delivery = Delivery.forStyle(voice.deliveryStyle());
        return Optional.of(new Cue(
                variant.key(),
                variant.source(),
                variant.volume() * delivery.volumeMultiplier,
                basePitch * delivery.pitchMultiplier
        ));
    }

    record Cue(Key key, Sound.Source source, float volume, float pitch) {
        Cue {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(source, "source");
        }

        NpcRenderedSound shared() {
            return NpcRenderedSound.shared(key, source, volume, pitch);
        }

        NpcRenderedSound viewer(UUID viewerId) {
            return NpcRenderedSound.viewer(viewerId, key, source, volume, pitch);
        }
    }

    private record Delivery(float volumeMultiplier, float pitchMultiplier) {
        private static Delivery forStyle(NpcVoiceDeliveryStyle style) {
            return switch (Objects.requireNonNull(style, "style")) {
                case NEUTRAL -> new Delivery(1.00f, 1.00f);
                case SOFT -> new Delivery(0.72f, 0.96f);
                case BRIGHT -> new Delivery(0.95f, 1.12f);
                case GRUFF -> new Delivery(1.05f, 0.82f);
                case HESITANT -> new Delivery(0.78f, 0.91f);
                case SLEEPY -> new Delivery(0.65f, 0.78f);
            };
        }
    }
}
