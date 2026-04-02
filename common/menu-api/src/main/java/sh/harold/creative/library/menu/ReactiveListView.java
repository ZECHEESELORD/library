package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record ReactiveListView(Component title, List<MenuItem> items, int pageIndex) {

    public ReactiveListView {
        title = Objects.requireNonNull(title, "title");
        items = copyItems(items);
        if (pageIndex < 0) {
            throw new IllegalArgumentException("pageIndex cannot be negative");
        }
    }

    public static Builder builder(String title) {
        return builder(Component.text(Objects.requireNonNull(title, "title")));
    }

    public static Builder builder(Component title) {
        return new Builder(title);
    }

    private static List<MenuItem> copyItems(Iterable<? extends MenuItem> items) {
        Objects.requireNonNull(items, "items");
        List<MenuItem> copied = new ArrayList<>();
        for (MenuItem item : items) {
            copied.add(Objects.requireNonNull(item, "item"));
        }
        return List.copyOf(copied);
    }

    public static final class Builder {

        private Component title;
        private final List<MenuItem> items = new ArrayList<>();
        private int pageIndex;

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

        public Builder page(int pageIndex) {
            if (pageIndex < 0) {
                throw new IllegalArgumentException("pageIndex cannot be negative");
            }
            this.pageIndex = pageIndex;
            return this;
        }

        public Builder addItem(MenuItem item) {
            items.add(Objects.requireNonNull(item, "item"));
            return this;
        }

        public Builder addItems(Iterable<? extends MenuItem> items) {
            Objects.requireNonNull(items, "items");
            for (MenuItem item : items) {
                addItem(item);
            }
            return this;
        }

        public <T> Builder addItems(Iterable<T> items, Function<T, ? extends MenuItem> mapper) {
            Objects.requireNonNull(items, "items");
            Objects.requireNonNull(mapper, "mapper");
            for (T item : items) {
                addItem(mapper.apply(item));
            }
            return this;
        }

        public ReactiveListView build() {
            return new ReactiveListView(title, items, pageIndex);
        }
    }
}
