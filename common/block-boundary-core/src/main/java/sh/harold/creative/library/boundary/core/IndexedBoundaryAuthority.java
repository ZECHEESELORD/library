package sh.harold.creative.library.boundary.core;

import net.kyori.adventure.key.Key;
import sh.harold.creative.library.blockgrid.BlockBounds;
import sh.harold.creative.library.blockgrid.BlockPos;
import sh.harold.creative.library.boundary.BoundaryDecision;
import sh.harold.creative.library.boundary.BoundaryDecisionQuery;
import sh.harold.creative.library.boundary.BoundaryDecisionReason;
import sh.harold.creative.library.boundary.BoundaryProvider;
import sh.harold.creative.library.boundary.BoundarySnapshot;
import sh.harold.creative.library.boundary.BoundaryTarget;
import sh.harold.creative.library.spatial.SpaceId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class IndexedBoundaryAuthority implements BoundaryProvider {

    private static final int CHUNK_WIDTH = 16;

    private final ConcurrentMap<SpaceId, SpaceIndex> spaces = new ConcurrentHashMap<>();

    public void upsert(BoundarySnapshot snapshot) {
        upsert(snapshot, BoundaryRule.allowAll());
    }

    public void upsert(BoundarySnapshot snapshot, BoundaryRule rule) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(rule, "rule");
        BlockBounds bounds = requireExactBounds(snapshot);
        spaces.computeIfAbsent(snapshot.spaceId(), ignored -> new SpaceIndex())
                .upsert(new BoundaryEntry(snapshot, bounds, rule));
    }

    public boolean remove(SpaceId spaceId, Key boundaryId) {
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(boundaryId, "boundaryId");
        SpaceIndex index = spaces.get(spaceId);
        if (index == null) {
            return false;
        }

        boolean removed = index.remove(boundaryId);
        if (removed && index.isEmpty()) {
            spaces.remove(spaceId, index);
        }
        return removed;
    }

    public void clearSpace(SpaceId spaceId) {
        spaces.remove(Objects.requireNonNull(spaceId, "spaceId"));
    }

    public void clearAll() {
        spaces.clear();
    }

    @Override
    public Optional<BoundarySnapshot> boundaryAt(SpaceId spaceId, BlockPos block) {
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(block, "block");
        SpaceIndex index = spaces.get(spaceId);
        return index == null ? Optional.empty() : index.boundaryAt(block);
    }

    @Override
    public List<BoundarySnapshot> boundariesIntersecting(SpaceId spaceId, BlockBounds bounds) {
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(bounds, "bounds");
        SpaceIndex index = spaces.get(spaceId);
        return index == null ? List.of() : index.boundariesIntersecting(bounds);
    }

    @Override
    public BoundaryDecision decide(BoundaryDecisionQuery query) {
        Objects.requireNonNull(query, "query");
        SpaceIndex index = spaces.get(query.spaceId());
        return index == null ? new BoundaryDecision(BoundaryDecisionReason.NO_BOUNDARY, null, true) : index.decide(query);
    }

    @Override
    public void close() {
        clearAll();
    }

    private static BlockBounds requireExactBounds(BoundarySnapshot snapshot) {
        return snapshot.extent().exactAabb()
                .orElseThrow(() -> new IllegalArgumentException("indexed authority requires exact AABB boundary extents"));
    }

    private static boolean exactReason(BoundaryDecisionReason reason) {
        return reason != BoundaryDecisionReason.ADAPTER_DEGRADED
                && reason != BoundaryDecisionReason.UNSUPPORTED_EXACT_BOUNDS;
    }

    private static final class SpaceIndex {

        private final Map<Key, BoundaryEntry> entries = new LinkedHashMap<>();
        private final Map<ColumnKey, LinkedHashSet<Key>> columns = new LinkedHashMap<>();

        synchronized void upsert(BoundaryEntry next) {
            Objects.requireNonNull(next, "next");
            for (Key candidateId : candidateIds(next.bounds())) {
                if (candidateId.equals(next.id())) {
                    continue;
                }
                BoundaryEntry candidate = entries.get(candidateId);
                if (candidate != null && candidate.bounds().intersects(next.bounds())) {
                    throw new IllegalArgumentException("boundary " + next.id() + " overlaps existing boundary " + candidate.id());
                }
            }

            BoundaryEntry existing = entries.put(next.id(), next);
            if (existing != null) {
                unlink(existing);
            }
            link(next);
        }

        synchronized boolean remove(Key boundaryId) {
            BoundaryEntry removed = entries.remove(boundaryId);
            if (removed == null) {
                return false;
            }
            unlink(removed);
            return true;
        }

        synchronized boolean isEmpty() {
            return entries.isEmpty();
        }

        synchronized Optional<BoundarySnapshot> boundaryAt(BlockPos block) {
            BoundaryEntry matched = null;
            LinkedHashSet<Key> candidates = columns.get(columnFor(block));
            if (candidates == null || candidates.isEmpty()) {
                return Optional.empty();
            }

            for (Key candidateId : candidates) {
                BoundaryEntry candidate = entries.get(candidateId);
                if (candidate == null || !candidate.bounds().contains(block)) {
                    continue;
                }
                if (matched != null) {
                    throw new IllegalStateException("multiple exact boundaries contain block " + block);
                }
                matched = candidate;
            }
            return matched == null ? Optional.empty() : Optional.of(matched.snapshot());
        }

        synchronized List<BoundarySnapshot> boundariesIntersecting(BlockBounds bounds) {
            ArrayList<BoundarySnapshot> matches = new ArrayList<>();
            for (Key candidateId : candidateIds(bounds)) {
                BoundaryEntry candidate = entries.get(candidateId);
                if (candidate != null && candidate.bounds().intersects(bounds)) {
                    matches.add(candidate.snapshot());
                }
            }
            return List.copyOf(matches);
        }

        synchronized BoundaryDecision decide(BoundaryDecisionQuery query) {
            return switch (query.target()) {
                case BoundaryTarget.SingleBlock singleBlock -> decideSingleBlock(query, singleBlock);
                case BoundaryTarget.Bounds bounds -> decideBounds(query, bounds);
                case BoundaryTarget.BlockSet blockSet -> decideBlockSet(query, blockSet);
            };
        }

        private BoundaryDecision decideSingleBlock(BoundaryDecisionQuery query, BoundaryTarget.SingleBlock singleBlock) {
            BoundaryEntry entry = entryAt(singleBlock.block());
            if (entry == null) {
                return new BoundaryDecision(BoundaryDecisionReason.NO_BOUNDARY, null, true);
            }
            return applyRule(entry, query);
        }

        private BoundaryDecision decideBounds(BoundaryDecisionQuery query, BoundaryTarget.Bounds target) {
            List<BoundaryEntry> intersections = intersectingEntries(target.bounds());
            if (intersections.isEmpty()) {
                return new BoundaryDecision(BoundaryDecisionReason.NO_BOUNDARY, null, true);
            }
            if (intersections.size() != 1) {
                return new BoundaryDecision(BoundaryDecisionReason.OUTSIDE_BOUNDARY, null, true);
            }

            BoundaryEntry entry = intersections.get(0);
            if (!entry.bounds().contains(target.bounds())) {
                return new BoundaryDecision(BoundaryDecisionReason.OUTSIDE_BOUNDARY, entry.snapshot(), true);
            }
            return applyRule(entry, query);
        }

        private BoundaryDecision decideBlockSet(BoundaryDecisionQuery query, BoundaryTarget.BlockSet target) {
            List<BoundaryEntry> intersections = intersectingEntries(target.bounds());
            if (intersections.isEmpty()) {
                return new BoundaryDecision(BoundaryDecisionReason.NO_BOUNDARY, null, true);
            }
            if (intersections.size() == 1 && intersections.get(0).bounds().contains(target.bounds())) {
                return applyRule(intersections.get(0), query);
            }

            BoundaryEntry matched = null;
            boolean unbounded = false;
            for (BlockPos block : target.blocks()) {
                BoundaryEntry entry = entryAt(block);
                if (entry == null) {
                    unbounded = true;
                    continue;
                }
                if (matched == null) {
                    matched = entry;
                    continue;
                }
                if (!matched.id().equals(entry.id())) {
                    return new BoundaryDecision(BoundaryDecisionReason.OUTSIDE_BOUNDARY, null, true);
                }
            }

            if (matched == null) {
                return new BoundaryDecision(BoundaryDecisionReason.NO_BOUNDARY, null, true);
            }
            if (unbounded) {
                return new BoundaryDecision(BoundaryDecisionReason.OUTSIDE_BOUNDARY, matched.snapshot(), true);
            }
            return applyRule(matched, query);
        }

        private BoundaryDecision applyRule(BoundaryEntry entry, BoundaryDecisionQuery query) {
            BoundaryDecisionReason reason = Objects.requireNonNull(entry.rule().decide(query), "reason");
            return new BoundaryDecision(reason, entry.snapshot(), exactReason(reason));
        }

        private BoundaryEntry entryAt(BlockPos block) {
            LinkedHashSet<Key> candidates = columns.get(columnFor(block));
            if (candidates == null || candidates.isEmpty()) {
                return null;
            }

            BoundaryEntry matched = null;
            for (Key candidateId : candidates) {
                BoundaryEntry candidate = entries.get(candidateId);
                if (candidate == null || !candidate.bounds().contains(block)) {
                    continue;
                }
                if (matched != null) {
                    throw new IllegalStateException("multiple exact boundaries contain block " + block);
                }
                matched = candidate;
            }
            return matched;
        }

        private List<BoundaryEntry> intersectingEntries(BlockBounds bounds) {
            ArrayList<BoundaryEntry> matches = new ArrayList<>();
            for (Key candidateId : candidateIds(bounds)) {
                BoundaryEntry candidate = entries.get(candidateId);
                if (candidate != null && candidate.bounds().intersects(bounds)) {
                    matches.add(candidate);
                }
            }
            return matches;
        }

        private LinkedHashSet<Key> candidateIds(BlockBounds bounds) {
            LinkedHashSet<Key> ids = new LinkedHashSet<>();
            int minChunkX = chunk(bounds.min().x());
            int maxChunkX = chunk(bounds.max().x());
            int minChunkZ = chunk(bounds.min().z());
            int maxChunkZ = chunk(bounds.max().z());
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    LinkedHashSet<Key> column = columns.get(new ColumnKey(chunkX, chunkZ));
                    if (column != null) {
                        ids.addAll(column);
                    }
                }
            }
            return ids;
        }

        private void link(BoundaryEntry entry) {
            for (int chunkX = entry.minChunkX(); chunkX <= entry.maxChunkX(); chunkX++) {
                for (int chunkZ = entry.minChunkZ(); chunkZ <= entry.maxChunkZ(); chunkZ++) {
                    columns.computeIfAbsent(new ColumnKey(chunkX, chunkZ), ignored -> new LinkedHashSet<>()).add(entry.id());
                }
            }
        }

        private void unlink(BoundaryEntry entry) {
            for (int chunkX = entry.minChunkX(); chunkX <= entry.maxChunkX(); chunkX++) {
                for (int chunkZ = entry.minChunkZ(); chunkZ <= entry.maxChunkZ(); chunkZ++) {
                    ColumnKey key = new ColumnKey(chunkX, chunkZ);
                    LinkedHashSet<Key> column = columns.get(key);
                    if (column == null) {
                        continue;
                    }
                    column.remove(entry.id());
                    if (column.isEmpty()) {
                        columns.remove(key);
                    }
                }
            }
        }

        private static ColumnKey columnFor(BlockPos block) {
            return new ColumnKey(chunk(block.x()), chunk(block.z()));
        }
    }

    private record BoundaryEntry(
            BoundarySnapshot snapshot,
            BlockBounds bounds,
            BoundaryRule rule,
            int minChunkX,
            int maxChunkX,
            int minChunkZ,
            int maxChunkZ
    ) {

        private BoundaryEntry(BoundarySnapshot snapshot, BlockBounds bounds, BoundaryRule rule) {
            this(
                    Objects.requireNonNull(snapshot, "snapshot"),
                    Objects.requireNonNull(bounds, "bounds"),
                    Objects.requireNonNull(rule, "rule"),
                    chunk(bounds.min().x()),
                    chunk(bounds.max().x()),
                    chunk(bounds.min().z()),
                    chunk(bounds.max().z())
            );
        }

        private Key id() {
            return snapshot.id();
        }
    }

    private record ColumnKey(int chunkX, int chunkZ) {
    }

    private static int chunk(int coordinate) {
        return Math.floorDiv(coordinate, CHUNK_WIDTH);
    }
}
