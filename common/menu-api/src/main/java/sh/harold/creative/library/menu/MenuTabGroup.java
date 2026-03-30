package sh.harold.creative.library.menu;

import java.util.List;
import java.util.Objects;

public record MenuTabGroup(String id, List<MenuTab> tabs) {

    public MenuTabGroup {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tabs, "tabs");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        java.util.ArrayList<MenuTab> copied = new java.util.ArrayList<>();
        for (MenuTab tab : tabs) {
            copied.add(Objects.requireNonNull(tab, "tab"));
        }
        tabs = List.copyOf(copied);
        if (tabs.isEmpty()) {
            throw new IllegalArgumentException("tabs cannot be empty");
        }
    }

    public static MenuTabGroup of(String id, Iterable<? extends MenuTab> tabs) {
        Objects.requireNonNull(tabs, "tabs");
        java.util.ArrayList<MenuTab> copied = new java.util.ArrayList<>();
        for (MenuTab tab : tabs) {
            copied.add(Objects.requireNonNull(tab, "tab"));
        }
        return new MenuTabGroup(id, copied);
    }
}
