package sh.harold.library.menu.showcase;

import sh.harold.library.menu.MenuDefinition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ShowcaseEntry(
        String id,
        String label,
        ShowcaseOrigin origin,
        Optional<SourceReference> source,
        MenuDefinition menu,
        List<ShowcaseSnapshot> snapshots,
        Set<ShowcaseFeature> features
) {

    public ShowcaseEntry {
        id = requireText(id, "id");
        label = requireText(label, "label");
        origin = Objects.requireNonNull(origin, "origin");
        source = Objects.requireNonNull(source, "source");
        menu = Objects.requireNonNull(menu, "menu");
        snapshots = List.copyOf(Objects.requireNonNull(snapshots, "snapshots"));
        features = Set.copyOf(Objects.requireNonNull(features, "features"));
        if (snapshots.isEmpty()) {
            throw new IllegalArgumentException("snapshots cannot be empty");
        }
        if ((origin == ShowcaseOrigin.CORPUS_GOLDEN) != source.isPresent()) {
            throw new IllegalArgumentException("only corpus goldens carry source references");
        }
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return value;
    }
}
