package sh.harold.creative.library.ambient;

import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AmbientSnapshot(UUID viewerId, AmbientProfile profile, List<Key> contributingZones) {

    public AmbientSnapshot {
        viewerId = Objects.requireNonNull(viewerId, "viewerId");
        profile = Objects.requireNonNull(profile, "profile");
        contributingZones = List.copyOf(Objects.requireNonNull(contributingZones, "contributingZones"));
    }
}
