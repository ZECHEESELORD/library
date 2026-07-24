package sh.harold.library.menu;

import java.util.Objects;

public sealed interface ReactiveMenuInput permits ReactiveMenuInput.Click,
        ReactiveMenuInput.CustodyCommitted, ReactiveMenuInput.CustodyRejected, ReactiveMenuInput.Opened,
        ReactiveMenuInput.TextPromptCancelled, ReactiveMenuInput.TextPromptSubmitted, ReactiveMenuInput.Tick {

    record Opened() implements ReactiveMenuInput {
    }

    record CustodyCommitted(long operationId, MenuCustodyGesture gesture, MenuCustodySnapshot snapshot)
            implements ReactiveMenuInput {

        public CustodyCommitted {
            if (operationId <= 0L) {
                throw new IllegalArgumentException("operationId must be greater than zero");
            }
            gesture = Objects.requireNonNull(gesture, "gesture");
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    record CustodyRejected(long operationId, MenuCustodyGesture gesture, MenuCustodyFailure failure,
                           MenuCustodySnapshot snapshot) implements ReactiveMenuInput {

        public CustodyRejected {
            if (operationId <= 0L) {
                throw new IllegalArgumentException("operationId must be greater than zero");
            }
            gesture = Objects.requireNonNull(gesture, "gesture");
            failure = Objects.requireNonNull(failure, "failure");
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
        }
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

    record Click(int slot, MenuClick button, boolean shift, Object message) implements ReactiveMenuInput {

        public Click {
            Objects.requireNonNull(button, "button");
            if (slot < 0 || slot > 53) {
                throw new IllegalArgumentException("slot must be between 0 and 53");
            }
        }
    }
}
