package sh.harold.creative.library.boundary;

import sh.harold.creative.library.blockgrid.BlockBounds;
import sh.harold.creative.library.blockgrid.BlockPos;
import sh.harold.creative.library.spatial.SpaceId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public sealed interface BoundaryTarget permits BoundaryTarget.BlockSet, BoundaryTarget.Bounds, BoundaryTarget.SingleBlock {

    SpaceId spaceId();

    record SingleBlock(SpaceId spaceId, BlockPos block) implements BoundaryTarget {

        public SingleBlock {
            spaceId = Objects.requireNonNull(spaceId, "spaceId");
            block = Objects.requireNonNull(block, "block");
        }
    }

    record Bounds(SpaceId spaceId, BlockBounds bounds) implements BoundaryTarget {

        public Bounds {
            spaceId = Objects.requireNonNull(spaceId, "spaceId");
            bounds = Objects.requireNonNull(bounds, "bounds");
        }
    }

    record BlockSet(SpaceId spaceId, List<BlockPos> blocks) implements BoundaryTarget {

        public BlockSet {
            spaceId = Objects.requireNonNull(spaceId, "spaceId");
            blocks = normalizeBlocks(blocks);
        }

        public BlockBounds bounds() {
            BlockPos min = blocks.get(0);
            BlockPos max = min;
            for (int index = 1; index < blocks.size(); index++) {
                BlockPos block = blocks.get(index);
                min = min.min(block);
                max = max.max(block);
            }
            return new BlockBounds(min, max);
        }

        private static List<BlockPos> normalizeBlocks(List<BlockPos> blocks) {
            Objects.requireNonNull(blocks, "blocks");
            if (blocks.isEmpty()) {
                throw new IllegalArgumentException("blocks must not be empty");
            }

            LinkedHashSet<BlockPos> deduped = new LinkedHashSet<>();
            for (BlockPos block : blocks) {
                deduped.add(Objects.requireNonNull(block, "blocks must not contain null"));
            }
            return List.copyOf(new ArrayList<>(deduped));
        }
    }
}
