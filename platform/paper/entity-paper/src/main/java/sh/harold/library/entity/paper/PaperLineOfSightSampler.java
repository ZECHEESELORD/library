package sh.harold.library.entity.paper;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Samples short attention rays without ever loading a chunk or reading one from
 * a foreign Folia region. Results carry the request epoch so callers can reject
 * stale probes after a viewer or actor changes space.
 */
final class PaperLineOfSightSampler {
    private static final double SAMPLE_STEP = 0.2D;

    private final Plugin plugin;

    PaperLineOfSightSampler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    CompletionStage<Result> sample(World world, Vector from, Vector to, long epoch) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");

        Map<ChunkKey, List<BlockPoint>> segments = splitByChunk(from, to);
        if (segments.isEmpty()) {
            return CompletableFuture.completedFuture(new Result(epoch, true));
        }

        List<CompletableFuture<Boolean>> checks = new ArrayList<>(segments.size());
        segments.forEach((chunk, points) -> {
            CompletableFuture<Boolean> check = new CompletableFuture<>();
            checks.add(check);
            try {
                plugin.getServer().getRegionScheduler().execute(
                        plugin,
                        world,
                        chunk.x(),
                        chunk.z(),
                        () -> check.complete(checkOwnedChunk(world, chunk, points))
                );
            } catch (Throwable failure) {
                check.complete(false);
            }
        });

        return CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new))
                .handle((ignored, failure) -> {
                    if (failure != null) {
                        return new Result(epoch, false);
                    }
                    return new Result(epoch, checks.stream().allMatch(future -> Boolean.TRUE.equals(future.getNow(false))));
                });
    }

    private static boolean checkOwnedChunk(World world, ChunkKey chunk, List<BlockPoint> points) {
        try {
            if (!world.isChunkLoaded(chunk.x(), chunk.z())) {
                return false;
            }
            for (BlockPoint point : points) {
                if (!world.getBlockAt(point.x(), point.y(), point.z()).isPassable()) {
                    return false;
                }
            }
            return true;
        } catch (Throwable failure) {
            return false;
        }
    }

    static Map<ChunkKey, List<BlockPoint>> splitByChunk(Vector from, Vector to) {
        Vector delta = to.clone().subtract(from);
        double length = delta.length();
        int samples = Math.max(1, (int) Math.ceil(length / SAMPLE_STEP));
        Map<ChunkKey, List<BlockPoint>> chunks = new LinkedHashMap<>();

        // Skip the exact endpoints: the source mannequin and target player's
        // occupied blocks must not invalidate their own sight line.
        for (int index = 1; index < samples; index++) {
            double progress = (double) index / samples;
            int x = floor(from.getX() + delta.getX() * progress);
            int y = floor(from.getY() + delta.getY() * progress);
            int z = floor(from.getZ() + delta.getZ() * progress);
            ChunkKey key = new ChunkKey(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
            List<BlockPoint> points = chunks.computeIfAbsent(key, ignored -> new ArrayList<>());
            BlockPoint point = new BlockPoint(x, y, z);
            if (points.isEmpty() || !points.getLast().equals(point)) {
                points.add(point);
            }
        }
        return Map.copyOf(chunks);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    record Result(long epoch, boolean clear) {
    }

    record ChunkKey(int x, int z) {
    }

    record BlockPoint(int x, int y, int z) {
    }
}
