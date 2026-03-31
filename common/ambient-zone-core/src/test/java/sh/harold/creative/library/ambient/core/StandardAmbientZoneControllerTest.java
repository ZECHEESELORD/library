package sh.harold.creative.library.ambient.core;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.ambient.AmbientBlendMode;
import sh.harold.creative.library.ambient.AmbientProfile;
import sh.harold.creative.library.ambient.AmbientSnapshot;
import sh.harold.creative.library.ambient.AmbientWeightModel;
import sh.harold.creative.library.ambient.ViewerAmbientState;
import sh.harold.creative.library.ambient.WeightCurve;
import sh.harold.creative.library.ambient.ZoneSpec;
import sh.harold.creative.library.spatial.AnchorRef;
import sh.harold.creative.library.spatial.AnchorResolver;
import sh.harold.creative.library.spatial.AnchorSnapshot;
import sh.harold.creative.library.spatial.Frame3;
import sh.harold.creative.library.spatial.SpaceId;
import sh.harold.creative.library.spatial.Vec3;
import sh.harold.creative.library.spatial.core.SphereVolume;
import sh.harold.creative.library.tick.InstanceConflictPolicy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardAmbientZoneControllerTest {

    @Test
    void hardEdgeZoneAppliesFullProfileInside() {
        StandardAmbientZoneController controller = new StandardAmbientZoneController();
        controller.start(zone("mist", 0.6, AmbientBlendMode.MAX, 0, 0L));

        AmbientSnapshot snapshot = controller.tick(List.of(viewer(Vec3.ZERO)), anchorResolver()).getFirst();
        assertEquals(0.6, snapshot.profile().overlayStrength(), 1.0e-6);
    }

    @Test
    void featherDistanceFallsOffOutsideBoundary() {
        StandardAmbientZoneController controller = new StandardAmbientZoneController();
        controller.start(new ZoneSpec(
                Key.key("test", "mist"),
                new AnchorRef.Fixed(new AnchorSnapshot(SpaceId.of("creative", "arena"), Frame3.world(Vec3.ZERO))),
                new SphereVolume(Vec3.ZERO, 2.0),
                new AmbientProfile(1.0, null, null, null, null),
                AmbientBlendMode.MAX,
                new AmbientWeightModel(2.0, WeightCurve.LINEAR),
                0,
                0L,
                InstanceConflictPolicy.REPLACE
        ));

        AmbientSnapshot snapshot = controller.tick(List.of(viewer(new Vec3(3.0, 0.0, 0.0))), anchorResolver()).getFirst();
        assertEquals(0.5, snapshot.profile().overlayStrength(), 1.0e-6);
    }

    @Test
    void priorityWinnerChoosesHighestPriorityContribution() {
        StandardAmbientZoneController controller = new StandardAmbientZoneController();
        controller.start(zone("low", 0.2, AmbientBlendMode.PRIORITY_WINNER, 1, 0L));
        controller.start(zone("high", 0.8, AmbientBlendMode.PRIORITY_WINNER, 5, 0L));

        AmbientSnapshot snapshot = controller.tick(List.of(viewer(Vec3.ZERO)), anchorResolver()).getFirst();
        assertEquals(0.8, snapshot.profile().overlayStrength(), 1.0e-6);
    }

    @Test
    void refreshPreservesHandleAndResetsTtl() {
        StandardAmbientZoneController controller = new StandardAmbientZoneController();
        var handle = controller.start(zone("mist", 0.4, AmbientBlendMode.MAX, 0, 1L, InstanceConflictPolicy.REPLACE));

        controller.tick(List.of(viewer(Vec3.ZERO)), anchorResolver());
        controller.start(zone("mist", 0.7, AmbientBlendMode.MAX, 0, 1L, InstanceConflictPolicy.REFRESH));

        assertTrue(handle.active());
        AmbientSnapshot snapshot = controller.tick(List.of(viewer(Vec3.ZERO)), anchorResolver()).getFirst();
        assertEquals(0.7, snapshot.profile().overlayStrength(), 1.0e-6);
    }

    @Test
    void ttlExpirationRemovesZone() {
        StandardAmbientZoneController controller = new StandardAmbientZoneController();
        controller.start(zone("short", 0.4, AmbientBlendMode.MAX, 0, 1L));

        controller.tick(List.of(viewer(Vec3.ZERO)), anchorResolver());
        AmbientSnapshot snapshot = controller.tick(List.of(viewer(Vec3.ZERO)), anchorResolver()).getFirst();
        assertNull(snapshot.profile().overlayStrength());
    }

    private static ZoneSpec zone(String key, double overlay, AmbientBlendMode blendMode, int priority, long ttlTicks) {
        return zone(key, overlay, blendMode, priority, ttlTicks, InstanceConflictPolicy.REPLACE);
    }

    private static ZoneSpec zone(
            String key,
            double overlay,
            AmbientBlendMode blendMode,
            int priority,
            long ttlTicks,
            InstanceConflictPolicy conflictPolicy
    ) {
        return new ZoneSpec(
                Key.key("test", key),
                new AnchorRef.Fixed(new AnchorSnapshot(SpaceId.of("creative", "arena"), Frame3.world(Vec3.ZERO))),
                new SphereVolume(Vec3.ZERO, 2.0),
                new AmbientProfile(overlay, 0.1, 0.1, 0.1, 0.1),
                blendMode,
                AmbientWeightModel.hardEdge(),
                priority,
                ttlTicks,
                conflictPolicy
        );
    }

    private static ViewerAmbientState viewer(Vec3 position) {
        return new ViewerAmbientState(UUID.randomUUID(), SpaceId.of("creative", "arena"), position);
    }

    private static AnchorResolver anchorResolver() {
        return anchor -> Optional.of(((AnchorRef.Fixed) anchor).snapshot());
    }
}
