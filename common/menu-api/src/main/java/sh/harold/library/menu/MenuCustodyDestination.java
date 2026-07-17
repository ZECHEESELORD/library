package sh.harold.library.menu;

import java.util.Objects;

public sealed interface MenuCustodyDestination permits MenuCustodyDestination.Cursor, MenuCustodyDestination.Origin,
        MenuCustodyDestination.Target, MenuCustodyDestination.ViewerSlot {

    static MenuCustodyDestination cursor() {
        return new Cursor();
    }

    static MenuCustodyDestination origin() {
        return new Origin();
    }

    static MenuCustodyDestination target(String key) {
        return new Target(key);
    }

    static MenuCustodyDestination viewerSlot(MenuViewerSlot slot) {
        return new ViewerSlot(slot);
    }

    record Cursor() implements MenuCustodyDestination {
    }

    record Origin() implements MenuCustodyDestination {
    }

    record Target(String key) implements MenuCustodyDestination {

        public Target {
            key = Objects.requireNonNull(key, "key");
            if (key.isBlank()) {
                throw new IllegalArgumentException("key cannot be blank");
            }
        }
    }

    record ViewerSlot(MenuViewerSlot slot) implements MenuCustodyDestination {

        public ViewerSlot {
            slot = Objects.requireNonNull(slot, "slot");
        }
    }
}
