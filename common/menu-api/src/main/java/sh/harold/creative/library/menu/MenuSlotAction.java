package sh.harold.creative.library.menu;

import java.util.Objects;

public sealed interface MenuSlotAction permits MenuSlotAction.Execute, MenuSlotAction.OpenFrame, MenuSlotAction.Close {

    record Execute(MenuAction action) implements MenuSlotAction {

        public Execute {
            action = Objects.requireNonNull(action, "action");
        }
    }

    record OpenFrame(String frameId) implements MenuSlotAction {

        public OpenFrame {
            Objects.requireNonNull(frameId, "frameId");
            if (frameId.isBlank()) {
                throw new IllegalArgumentException("frameId cannot be blank");
            }
        }
    }

    record Close() implements MenuSlotAction {
    }
}
