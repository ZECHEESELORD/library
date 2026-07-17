package sh.harold.library.menu;

/**
 * A short-lived viewer-inventory observation used to reject stale custody decisions.
 *
 * <p>{@code item} is presentation-only and is {@code null} when the observed slot is empty.</p>
 */
public record MenuViewerSlot(long observationId, int slot, MenuStack item) {

    public MenuViewerSlot {
        if (observationId <= 0L) {
            throw new IllegalArgumentException("observationId must be greater than zero");
        }
        if (slot < 0) {
            throw new IllegalArgumentException("slot cannot be negative");
        }
    }
}
