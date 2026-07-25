package sh.harold.library.npc.behavior;

import java.util.List;
import java.util.UUID;

public interface NpcConversationRegistration extends AutoCloseable {
    UUID id();

    NpcConversationTopic topic();

    List<UUID> cast();

    NpcConversationSnapshot snapshot();

    boolean registered();

    void unregister();

    @Override
    default void close() {
        unregister();
    }
}
