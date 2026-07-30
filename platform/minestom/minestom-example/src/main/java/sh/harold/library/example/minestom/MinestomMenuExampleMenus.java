package sh.harold.library.example.minestom;

import sh.harold.library.menu.MenuDefinition;
import sh.harold.library.menu.minestom.MinestomMenuPlatform;
import sh.harold.library.menu.showcase.MenuShowcaseCatalog;

import java.util.Objects;

final class MinestomMenuExampleMenus {

    static final String LOCK_DRAG_TITLE = "Salvage Station";
    static final String LOCK_CLICK_TITLE = "Mail Locker";

    private final MenuShowcaseCatalog catalog;

    MinestomMenuExampleMenus(MinestomMenuPlatform menus) {
        Objects.requireNonNull(menus, "menus");
        this.catalog = new MenuShowcaseCatalog();
    }

    MenuDefinition gallery() {
        return catalog.gallery();
    }

    MenuDefinition tabsGallery() {
        return catalog.gallery();
    }

    MenuDefinition listGallery() {
        return entry("network-browser");
    }

    MenuDefinition canvasGallery() {
        return entry("skyblock-menu");
    }

    MenuDefinition reactiveGallery() {
        return entry("loadout-workshop");
    }

    MenuDefinition profilePreview() {
        return entry("profile-management");
    }

    MenuDefinition farmingPreview() {
        return entry("skills");
    }

    MenuDefinition museumPreview() {
        return entry("museum-milestones");
    }

    MenuDefinition slotFivePreview() {
        return entry("profile-management");
    }

    MenuDefinition snakeDemo() {
        return entry("match-finder");
    }

    MenuDefinition lockDragDemo() {
        return entry("salvage-station");
    }

    MenuDefinition lockClickDemo() {
        return entry("mail-locker");
    }

    private MenuDefinition entry(String id) {
        return catalog.entry(id).menu();
    }
}