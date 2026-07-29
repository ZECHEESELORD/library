package sh.harold.library.entity.paper;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperLifecycleGateTest {

    @Test
    void shutdownSnapshotContainsEveryAcceptedRegistrationAndRejectsLaterOnes() {
        PaperEntityPlatform.LifecycleGate gate = new PaperEntityPlatform.LifecycleGate();
        List<Integer> registrations = new ArrayList<>();

        assertTrue(gate.ifOpen(() -> registrations.add(1)));
        Optional<List<Integer>> snapshot = gate.closeAndSnapshot(() -> List.copyOf(registrations));

        assertEquals(List.of(1), snapshot.orElseThrow());
        assertTrue(gate.closed());
        assertFalse(gate.ifOpen(() -> registrations.add(2)));
        assertTrue(gate.closeAndSnapshot(() -> List.copyOf(registrations)).isEmpty());
        assertThrows(IllegalStateException.class, () -> gate.requireOpen(() -> 3));
        assertEquals(List.of(1), registrations);
    }

    @Test
    void failedSnapshotDoesNotPoisonTheOpenGate() {
        PaperEntityPlatform.LifecycleGate gate = new PaperEntityPlatform.LifecycleGate();

        assertThrows(IllegalStateException.class, () -> gate.closeAndSnapshot(() -> {
            throw new IllegalStateException("not owned");
        }));

        assertFalse(gate.closed());
        assertEquals(4, gate.requireOpen(() -> 4));
    }
}
