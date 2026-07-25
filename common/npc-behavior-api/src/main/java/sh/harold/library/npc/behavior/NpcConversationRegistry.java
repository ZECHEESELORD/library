package sh.harold.library.npc.behavior;

import sh.harold.library.entity.ManagedEntity;

import java.util.Collection;

public interface NpcConversationRegistry {
    NpcConversationRegistration register(
            NpcConversationTopic topic,
            Collection<? extends ManagedEntity> cast
    );
}
