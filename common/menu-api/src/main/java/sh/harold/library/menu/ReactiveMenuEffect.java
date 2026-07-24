package sh.harold.library.menu;

import java.util.Objects;

public sealed interface ReactiveMenuEffect permits ReactiveMenuEffect.Close, ReactiveMenuEffect.Open,
        ReactiveMenuEffect.Replace,
        ReactiveMenuEffect.RequestTextPrompt {

    record Open(MenuDefinition menu) implements ReactiveMenuEffect {

        public Open {
            menu = Objects.requireNonNull(menu, "menu");
        }
    }

    record Replace(MenuDefinition menu) implements ReactiveMenuEffect {

        public Replace {
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
}
