package sh.harold.creative.library.ambient;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.spatial.AnchorRef;
import sh.harold.creative.library.spatial.AnchorSnapshot;
import sh.harold.creative.library.spatial.Bounds3;
import sh.harold.creative.library.spatial.Frame3;
import sh.harold.creative.library.spatial.SpaceId;
import sh.harold.creative.library.spatial.Vec3;
import sh.harold.creative.library.spatial.Volume;
import sh.harold.creative.library.tick.InstanceConflictPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AmbientApiTest {

    @Test
    void zoneSpecCarriesTtl() {
        ZoneSpec spec = new ZoneSpec(
                Key.key("test", "mist"),
                new AnchorRef.Fixed(new AnchorSnapshot(SpaceId.of("creative", "arena"), Frame3.world(Vec3.ZERO))),
                new TestVolume(),
                new AmbientProfile(0.5, null, null, null, null),
                AmbientBlendMode.MAX,
                AmbientWeightModel.hardEdge(),
                0,
                20L,
                InstanceConflictPolicy.REPLACE
        );

        assertEquals(20L, spec.ttlTicks());
    }

    private static final class TestVolume implements Volume {

        @Override
        public boolean contains(Vec3 point) {
            return true;
        }

        @Override
        public Vec3 nearestPoint(Vec3 point) {
            return point;
        }

        @Override
        public double distanceToBoundary(Vec3 point) {
            return 0.0;
        }

        @Override
        public Bounds3 bounds() {
            return Bounds3.point(Vec3.ZERO);
        }
    }
}
