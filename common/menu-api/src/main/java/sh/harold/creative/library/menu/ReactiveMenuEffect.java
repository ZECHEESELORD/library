package sh.harold.creative.library.menu;

import java.util.Objects;

public sealed interface ReactiveMenuEffect permits ReactiveMenuEffect.Close, ReactiveMenuEffect.Open {

    record Open(MenuDefinition menu) implements ReactiveMenuEffect {

        public Open {
            menu = Objects.requireNonNull(menu, "menu");
        }
    }

    record Close() implements ReactiveMenuEffect {
    }
}
