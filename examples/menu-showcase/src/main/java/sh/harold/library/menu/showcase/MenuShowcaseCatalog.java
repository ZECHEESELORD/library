package sh.harold.library.menu.showcase;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import sh.harold.library.menu.ActionVerb;
import sh.harold.library.menu.Menu;
import sh.harold.library.menu.MenuButton;
import sh.harold.library.menu.MenuIcon;
import sh.harold.library.menu.MenuService;
import sh.harold.library.menu.MenuTab;
import sh.harold.library.menu.core.StandardMenuService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MenuShowcaseCatalog {

    private final MenuService menus;
    private final List<ShowcaseEntry> goldens;
    private final List<ShowcaseEntry> synthesized;
    private final List<ShowcaseEntry> entries;
    private final Map<String, ShowcaseEntry> byId;
    private final Menu gallery;

    public MenuShowcaseCatalog() {
        this(new StandardMenuService());
    }

    public MenuShowcaseCatalog(MenuService menus) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.goldens = GoldenShowcases.create(menus);
        this.synthesized = SynthesizedShowcases.create(menus);
        this.entries = java.util.stream.Stream.concat(goldens.stream(), synthesized.stream()).toList();

        Map<String, ShowcaseEntry> index = new LinkedHashMap<>();
        for (ShowcaseEntry entry : entries) {
            if (index.putIfAbsent(entry.id(), entry) != null) {
                throw new IllegalStateException("Duplicate showcase id: " + entry.id());
            }
        }
        this.byId = Map.copyOf(index);
        this.gallery = buildGallery();
    }

    public List<ShowcaseEntry> entries() {
        return entries;
    }

    public List<ShowcaseEntry> goldens() {
        return goldens;
    }

    public List<ShowcaseEntry> synthesized() {
        return synthesized;
    }

    public ShowcaseEntry entry(String id) {
        ShowcaseEntry entry = byId.get(Objects.requireNonNull(id, "id"));
        if (entry == null) {
            throw new IllegalArgumentException("Unknown showcase id: " + id);
        }
        return entry;
    }

    public Menu gallery() {
        return gallery;
    }

    private Menu buildGallery() {
        return menus.tabs()
                .title("Menu Showcase v9")
                .defaultTab("goldens")
                .addTab(MenuTab.builder("goldens", MenuIcon.vanilla("nether_star"))
                        .name(Component.text("Corpus Goldens", NamedTextColor.GOLD))
                        .secondary("20 normalized surfaces")
                        .items(goldens.stream().map(this::entryButton).toList())
                        .build())
                .addTab(MenuTab.builder("synthesized", MenuIcon.vanilla("crafting_table"))
                        .name(Component.text("Synthesized", NamedTextColor.AQUA))
                        .secondary("10 v9 examples")
                        .items(synthesized.stream().map(this::entryButton).toList())
                        .build())
                .build();
    }

    private MenuButton entryButton(ShowcaseEntry entry) {
        NamedTextColor color = entry.origin() == ShowcaseOrigin.CORPUS_GOLDEN
                ? NamedTextColor.GOLD
                : NamedTextColor.AQUA;
        return MenuButton.builder(MenuIcon.vanilla(
                        entry.origin() == ShowcaseOrigin.CORPUS_GOLDEN ? "paper" : "book"))
                .name(Component.text(entry.label(), color))
                .secondary(entry.origin() == ShowcaseOrigin.CORPUS_GOLDEN
                        ? "Corpus golden"
                        : "Synthesized")
                .onLeftClick(ActionVerb.OPEN, context -> context.open(entry.menu()))
                .build();
    }
}
