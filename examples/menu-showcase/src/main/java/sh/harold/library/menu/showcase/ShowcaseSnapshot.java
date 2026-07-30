package sh.harold.library.menu.showcase;

import sh.harold.library.menu.Menu;

import java.util.Objects;

public record ShowcaseSnapshot(String state, Menu menu) {

    public ShowcaseSnapshot {
        Objects.requireNonNull(state, "state");
        if (state.isBlank()) {
            throw new IllegalArgumentException("state cannot be blank");
        }
        menu = Objects.requireNonNull(menu, "menu");
    }
}
