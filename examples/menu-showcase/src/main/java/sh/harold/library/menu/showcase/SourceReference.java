package sh.harold.library.menu.showcase;

import java.util.List;
import java.util.Objects;

public record SourceReference(String surfaceSha256, List<SourceItemReference> items) {

    public SourceReference {
        surfaceSha256 = SourceItemReference.requireSha256(surfaceSha256, "surfaceSha256");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (items.isEmpty()) {
            throw new IllegalArgumentException("items cannot be empty");
        }
    }

    public static SourceReference of(String surfaceSha256, String itemSha256, int slot) {
        return new SourceReference(surfaceSha256, List.of(new SourceItemReference(itemSha256, slot)));
    }
}
