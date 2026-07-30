package sh.harold.library.example.paper;

import sh.harold.library.menu.MenuDefinition;
import sh.harold.library.menu.paper.PaperMenuPlatform;
import sh.harold.library.menu.showcase.MenuShowcaseCatalog;

import java.util.Objects;

final class PaperMenuExampleMenus {

    private final MenuShowcaseCatalog catalog;

    PaperMenuExampleMenus(PaperMenuPlatform menus) {
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