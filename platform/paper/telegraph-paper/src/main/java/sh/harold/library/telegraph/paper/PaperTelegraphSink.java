package sh.harold.library.telegraph.paper;

import sh.harold.library.telegraph.TelegraphFrame;

import java.util.List;

@FunctionalInterface
public interface PaperTelegraphSink {

    void accept(List<TelegraphFrame> frames);
}
