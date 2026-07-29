package sh.harold.library.entity.paper;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Reserves vertical space without changing an existing permanent label. */
final class PaperOverheadLayerManager {
    static final double HOUSE_LINE_HEIGHT = 0.3D;
    static final double BASE_HUMANOID_HEIGHT = 2.15D;
    static final double BUBBLE_GAP = 0.18D;

    private final Map<UUID, Double> reservations = new ConcurrentHashMap<>();

    Reservation reserveHouse(UUID entityId, int lineCount) {
        Objects.requireNonNull(entityId, "entityId");
        if (lineCount < 0) {
            throw new IllegalArgumentException("lineCount cannot be negative");
        }
        double height = lineCount * HOUSE_LINE_HEIGHT;
        reservations.put(entityId, height);
        return new Reservation(this, entityId, height);
    }

    double bubbleOffset(UUID entityId) {
        return BASE_HUMANOID_HEIGHT + reservations.getOrDefault(entityId, 0.0D) + BUBBLE_GAP;
    }

    int reservationCount() {
        return reservations.size();
    }

    void clear() {
        reservations.clear();
    }

    private void release(UUID entityId, double expectedHeight) {
        reservations.remove(entityId, expectedHeight);
    }

    static final class Reservation implements AutoCloseable {
        private final PaperOverheadLayerManager owner;
        private final UUID entityId;
        private final double height;
        private boolean closed;

        private Reservation(PaperOverheadLayerManager owner, UUID entityId, double height) {
            this.owner = owner;
            this.entityId = entityId;
            this.height = height;
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                closed = true;
                owner.release(entityId, height);
            }
        }
    }
}
