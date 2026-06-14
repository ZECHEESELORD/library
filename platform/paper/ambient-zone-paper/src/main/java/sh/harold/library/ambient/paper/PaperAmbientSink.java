package sh.harold.library.ambient.paper;

import sh.harold.library.ambient.AmbientSnapshot;

import java.util.List;

@FunctionalInterface
public interface PaperAmbientSink {

    void accept(List<AmbientSnapshot> snapshots);
}
