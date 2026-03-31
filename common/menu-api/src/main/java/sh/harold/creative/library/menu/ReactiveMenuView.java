package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ReactiveMenuView(Component title, Map<Integer, MenuItem> placements, MenuStack cursor, Boolean fillWithBlackPane) {

    public ReactiveMenuView {
        title = Objects.requireNonNull(title, "title");
        placements = copyPlacements(placements);
    }

    public static Builder builder(String title) {
        return builder(Component.text(Objects.requireNonNull(title, "title")));
    }

    public static Builder builder(Component title) {
        return new Builder(title);
    }

    private static Map<Integer, MenuItem> copyPlacements(Map<Integer, ? extends MenuItem> placements) {
        Objects.requireNonNull(placements, "placements");
        LinkedHashMap<Integer, MenuItem> copied = new LinkedHashMap<>();
        for (Map.Entry<Integer, ? extends MenuItem> entry : placements.entrySet()) {
            Integer slot = Objects.requireNonNull(entry.getKey(), "placement slot");
            if (slot < 0 || slot > 53) {
                throw new IllegalArgumentException("slot must be between 0 and 53");
            }
            copied.put(slot, Objects.requireNonNull(entry.getValue(), "placement item"));
        }
        return Map.copyOf(copied);
    }

    public static final class Builder {

        private Component title;
        private final Map<Integer, MenuItem> placements = new LinkedHashMap<>();
        private MenuStack cursor;
        private Boolean fillWithBlackPane;

        private Builder(Component title) {
            this.title = Objects.requireNonNull(title, "title");
        }

        public Builder title(String title) {
            this.title = Component.text(Objects.requireNonNull(title, "title"));
            return this;
        }

        public Builder title(Component title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        public Builder place(int slot, MenuItem item) {
            if (slot < 0 || slot > 53) {
                throw new IllegalArgumentException("slot must be between 0 and 53");
            }
            placements.put(slot, Objects.requireNonNull(item, "item"));
            return this;
        }

        public Builder cursor(MenuStack cursor) {
            this.cursor = cursor;
            return this;
        }

        public Builder fillWithBlackPane(boolean fillWithBlackPane) {
            this.fillWithBlackPane = fillWithBlackPane;
            return this;
        }

        public ReactiveMenuView build() {
            return new ReactiveMenuView(title, placements, cursor, fillWithBlackPane);
        }
    }
}
