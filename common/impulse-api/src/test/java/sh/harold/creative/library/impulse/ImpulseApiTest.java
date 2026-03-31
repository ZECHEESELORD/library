package sh.harold.creative.library.impulse;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.tick.InstanceConflictPolicy;
import sh.harold.creative.library.tween.Envelope;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImpulseApiTest {

    @Test
    void impulseSpecCarriesBasicConfiguration() {
        ImpulseSpec spec = new ImpulseSpec(
                Key.key("test", "push"),
                ImpulseMode.ADD_VELOCITY,
                new ImpulseVector.World(new sh.harold.creative.library.spatial.Vec3(1.0, 0.0, 0.0)),
                0L,
                5L,
                Envelope.constant(1.0),
                AxisMask.ALL,
                ImpulseStackMode.ADD,
                0.0,
                0,
                InstanceConflictPolicy.REPLACE
        );

        assertEquals(5L, spec.durationTicks());
    }
}
