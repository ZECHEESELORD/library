package sh.harold.library.npc.behavior;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface NpcPlayback extends AutoCloseable {
    UUID id();

    boolean active();

    CompletionStage<Void> completion();

    void cancel();

    @Override
    default void close() {
        cancel();
    }
}
