package sh.harold.library.entity.paper;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperLineOfSightSamplerTest {

    @Test
    void splitsRayAtPositiveAndNegativeChunkBoundaries() {
        Map<PaperLineOfSightSampler.ChunkKey, ?> result = PaperLineOfSightSampler.splitByChunk(
                new Vector(-0.5, 64.5, 0.5),
                new Vector(16.5, 64.5, 0.5)
        );

        assertEquals(3, result.size());
        assertTrue(result.containsKey(new PaperLineOfSightSampler.ChunkKey(-1, 0)));
        assertTrue(result.containsKey(new PaperLineOfSightSampler.ChunkKey(0, 0)));
        assertTrue(result.containsKey(new PaperLineOfSightSampler.ChunkKey(1, 0)));
    }

    @Test
    void excludesExactRayEndpoints() {
        Map<PaperLineOfSightSampler.ChunkKey, ?> result = PaperLineOfSightSampler.splitByChunk(
                new Vector(1.1, 64.1, 1.1),
                new Vector(1.2, 64.2, 1.2)
        );

        assertTrue(result.isEmpty());
        assertFalse(result.containsKey(new PaperLineOfSightSampler.ChunkKey(0, 0)));
    }
}
