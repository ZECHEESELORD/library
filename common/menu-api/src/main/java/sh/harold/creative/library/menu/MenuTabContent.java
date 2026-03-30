package sh.harold.creative.library.menu;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public sealed interface MenuTabContent permits MenuTabContent.CanvasContent, MenuTabContent.ListContent {

    static ListContent list(Iterable<? extends MenuItem> items) {
        Objects.requireNonNull(items, "items");
        java.util.ArrayList<MenuItem> copied = new java.util.ArrayList<>();
        for (MenuItem item : items) {
            copied.add(Objects.requireNonNull(item, "item"));
        }
        return new ListContent(copied);
    }

    static CanvasContent canvas(Map<Integer, ? extends MenuItem> placements) {
        Objects.requireNonNull(placements, "placements");
        LinkedHashMap<Integer, MenuItem> copied = new LinkedHashMap<>();
        for (Map.Entry<Integer, ? extends MenuItem> entry : placements.entrySet()) {
            copied.put(validateSlot(entry.getKey()), Objects.requireNonNull(entry.getValue(), "placement item"));
        }
        return new CanvasContent(copied, true);
    }

    static CanvasContent canvas(Consumer<CanvasBuilder> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        CanvasBuilder builder = new CanvasBuilder();
        consumer.accept(builder);
        return builder.build();
    }

    private static int validateSlot(Integer slot) {
        Objects.requireNonNull(slot, "slot");
        if (slot < 0 || slot > 53) {
            throw new IllegalArgumentException("slot must be between 0 and 53");
        }
        return slot;
    }

    record ListContent(List<MenuItem> items) implements MenuTabContent {

        public ListContent {
            java.util.ArrayList<MenuItem> copied = new java.util.ArrayList<>();
            for (MenuItem item : items) {
                copied.add(Objects.requireNonNull(item, "item"));
            }
            items = List.copyOf(copied);
        }
    }

    record CanvasContent(Map<Integer, MenuItem> placements, boolean fillWithBlackPane) implements MenuTabContent {

        public CanvasContent {
            LinkedHashMap<Integer, MenuItem> copied = new LinkedHashMap<>();
            for (Map.Entry<Integer, MenuItem> entry : placements.entrySet()) {
                copied.put(validateSlot(entry.getKey()), Objects.requireNonNull(entry.getValue(), "placement item"));
            }
            placements = Map.copyOf(copied);
        }
    }

    final class CanvasBuilder {

        private final Map<Integer, MenuItem> placements = new LinkedHashMap<>();
        private boolean fillWithBlackPane = true;

        public CanvasBuilder place(int slot, MenuItem item) {
            placements.put(validateSlot(slot), Objects.requireNonNull(item, "item"));
            return this;
        }

        public CanvasBuilder fillWithBlackPane(boolean fillWithBlackPane) {
            this.fillWithBlackPane = fillWithBlackPane;
            return this;
        }

        public CanvasContent build() {
            return new CanvasContent(placements, fillWithBlackPane);
        }
    }
}
