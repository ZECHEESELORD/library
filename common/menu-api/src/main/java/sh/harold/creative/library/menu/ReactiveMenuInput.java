package sh.harold.creative.library.menu;

import java.util.List;
import java.util.Objects;

public sealed interface ReactiveMenuInput permits ReactiveMenuInput.Click, ReactiveMenuInput.Closed, ReactiveMenuInput.Drag,
        ReactiveMenuInput.DropCursor, ReactiveMenuInput.InventoryClick, ReactiveMenuInput.Opened,
        ReactiveMenuInput.TextPromptCancelled, ReactiveMenuInput.TextPromptSubmitted, ReactiveMenuInput.Tick {

    record Opened() implements ReactiveMenuInput {
    }

    record Closed() implements ReactiveMenuInput {
    }

    record Tick(long tick) implements ReactiveMenuInput {

        public Tick {
            if (tick < 0L) {
                throw new IllegalArgumentException("tick cannot be negative");
            }
        }
    }

    record TextPromptSubmitted(String key, String value, ReactiveTextPromptMode mode) implements ReactiveMenuInput {

        public TextPromptSubmitted {
            Objects.requireNonNull(key, "key");
            if (key.isBlank()) {
                throw new IllegalArgumentException("key cannot be blank");
            }
            value = value == null ? "" : value;
            mode = Objects.requireNonNull(mode, "mode");
        }
    }

    record TextPromptCancelled(String key, ReactiveTextPromptMode mode) implements ReactiveMenuInput {

        public TextPromptCancelled {
            Objects.requireNonNull(key, "key");
            if (key.isBlank()) {
                throw new IllegalArgumentException("key cannot be blank");
            }
            mode = Objects.requireNonNull(mode, "mode");
        }
    }

    record Click(int slot, MenuClick button, boolean shift, Object message, MenuStack cursor, MenuStack slotItem) implements ReactiveMenuInput {

        public Click {
            Objects.requireNonNull(button, "button");
            if (slot < 0 || slot > 53) {
                throw new IllegalArgumentException("slot must be between 0 and 53");
            }
        }

        public Click(int slot, MenuClick button, boolean shift, Object message) {
            this(slot, button, shift, message, null, null);
        }
    }

    record InventoryClick(int slot, MenuClick button, boolean shift, MenuStack item) implements ReactiveMenuInput {

        public InventoryClick {
            Objects.requireNonNull(button, "button");
            if (slot < 0) {
                throw new IllegalArgumentException("slot cannot be negative");
            }
        }
    }

    record Drag(MenuClick button, List<Integer> slots, MenuStack cursor) implements ReactiveMenuInput {

        public Drag {
            button = Objects.requireNonNull(button, "button");
            slots = List.copyOf(slots);
            if (slots.isEmpty()) {
                throw new IllegalArgumentException("slots cannot be empty");
            }
            for (Integer slot : slots) {
                if (slot == null || slot < 0 || slot > 53) {
                    throw new IllegalArgumentException("drag slot must be between 0 and 53");
                }
            }
        }

        public Drag(MenuClick button, List<Integer> slots) {
            this(button, slots, null);
        }
    }

    record DropCursor(MenuClick button, MenuStack cursor) implements ReactiveMenuInput {

        public DropCursor {
            button = Objects.requireNonNull(button, "button");
        }

        public DropCursor(MenuClick button) {
            this(button, null);
        }
    }
}
