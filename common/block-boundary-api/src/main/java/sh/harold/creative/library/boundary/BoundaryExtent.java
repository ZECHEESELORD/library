package sh.harold.creative.library.boundary;

import sh.harold.creative.library.blockgrid.BlockBounds;

import java.util.Objects;
import java.util.Optional;

public sealed interface BoundaryExtent permits BoundaryExtent.ExactAabb, BoundaryExtent.Unavailable {

    default Optional<BlockBounds> exactAabb() {
        return Optional.empty();
    }

    static ExactAabb exactAabb(BlockBounds bounds) {
        return new ExactAabb(bounds);
    }

    static Unavailable unavailable() {
        return Unavailable.INSTANCE;
    }

    record ExactAabb(BlockBounds bounds) implements BoundaryExtent {

        public ExactAabb {
            bounds = Objects.requireNonNull(bounds, "bounds");
        }

        @Override
        public Optional<BlockBounds> exactAabb() {
            return Optional.of(bounds);
        }
    }

    enum Unavailable implements BoundaryExtent {
        INSTANCE
    }
}
