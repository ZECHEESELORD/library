package sh.harold.library.npc.behavior.core;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A positional sound command. An empty recipient is shared with all currently
 * permitted tracking viewers; a present recipient is a viewer-only branch.
 */
public record NpcRenderedSound(
        Key key,
        Sound.Source source,
        float volume,
        float pitch,
        Optional<UUID> recipient
) {
    public NpcRenderedSound {
        key = Objects.requireNonNull(key, "key");
        source = Objects.requireNonNull(source, "source");
        if (!Float.isFinite(volume) || volume < 0.0f) {
            throw new IllegalArgumentException("volume must be finite and non-negative");
        }
        if (!Float.isFinite(pitch) || pitch <= 0.0f) {
            throw new IllegalArgumentException("pitch must be finite and positive");
        }
        recipient = Objects.requireNonNull(recipient, "recipient");
    }

    public static NpcRenderedSound shared(Key key, Sound.Source source, float volume, float pitch) {
        return new NpcRenderedSound(key, source, volume, pitch, Optional.empty());
    }

    public static NpcRenderedSound viewer(
            UUID viewerId,
            Key key,
            Sound.Source source,
            float volume,
            float pitch
    ) {
        return new NpcRenderedSound(key, source, volume, pitch, Optional.of(viewerId));
    }
}
