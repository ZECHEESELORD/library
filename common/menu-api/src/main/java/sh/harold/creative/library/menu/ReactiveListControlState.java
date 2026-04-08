package sh.harold.creative.library.menu;

import java.util.Objects;

public final class ReactiveListControlState {

    private String searchQuery = "";
    private int filterIndex;
    private int sortIndex;

    public String searchQuery() {
        return searchQuery;
    }

    public void searchQuery(String searchQuery) {
        this.searchQuery = normalizeSearchQuery(searchQuery);
    }

    public int filterIndex() {
        return filterIndex;
    }

    public void filterIndex(int filterIndex) {
        if (filterIndex < 0) {
            throw new IllegalArgumentException("filterIndex cannot be negative");
        }
        this.filterIndex = filterIndex;
    }

    public int sortIndex() {
        return sortIndex;
    }

    public void sortIndex(int sortIndex) {
        if (sortIndex < 0) {
            throw new IllegalArgumentException("sortIndex cannot be negative");
        }
        this.sortIndex = sortIndex;
    }

    public static String normalizeSearchQuery(String searchQuery) {
        Objects.requireNonNull(searchQuery, "searchQuery");
        return searchQuery.trim().replaceAll("\\s+", " ");
    }
}
