package sh.harold.library.menu;

import java.util.Objects;

/**
 * A non-authoritative description of an exact native stack held by the menu runtime.
 */
public record MenuCustodyItem(MenuStack presentation, int originalViewerSlot) {

    public MenuCustodyItem {
        presentation = Objects.requireNonNull(presentation, "presentation");
        if (originalViewerSlot < 0) {
            throw new IllegalArgumentException("originalViewerSlot cannot be negative");
        }
    }
}
