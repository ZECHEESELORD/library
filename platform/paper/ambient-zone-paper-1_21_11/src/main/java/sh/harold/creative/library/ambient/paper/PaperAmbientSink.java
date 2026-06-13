package sh.harold.creative.library.ambient.paper;

import sh.harold.creative.library.ambient.AmbientSnapshot;

import java.util.List;

@FunctionalInterface
public interface PaperAmbientSink {

    void accept(List<AmbientSnapshot> snapshots);
}
