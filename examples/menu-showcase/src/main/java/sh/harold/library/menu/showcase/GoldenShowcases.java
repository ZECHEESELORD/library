package sh.harold.library.menu.showcase;

import sh.harold.library.menu.Menu;
import sh.harold.library.menu.MenuService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class GoldenShowcases {

    private GoldenShowcases() {
    }

    static List<ShowcaseEntry> create(MenuService menus) {
        List<ShowcaseEntry> entries = new ArrayList<>();
        for (CorpusGoldenSurface surface : CorpusGoldenSurface.all()) {
            Menu menu = surface.build(menus);
            Set<ShowcaseFeature> features = surface.id().equals("confirmation")
                    ? Set.of(ShowcaseFeature.CONFIRMATION)
                    : Set.of();
            entries.add(new ShowcaseEntry(
                    surface.id(),
                    surface.label(),
                    ShowcaseOrigin.CORPUS_GOLDEN,
                    Optional.of(surface.sourceReference()),
                    menu,
                    List.of(new ShowcaseSnapshot("normalized", menu)),
                    features));
        }
        return List.copyOf(entries);
    }
}
