package sh.harold.creative.library.spatial;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpatialApiTest {

    @Test
    void angleShortestDeltaWrapsAcrossPi() {
        Angle from = Angle.degrees(170.0);
        Angle to = Angle.degrees(-170.0);

        assertEquals(20.0, from.shortestDeltaTo(to).degrees(), 1.0e-6);
    }

    @Test
    void frameTransformsRoundTrip() {
        Frame3 frame = Frame3.of(new Vec3(5.0, 2.0, -3.0), new Vec3(0.0, 0.0, 1.0), new Vec3(0.0, 1.0, 0.0));
        Vec3 local = new Vec3(2.0, -1.0, 4.0);

        Vec3 world = frame.localToWorldPoint(local);
        Vec3 recovered = frame.worldToLocalPoint(world);

        assertEquals(local.x(), recovered.x(), 1.0e-6);
        assertEquals(local.y(), recovered.y(), 1.0e-6);
        assertEquals(local.z(), recovered.z(), 1.0e-6);
    }

    @Test
    void boundsContainCenterAndUnion() {
        Bounds3 first = new Bounds3(new Vec3(-1.0, -2.0, -3.0), new Vec3(1.0, 2.0, 3.0));
        Bounds3 second = new Bounds3(new Vec3(0.0, 0.0, 0.0), new Vec3(4.0, 4.0, 4.0));

        assertTrue(first.contains(first.center()));
        assertTrue(first.union(second).contains(new Vec3(4.0, 4.0, 4.0)));
    }

    @Test
    void spaceIdWrapsAdventureKey() {
        assertEquals(Key.key("creative", "arena"), SpaceId.of("creative", "arena").key());
    }

    @Test
    void anchorSnapshotTranslatesInLocalFrame() {
        AnchorSnapshot snapshot = new AnchorSnapshot(
                SpaceId.of("creative", "arena"),
                Frame3.world(new Vec3(10.0, 0.0, 5.0))
        );

        assertTrue(snapshot.translated(new Vec3(1.0, 2.0, 3.0)).frame().origin().distance(new Vec3(11.0, 2.0, 8.0)) < 1.0e-6);
    }
}
