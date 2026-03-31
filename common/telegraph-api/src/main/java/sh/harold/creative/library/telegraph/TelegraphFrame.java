package sh.harold.creative.library.telegraph;

import net.kyori.adventure.key.Key;
import sh.harold.creative.library.spatial.AnchorSnapshot;

import java.util.Objects;

public record TelegraphFrame(
        Key key,
        AnchorSnapshot anchor,
        TelegraphShape shape,
        ViewerScope viewerScope,
        double alpha,
        double thickness,
        int priority,
        long ageTicks
) {

    public TelegraphFrame {
        key = Objects.requireNonNull(key, "key");
        anchor = Objects.requireNonNull(anchor, "anchor");
        shape = Objects.requireNonNull(shape, "shape");
        viewerScope = Objects.requireNonNull(viewerScope, "viewerScope");
    }
}
