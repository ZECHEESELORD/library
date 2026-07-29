package sh.harold.library.entity.paper;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaperOverheadLayerManagerTest {

    @Test
    void reservationRaisesBubbleAndClosesIdempotently() {
        PaperOverheadLayerManager layers = new PaperOverheadLayerManager();
        UUID npc = UUID.randomUUID();
        double base = layers.bubbleOffset(npc);
        PaperOverheadLayerManager.Reservation reservation = layers.reserveHouse(npc, 3);

        assertEquals(base + 0.9D, layers.bubbleOffset(npc), 0.0001D);
        reservation.close();
        reservation.close();
        assertEquals(base, layers.bubbleOffset(npc), 0.0001D);
        assertEquals(0, layers.reservationCount());
    }
}
