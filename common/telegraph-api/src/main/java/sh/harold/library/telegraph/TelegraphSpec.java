package sh.harold.library.telegraph;

import net.kyori.adventure.key.Key;
import sh.harold.library.spatial.AnchorRef;
import sh.harold.library.tick.InstanceConflictPolicy;

import java.util.Objects;

public record TelegraphSpec(
        Key key,
        AnchorRef anchor,
        TelegraphShape shape,
        ViewerScope viewerScope,
        TelegraphTiming timing,
        InstanceConflictPolicy conflictPolicy,
        int priority
) {

    public TelegraphSpec {
        key = Objects.requireNonNull(key, "key");
        anchor = Objects.requireNonNull(anchor, "anchor");
        shape = Objects.requireNonNull(shape, "shape");
        viewerScope = Objects.requireNonNull(viewerScope, "viewerScope");
        timing = Objects.requireNonNull(timing, "timing");
        conflictPolicy = Objects.requireNonNull(conflictPolicy, "conflictPolicy");
    }
}
