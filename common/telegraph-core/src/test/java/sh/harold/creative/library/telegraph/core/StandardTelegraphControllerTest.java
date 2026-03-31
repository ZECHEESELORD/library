package sh.harold.creative.library.telegraph.core;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.spatial.AnchorRef;
import sh.harold.creative.library.spatial.AnchorResolver;
import sh.harold.creative.library.spatial.AnchorSnapshot;
import sh.harold.creative.library.spatial.Frame3;
import sh.harold.creative.library.spatial.SpaceId;
import sh.harold.creative.library.spatial.Vec3;
import sh.harold.creative.library.telegraph.TelegraphFrame;
import sh.harold.creative.library.telegraph.TelegraphShape;
import sh.harold.creative.library.telegraph.TelegraphSpec;
import sh.harold.creative.library.telegraph.TelegraphTiming;
import sh.harold.creative.library.telegraph.ViewerScope;
import sh.harold.creative.library.tick.InstanceConflictPolicy;
import sh.harold.creative.library.tick.KeyedHandle;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardTelegraphControllerTest {

    @Test
    void sameKeyReplacementInvalidatesOldHandle() {
        StandardTelegraphController controller = new StandardTelegraphController();
        KeyedHandle first = controller.start(spec("warning"));
        KeyedHandle second = controller.start(spec("warning"));

        assertFalse(first.active());
        assertTrue(second.active());
    }

    @Test
    void refreshPreservesHandleAndRestartsTiming() {
        StandardTelegraphController controller = new StandardTelegraphController();
        KeyedHandle handle = controller.start(spec("warning"));

        controller.tick(anchorResolver());
        controller.start(spec("warning", InstanceConflictPolicy.REFRESH));

        assertTrue(handle.active());
        TelegraphFrame refreshed = controller.tick(anchorResolver()).getFirst();
        assertEquals(0L, refreshed.ageTicks());
        assertEquals(0.5, refreshed.alpha(), 1.0e-6);
    }

    @Test
    void telegraphFadesInHoldsAndExpires() {
        StandardTelegraphController controller = new StandardTelegraphController();
        controller.start(spec("warning"));

        List<TelegraphFrame> tick0 = controller.tick(anchorResolver());
        assertEquals(0.5, tick0.getFirst().alpha(), 1.0e-6);

        List<TelegraphFrame> tick1 = controller.tick(anchorResolver());
        assertEquals(1.0, tick1.getFirst().alpha(), 1.0e-6);

        List<TelegraphFrame> tick2 = controller.tick(anchorResolver());
        assertEquals(1.0, tick2.getFirst().alpha(), 1.0e-6);

        List<TelegraphFrame> tick3 = controller.tick(anchorResolver());
        assertEquals(1.0, tick3.getFirst().alpha(), 1.0e-6);

        List<TelegraphFrame> tick4 = controller.tick(anchorResolver());
        assertEquals(0.5, tick4.getFirst().alpha(), 1.0e-6);

        assertTrue(controller.tick(anchorResolver()).isEmpty());
        assertFalse(controller.hasActiveTelegraphs());
    }

    @Test
    void missingAnchorRemovesTelegraph() {
        StandardTelegraphController controller = new StandardTelegraphController();
        controller.start(spec("warning"));

        assertTrue(controller.tick(anchor -> Optional.empty()).isEmpty());
        assertFalse(controller.hasActiveTelegraphs());
    }

    private static TelegraphSpec spec(String key) {
        return spec(key, InstanceConflictPolicy.REPLACE);
    }

    private static TelegraphSpec spec(String key, InstanceConflictPolicy conflictPolicy) {
        return new TelegraphSpec(
                Key.key("test", key),
                new AnchorRef.Fixed(new AnchorSnapshot(SpaceId.of("creative", "arena"), Frame3.world(Vec3.ZERO))),
                new TelegraphShape.Circle(2.0),
                new ViewerScope.Everyone(),
                new TelegraphTiming(2L, 1L, 2L, 1.0, 0.5),
                conflictPolicy,
                0
        );
    }

    private static AnchorResolver anchorResolver() {
        return anchor -> Optional.of(((AnchorRef.Fixed) anchor).snapshot());
    }
}
