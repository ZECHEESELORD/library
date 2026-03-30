package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class MenuTab {

    private final String id;
    private final Component name;
    private final MenuIcon icon;
    private final String secondary;
    private final List<MenuBlock> blocks;
    private final boolean glow;
    private final MenuTabContent content;

    public MenuTab(String id, Component name, MenuIcon icon, MenuTabContent content) {
        this(id, name, icon, null, List.of(), false, content);
    }

    public MenuTab(String id, Component name, MenuIcon icon, String secondary, List<MenuBlock> blocks, boolean glow, MenuTabContent content) {
        this.id = requireId(id);
        this.name = Objects.requireNonNull(name, "name");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.secondary = secondary;
        this.blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
        this.glow = glow;
        this.content = Objects.requireNonNull(content, "content");
    }

    public static MenuTab of(String id, String name, MenuIcon icon, Iterable<? extends MenuItem> items) {
        return new MenuTab(requireId(id), Component.text(Objects.requireNonNull(name, "name")), icon, MenuTabContent.list(items));
    }

    public static MenuTab of(String id, Component name, MenuIcon icon, Iterable<? extends MenuItem> items) {
        return new MenuTab(requireId(id), name, icon, MenuTabContent.list(items));
    }

    public static MenuTab canvas(String id, String name, MenuIcon icon, Consumer<MenuTabContent.CanvasBuilder> consumer) {
        return new MenuTab(requireId(id), Component.text(Objects.requireNonNull(name, "name")), icon, MenuTabContent.canvas(consumer));
    }

    public static MenuTab canvas(String id, Component name, MenuIcon icon, Consumer<MenuTabContent.CanvasBuilder> consumer) {
        return new MenuTab(requireId(id), name, icon, MenuTabContent.canvas(consumer));
    }

    public static Builder builder(String id, MenuIcon icon) {
        return new Builder(requireId(id), Objects.requireNonNull(icon, "icon"));
    }

    public String id() {
        return id;
    }

    public Component name() {
        return name;
    }

    public MenuIcon icon() {
        return icon;
    }

    public String secondary() {
        return secondary;
    }

    public List<MenuBlock> blocks() {
        return blocks;
    }

    public boolean glow() {
        return glow;
    }

    public MenuTabContent content() {
        return content;
    }

    public List<MenuItem> items() {
        return switch (content) {
            case MenuTabContent.ListContent list -> list.items();
            case MenuTabContent.CanvasContent ignored -> throw new IllegalStateException("Tab content is not list-based");
        };
    }

    private static String requireId(String id) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        return id;
    }

    public static final class Builder extends AbstractMenuItemBuilder<Builder> {

        private final String id;
        private MenuTabContent content;

        private Builder(String id, MenuIcon icon) {
            super(icon);
            this.id = id;
        }

        public Builder items(Iterable<? extends MenuItem> items) {
            this.content = MenuTabContent.list(items);
            return this;
        }

        public Builder canvas(Consumer<MenuTabContent.CanvasBuilder> consumer) {
            this.content = MenuTabContent.canvas(consumer);
            return this;
        }

        public MenuTab build() {
            if (content == null) {
                throw new IllegalStateException("tab content is required");
            }
            return new MenuTab(id, name(), icon(), secondary(), blocks(), isGlowing(), content);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
