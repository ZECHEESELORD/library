package sh.harold.library.telegraph;

import org.junit.jupiter.api.Test;
import sh.harold.library.spatial.AnchorRef;
import sh.harold.library.spatial.AnchorSnapshot;
import sh.harold.library.spatial.Frame3;
import sh.harold.library.spatial.SpaceId;
import sh.harold.library.spatial.Vec3;
import sh.harold.library.tick.InstanceConflictPolicy;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelegraphApiTest {

    @Test
    void telegraphSpecCarriesPriorityAndViewerScope() {
        TelegraphSpec spec = new TelegraphSpec(
                net.kyori.adventure.key.Key.key("test", "ring"),
                new AnchorRef.Fixed(new AnchorSnapshot(SpaceId.of("creative", "arena"), Frame3.world(Vec3.ZERO))),
                new TelegraphShape.Circle(2.0),
                new ViewerScope.SourceOnly(UUID.randomUUID()),
                new TelegraphTiming(2L, 5L, 2L, 0.8, 1.0),
                InstanceConflictPolicy.REPLACE,
                7
        );

        assertEquals(7, spec.priority());
    }
}
