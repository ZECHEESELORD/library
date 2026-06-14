package sh.harold.library.telegraph.minestom;

import sh.harold.library.telegraph.TelegraphFrame;

import java.util.List;

@FunctionalInterface
public interface MinestomTelegraphSink {

    void accept(List<TelegraphFrame> frames);
}
