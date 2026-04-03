package sh.harold.creative.library.boundary;

import net.kyori.adventure.key.Key;
import sh.harold.creative.library.blockgrid.BlockBounds;
import sh.harold.creative.library.spatial.SpaceId;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record BoundarySnapshot(Key id, SpaceId spaceId, BoundaryExtent extent, Set<BoundaryCapability> capabilities) {

    public BoundarySnapshot {
        id = Objects.requireNonNull(id, "id");
        spaceId = Objects.requireNonNull(spaceId, "spaceId");
        extent = Objects.requireNonNull(extent, "extent");
        capabilities = copyCapabilities(capabilities);

        boolean exactExtent = extent instanceof BoundaryExtent.ExactAabb;
        boolean exactCapability = capabilities.contains(BoundaryCapability.EXACT_AABB_BOUNDS);
        if (exactExtent != exactCapability) {
            throw new IllegalArgumentException("exact AABB bounds capability must match the published extent");
        }
    }

    public Optional<BlockBounds> exactBounds() {
        return extent.exactAabb();
    }

    public boolean supports(BoundaryCapability capability) {
        return capabilities.contains(Objects.requireNonNull(capability, "capability"));
    }

    private static Set<BoundaryCapability> copyCapabilities(Set<BoundaryCapability> capabilities) {
        Objects.requireNonNull(capabilities, "capabilities");
        EnumSet<BoundaryCapability> copy = EnumSet.noneOf(BoundaryCapability.class);
        copy.addAll(capabilities);
        return Set.copyOf(copy);
    }
}
