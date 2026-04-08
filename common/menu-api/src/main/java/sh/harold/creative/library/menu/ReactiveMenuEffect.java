package sh.harold.creative.library.menu;

import java.util.Objects;

public sealed interface ReactiveMenuEffect permits ReactiveMenuEffect.Close, ReactiveMenuEffect.Open,
        ReactiveMenuEffect.RequestTextPrompt, ReactiveMenuEffect.SetViewerInventorySlot {

    record Open(MenuDefinition menu) implements ReactiveMenuEffect {

        public Open {
            menu = Objects.requireNonNull(menu, "menu");
        }
    }

    record Close() implements ReactiveMenuEffect {
    }

    record RequestTextPrompt(ReactiveTextPromptRequest request) implements ReactiveMenuEffect {

        public RequestTextPrompt {
            request = Objects.requireNonNull(request, "request");
        }
    }

    record SetViewerInventorySlot(int slot, MenuStack stack) implements ReactiveMenuEffect {

        public SetViewerInventorySlot {
            if (slot < 0) {
                throw new IllegalArgumentException("slot cannot be negative");
            }
        }
    }
}
