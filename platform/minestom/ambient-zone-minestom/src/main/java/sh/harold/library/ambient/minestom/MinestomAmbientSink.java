package sh.harold.library.ambient.minestom;

import sh.harold.library.ambient.AmbientSnapshot;

import java.util.List;

@FunctionalInterface
public interface MinestomAmbientSink {

    void accept(List<AmbientSnapshot> snapshots);
}
