package sh.harold.creative.library.blockgrid.minestom;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.InstanceContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.blockgrid.BlockPos;
import sh.harold.creative.library.spatial.SpaceId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinestomBlockGridTest {

    private static boolean initialized;

    @BeforeAll
    static void initServer() {
        if (!initialized) {
            MinecraftServer.init();
            initialized = true;
        }
    }

    @Test
    void pointUsesMinestomBlockCoordinates() {
        assertEquals(new BlockPos(12, 64, -4), MinestomBlockGrid.blockPos(new Pos(12.75, 64.99, -3.2)));
    }

    @Test
    void spaceIdUsesTheExplicitResolver() {
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        SpaceId expected = SpaceId.of("creative", "instance");

        assertEquals(expected, MinestomBlockGrid.spaceId(instance, ignored -> expected));
    }
}
