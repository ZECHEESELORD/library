package sh.harold.library.example.feel.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import sh.harold.library.example.feel.FeelCourse;
import sh.harold.library.movement.fabric.client.InputScript;

/**
 * Placeholder for the movement feel gametests, which a later phase writes against the course
 * ({@link FeelCourse#build}) and the simulated-input hook ({@link InputScript}). It references both so an API change
 * breaks {@code check}, and does nothing when run.
 */
public final class FeelExampleGameTests implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        if (FeelCourse.STATIONS.isEmpty() || InputScript.parse("FS*2").size() != 2) {
            throw new AssertionError("course or input script helpers changed");
        }
    }
}
