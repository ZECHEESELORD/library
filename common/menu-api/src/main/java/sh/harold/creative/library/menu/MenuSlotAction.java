package sh.harold.creative.library.menu;

import java.util.Objects;

public sealed interface MenuSlotAction permits MenuSlotAction.Close, MenuSlotAction.Dispatch, MenuSlotAction.Execute, MenuSlotAction.OpenFrame {

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

    record Dispatch(Object message) implements MenuSlotAction {
    }

    record Close() implements MenuSlotAction {
    }
}
