package sh.harold.library.entity.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaperPacketEventsRequirementTest {

    @Test
    void acceptsOnlyReadyStable2130OnSupportedProtocol() {
        assertDoesNotThrow(() -> PaperPacketEventsRequirement.verify(state(true, true, true, true, 2, 13, 0, false, "V_26_1_2")));
        assertThrows(IllegalStateException.class,
                () -> PaperPacketEventsRequirement.verify(state(true, true, true, true, 2, 13, 1, false, "V_26_1_2")));
        assertThrows(IllegalStateException.class,
                () -> PaperPacketEventsRequirement.verify(state(true, true, true, true, 2, 13, 0, true, "V_26_1_2")));
        assertThrows(IllegalStateException.class,
                () -> PaperPacketEventsRequirement.verify(state(true, true, true, true, 2, 13, 0, false, "V_26_2")));
    }

    @Test
    void rejectsEveryIncompletePluginLifecycleState() {
        assertThrows(IllegalStateException.class,
                () -> PaperPacketEventsRequirement.verify(state(false, false, false, false, -1, -1, -1, false, "unknown")));
        assertThrows(IllegalStateException.class,
                () -> PaperPacketEventsRequirement.verify(state(true, false, false, false, -1, -1, -1, false, "unknown")));
        assertThrows(IllegalStateException.class,
                () -> PaperPacketEventsRequirement.verify(state(true, true, false, false, 2, 13, 0, false, "V_26_1_2")));
        assertThrows(IllegalStateException.class,
                () -> PaperPacketEventsRequirement.verify(state(true, true, true, false, 2, 13, 0, false, "V_26_1_2")));
    }

    private static PaperPacketEventsRequirement.RuntimeState state(
            boolean present,
            boolean enabled,
            boolean loaded,
            boolean initialized,
            int major,
            int minor,
            int patch,
            boolean snapshot,
            String protocol
    ) {
        return new PaperPacketEventsRequirement.RuntimeState(
                present, enabled, loaded, initialized, major, minor, patch, snapshot, protocol
        );
    }
}
