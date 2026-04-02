package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record ReactiveTabsView(Component title, List<MenuTabGroup> groups, String activeTabId, int navStart, int pageIndex) {

    public ReactiveTabsView {
        title = Objects.requireNonNull(title, "title");
        groups = copyGroups(groups);
        activeTabId = Objects.requireNonNull(activeTabId, "activeTabId");
        if (activeTabId.isBlank()) {
            throw new IllegalArgumentException("activeTabId cannot be blank");
        }
        if (navStart < 0) {
            throw new IllegalArgumentException("navStart cannot be negative");
        }
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

    private static List<MenuTabGroup> copyGroups(Iterable<? extends MenuTabGroup> groups) {
        Objects.requireNonNull(groups, "groups");
        List<MenuTabGroup> copied = new ArrayList<>();
        for (MenuTabGroup group : groups) {
            copied.add(Objects.requireNonNull(group, "group"));
        }
        return List.copyOf(copied);
    }

    public static final class Builder {

        private Component title;
        private final List<PendingTabGroup> groups = new ArrayList<>();
        private String activeTabId;
        private int navStart;
        private int pageIndex;
        private int implicitGroupCount;

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

        public Builder activeTab(String activeTabId) {
            Objects.requireNonNull(activeTabId, "activeTabId");
            if (activeTabId.isBlank()) {
                throw new IllegalArgumentException("activeTabId cannot be blank");
            }
            this.activeTabId = activeTabId;
            return this;
        }

        public Builder navStart(int navStart) {
            if (navStart < 0) {
                throw new IllegalArgumentException("navStart cannot be negative");
            }
            this.navStart = navStart;
            return this;
        }

        public Builder page(int pageIndex) {
            if (pageIndex < 0) {
                throw new IllegalArgumentException("pageIndex cannot be negative");
            }
            this.pageIndex = pageIndex;
            return this;
        }

        public Builder addGroup(MenuTabGroup group) {
            groups.add(PendingTabGroup.explicit(Objects.requireNonNull(group, "group")));
            return this;
        }

        public Builder addTab(MenuTab tab) {
            PendingTabGroup group = groups.isEmpty() ? null : groups.get(groups.size() - 1);
            if (group == null || !group.implicit()) {
                group = PendingTabGroup.implicit("implicit:" + implicitGroupCount++);
                groups.add(group);
            }
            group.tabs().add(Objects.requireNonNull(tab, "tab"));
            return this;
        }

        public ReactiveTabsView build() {
            if (activeTabId == null) {
                throw new IllegalStateException("activeTabId is required");
            }
            List<MenuTabGroup> builtGroups = new ArrayList<>();
            for (PendingTabGroup group : groups) {
                if (!group.tabs().isEmpty()) {
                    builtGroups.add(new MenuTabGroup(group.id(), group.tabs()));
                }
            }
            return new ReactiveTabsView(title, builtGroups, activeTabId, navStart, pageIndex);
        }

        private record PendingTabGroup(String id, List<MenuTab> tabs, boolean implicit) {

            private static PendingTabGroup explicit(MenuTabGroup group) {
                return new PendingTabGroup(group.id(), new ArrayList<>(group.tabs()), false);
            }

            private static PendingTabGroup implicit(String id) {
                return new PendingTabGroup(id, new ArrayList<>(), true);
            }
        }
    }
}
