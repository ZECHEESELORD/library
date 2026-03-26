package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Objects;

public record MenuTab(String id, Component name, MenuIcon icon, List<MenuItem> items) {

    public MenuTab {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        name = Objects.requireNonNull(name, "name");
        icon = Objects.requireNonNull(icon, "icon");
        items = List.copyOf(items);
    }

    public static MenuTab of(String id, String name, MenuIcon icon, Iterable<? extends MenuItem> items) {
        return new MenuTab(id, Component.text(Objects.requireNonNull(name, "name")), icon, copyItems(items));
    }

    public static MenuTab of(String id, Component name, MenuIcon icon, Iterable<? extends MenuItem> items) {
        return new MenuTab(id, name, icon, copyItems(items));
    }

    private static List<MenuItem> copyItems(Iterable<? extends MenuItem> items) {
        Objects.requireNonNull(items, "items");
        java.util.ArrayList<MenuItem> copied = new java.util.ArrayList<>();
        for (MenuItem item : items) {
            copied.add(Objects.requireNonNull(item, "item"));
        }
        return List.copyOf(copied);
    }
}
