package sh.harold.library.menu;

import java.util.List;
import java.util.Objects;

public sealed interface MenuCustodyGesture permits MenuCustodyGesture.OutsideClick, MenuCustodyGesture.Settle,
        MenuCustodyGesture.TargetClick, MenuCustodyGesture.TargetDrag, MenuCustodyGesture.ViewerClick {

    enum SettleReason {
        NAVIGATE,
        PROMPT,
        CLOSE,
        DISCONNECT,
        DEATH,
        SHUTDOWN
    }

    record ViewerClick(MenuViewerSlot slot, MenuClick button, boolean shift) implements MenuCustodyGesture {

        public ViewerClick {
            slot = Objects.requireNonNull(slot, "slot");
            button = Objects.requireNonNull(button, "button");
        }
    }

    record TargetClick(String targetKey, MenuClick button, boolean shift) implements MenuCustodyGesture {

        public TargetClick {
            targetKey = requireKey(targetKey);
            button = Objects.requireNonNull(button, "button");
        }
    }

    record TargetDrag(List<String> targetKeys, MenuClick button) implements MenuCustodyGesture {

        public TargetDrag {
            targetKeys = List.copyOf(targetKeys);
            if (targetKeys.isEmpty()) {
                throw new IllegalArgumentException("targetKeys cannot be empty");
            }
            targetKeys.forEach(MenuCustodyGesture::requireKey);
            button = Objects.requireNonNull(button, "button");
        }
    }

    record OutsideClick(MenuClick button) implements MenuCustodyGesture {

        public OutsideClick {
            button = Objects.requireNonNull(button, "button");
        }
    }

    record Settle(SettleReason reason) implements MenuCustodyGesture {

        public Settle {
            reason = Objects.requireNonNull(reason, "reason");
        }
    }

    private static String requireKey(String key) {
        Objects.requireNonNull(key, "target key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("target key cannot be blank");
        }
        return key;
    }
}
