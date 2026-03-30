package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public record MenuTab(String id, Component name, MenuIcon icon, MenuTabContent content) {

    public MenuTab {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        name = Objects.requireNonNull(name, "name");
        icon = Objects.requireNonNull(icon, "icon");
        content = Objects.requireNonNull(content, "content");
    }

    public static MenuTab of(String id, String name, MenuIcon icon, Iterable<? extends MenuItem> items) {
        return new MenuTab(id, Component.text(Objects.requireNonNull(name, "name")), icon, MenuTabContent.list(items));
    }

    public static MenuTab of(String id, Component name, MenuIcon icon, Iterable<? extends MenuItem> items) {
        return new MenuTab(id, name, icon, MenuTabContent.list(items));
    }

    public static MenuTab canvas(String id, String name, MenuIcon icon, Consumer<MenuTabContent.CanvasBuilder> consumer) {
        return new MenuTab(id, Component.text(Objects.requireNonNull(name, "name")), icon, MenuTabContent.canvas(consumer));
    }

    public static MenuTab canvas(String id, Component name, MenuIcon icon, Consumer<MenuTabContent.CanvasBuilder> consumer) {
        return new MenuTab(id, Objects.requireNonNull(name, "name"), icon, MenuTabContent.canvas(consumer));
    }

    public List<MenuItem> items() {
        return switch (content) {
            case MenuTabContent.ListContent list -> list.items();
            case MenuTabContent.CanvasContent ignored -> throw new IllegalStateException("Tab content is not list-based");
        };
    }
}
