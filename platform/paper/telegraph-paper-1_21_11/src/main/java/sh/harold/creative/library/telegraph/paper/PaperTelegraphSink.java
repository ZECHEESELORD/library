package sh.harold.creative.library.telegraph.paper;

import sh.harold.creative.library.telegraph.TelegraphFrame;

import java.util.List;

@FunctionalInterface
public interface PaperTelegraphSink {

    void accept(List<TelegraphFrame> frames);
}
