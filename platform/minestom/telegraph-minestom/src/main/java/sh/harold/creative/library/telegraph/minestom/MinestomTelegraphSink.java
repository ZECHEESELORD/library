package sh.harold.creative.library.telegraph.minestom;

import sh.harold.creative.library.telegraph.TelegraphFrame;

import java.util.List;

@FunctionalInterface
public interface MinestomTelegraphSink {

    void accept(List<TelegraphFrame> frames);
}
