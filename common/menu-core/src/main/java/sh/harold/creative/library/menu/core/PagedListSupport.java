package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import sh.harold.creative.library.menu.MenuItem;

import java.util.List;

final class PagedListSupport {

    static final List<Integer> PURE_LIST_CONTENT_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );
    static final int PURE_LIST_PAGE_SIZE = PURE_LIST_CONTENT_SLOTS.size();

    static final List<Integer> TAB_LIST_CONTENT_SLOTS = List.of(
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );
    static final int TAB_LIST_PAGE_SIZE = TAB_LIST_CONTENT_SLOTS.size();

    private PagedListSupport() {
    }

    static int pageCount(int itemCount, int pageSize) {
        return Math.max(1, (itemCount + pageSize - 1) / pageSize);
    }

    static int clampPageIndex(int pageIndex, int itemCount, int pageSize) {
        int totalPages = pageCount(itemCount, pageSize);
        return Math.max(0, Math.min(pageIndex, totalPages - 1));
    }

    static int firstItemIndex(int pageIndex, int pageSize) {
        return pageIndex * pageSize;
    }

    static int lastItemExclusive(int pageIndex, int itemCount, int pageSize) {
        return Math.min(itemCount, firstItemIndex(pageIndex, pageSize) + pageSize);
    }

    static Component decorateTitle(Component title, int pageIndex, int totalPages) {
        if (totalPages <= 1) {
            return title;
        }
        return Component.text().append(title).append(Component.text(" (" + (pageIndex + 1) + "/" + totalPages + ")")).build();
    }

    static List<MenuItem> copyItems(Iterable<? extends MenuItem> items) {
        java.util.ArrayList<MenuItem> copied = new java.util.ArrayList<>();
        for (MenuItem item : items) {
            copied.add(java.util.Objects.requireNonNull(item, "item"));
        }
        return List.copyOf(copied);
    }
}
