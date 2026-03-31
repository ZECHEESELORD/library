package sh.harold.creative.library.spatial;

import java.util.Optional;

@FunctionalInterface
public interface AnchorResolver {

    Optional<AnchorSnapshot> resolve(AnchorRef anchorRef);
}
