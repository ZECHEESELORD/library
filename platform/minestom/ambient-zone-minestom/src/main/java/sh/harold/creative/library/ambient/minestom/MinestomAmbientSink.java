package sh.harold.creative.library.ambient.minestom;

import sh.harold.creative.library.ambient.AmbientSnapshot;

import java.util.List;

@FunctionalInterface
public interface MinestomAmbientSink {

    void accept(List<AmbientSnapshot> snapshots);
}
